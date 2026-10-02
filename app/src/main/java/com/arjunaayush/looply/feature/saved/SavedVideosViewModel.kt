package com.arjunaayush.looply.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

data class SavedUiState(
    val creatorGroups: List<CreatorGroup> = emptyList(),
    val selectedCreator: String? = null,
    val selectedFilter: ReelSmartFilter = ReelSmartFilter.ALL,
    val selectedSort: ReelSortOrder = ReelSortOrder.RECENTLY_ADDED,
    val displayedVideos: List<Video> = emptyList(),
    val totalSavedCount: Int = 0,
    val totalStorageBytes: Long = 0L
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

    private val filterAndSortState = combine(
        selectedCreator,
        selectedFilter,
        selectedSort
    ) { creator, filter, sort ->
        Triple(creator, filter, sort)
    }

    val uiState: StateFlow<SavedUiState> = combine(
        getSavedVideosUseCase(),
        getVideosByCreatorUseCase(),
        filterAndSortState,
        repository.getStorageUsageBytes()
    ) { allVideos, creatorGroups, (creator, filter, sort), storageBytes ->
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
            totalStorageBytes = storageBytes
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
