package com.arjunaayush.looply.feature.saved

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjunaayush.looply.core.util.MediaExportUtils
import com.arjunaayush.looply.domain.model.CreatorGroup
import com.arjunaayush.looply.domain.model.ReelSmartFilter
import com.arjunaayush.looply.domain.model.ReelSortOrder
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.domain.model.applyFilterAndSort
import com.arjunaayush.looply.domain.repository.VideoRepository
import com.arjunaayush.looply.domain.usecase.ClearWatchedVideosUseCase
import com.arjunaayush.looply.domain.usecase.DeleteAllVideosUseCase
import com.arjunaayush.looply.domain.usecase.DeleteVideoUseCase
import com.arjunaayush.looply.domain.usecase.GetSavedVideosUseCase
import com.arjunaayush.looply.domain.usecase.GetVideosByCreatorUseCase
import com.arjunaayush.looply.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SavedUiState(
    val creatorGroups: List<CreatorGroup> = emptyList(),
    val selectedCreator: String? = null,
    val selectedFilter: ReelSmartFilter = ReelSmartFilter.ALL,
    val selectedSort: ReelSortOrder = ReelSortOrder.RECENTLY_ADDED,
    val displayedVideos: List<Video> = emptyList(),
    val totalSavedCount: Int = 0,
    val totalStorageBytes: Long = 0L,
    val selectedVideoIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
) {
    val storageUsageFormatted: String
        get() {
            val mb = totalStorageBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                String.format("%.1f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}

@HiltViewModel
class SavedVideosViewModel @Inject constructor(
    private val getSavedVideosUseCase: GetSavedVideosUseCase,
    private val getVideosByCreatorUseCase: GetVideosByCreatorUseCase,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val clearWatchedVideosUseCase: ClearWatchedVideosUseCase,
    private val deleteAllVideosUseCase: DeleteAllVideosUseCase,
    private val repository: VideoRepository
) : ViewModel() {

    private val selectedCreator = MutableStateFlow<String?>(null)
    private val selectedFilter = MutableStateFlow(ReelSmartFilter.ALL)
    private val selectedSort = MutableStateFlow(ReelSortOrder.RECENTLY_ADDED)
    private val selectedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    private val isSelectionMode = MutableStateFlow(false)

    private val filterAndSortState = combine(
        selectedCreator,
        selectedFilter,
        selectedSort
    ) { creator, filter, sort ->
        Triple(creator, filter, sort)
    }

    private val selectionState = combine(
        selectedVideoIds,
        isSelectionMode
    ) { ids, mode ->
        Pair(ids, mode)
    }

    val uiState: StateFlow<SavedUiState> = combine(
        getSavedVideosUseCase(),
        getVideosByCreatorUseCase(),
        filterAndSortState,
        repository.getStorageUsageBytes(),
        selectionState
    ) { allVideos, creatorGroups, (creator, filter, sort), storageBytes, (selectedIds, selectionMode) ->
        val filtered = allVideos.applyFilterAndSort(
            filter = filter,
            sortOrder = sort,
            creator = creator
        )

        SavedUiState(
            creatorGroups = creatorGroups,
            selectedCreator = creator,
            selectedFilter = filter,
            selectedSort = sort,
            displayedVideos = filtered,
            totalSavedCount = allVideos.size,
            totalStorageBytes = storageBytes,
            selectedVideoIds = selectedIds,
            isSelectionMode = selectionMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavedUiState()
    )

    fun selectCreator(creator: String?) {
        selectedCreator.value = if (selectedCreator.value == creator) null else creator
    }

    fun selectFilter(filter: ReelSmartFilter) {
        selectedFilter.value = filter
    }

    fun selectSort(sort: ReelSortOrder) {
        selectedSort.value = sort
    }

    fun toggleVideoSelection(videoId: String) {
        val current = selectedVideoIds.value.toMutableSet()
        if (current.contains(videoId)) {
            current.remove(videoId)
        } else {
            current.add(videoId)
        }
        selectedVideoIds.value = current
        isSelectionMode.value = current.isNotEmpty()
    }

    fun startSelection(videoId: String) {
        isSelectionMode.value = true
        selectedVideoIds.value = setOf(videoId)
    }

    fun selectAll() {
        isSelectionMode.value = true
        selectedVideoIds.value = uiState.value.displayedVideos.map { it.id }.toSet()
    }

    fun clearSelection() {
        isSelectionMode.value = false
        selectedVideoIds.value = emptySet()
    }

    fun deleteSelectedVideos() {
        val idsToDelete = selectedVideoIds.value.toList()
        viewModelScope.launch {
            idsToDelete.forEach { id ->
                deleteVideoUseCase(id)
            }
            clearSelection()
        }
    }

    fun exportSelectedVideos(context: Context): Int {
        val idsToExport = selectedVideoIds.value
        val videosToExport = uiState.value.displayedVideos.filter { it.id in idsToExport }
        var count = 0
        videosToExport.forEach { video ->
            val file = File(video.filePath)
            if (file.exists()) {
                val success = MediaExportUtils.exportVideoToDevice(
                    context = context,
                    sourceFile = file,
                    title = video.title.ifBlank { "Reel_${video.id}" }
                )
                if (success) count++
            }
        }
        clearSelection()
        return count
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            deleteVideoUseCase(videoId)
        }
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(videoId)
        }
    }

    fun clearWatchedVideos() {
        viewModelScope.launch {
            clearWatchedVideosUseCase()
        }
    }

    fun deleteAllVideos() {
        viewModelScope.launch {
            deleteAllVideosUseCase()
        }
    }
}
