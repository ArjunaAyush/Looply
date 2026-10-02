package com.arjunaayush.looply.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.domain.usecase.DeleteVideoUseCase
import com.arjunaayush.looply.domain.usecase.GetSavedVideosUseCase
import com.arjunaayush.looply.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val videos: List<Video> = emptyList(),
    val isLooping: Boolean = true,
    val isMuted: Boolean = false,
    val selectedIndex: Int = 0
)

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val getSavedVideosUseCase: GetSavedVideosUseCase,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val isLooping = MutableStateFlow(true)
    private val isMuted = MutableStateFlow(false)
    private val selectedIndex = MutableStateFlow(0)

    val uiState: StateFlow<FeedUiState> = combine(
        getSavedVideosUseCase(),
        isLooping,
        isMuted,
        selectedIndex
    ) { videos, looping, muted, index ->
        FeedUiState(
            videos = videos,
            isLooping = looping,
            isMuted = muted,
            selectedIndex = index.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeedUiState()
    )

    fun toggleLoop() {
        isLooping.value = !isLooping.value
    }

    fun toggleMute() {
        isMuted.value = !isMuted.value
    }

    fun setPage(page: Int) {
        selectedIndex.value = page
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(videoId)
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            deleteVideoUseCase(videoId)
        }
    }
}
