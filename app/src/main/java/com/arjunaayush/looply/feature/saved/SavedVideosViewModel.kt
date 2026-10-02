package com.arjunaayush.looply.feature.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjunaayush.looply.domain.model.CreatorGroup
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.domain.repository.VideoRepository
import com.arjunaayush.looply.domain.usecase.ClearWatchedVideosUseCase
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
    val displayedVideos: List<Video> = emptyList(),
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
    private val repository: VideoRepository
) : ViewModel() {

    private val selectedCreator = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SavedUiState> = combine(
        getSavedVideosUseCase(),
        getVideosByCreatorUseCase(),
        selectedCreator,
        repository.getStorageUsageBytes()
    ) { allVideos, creatorGroups, creator, storageBytes ->
        val filtered = if (creator == null) {
            allVideos
        } else {
            allVideos.filter { it.displayAuthor.equals(creator, ignoreCase = true) }
        }

        SavedUiState(
            creatorGroups = creatorGroups,
            selectedCreator = creator,
            displayedVideos = filtered,
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
}
