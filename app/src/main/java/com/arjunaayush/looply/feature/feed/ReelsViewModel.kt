package com.arjunaayush.looply.feature.feed

import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjunaayush.looply.domain.model.ReelSmartFilter
import com.arjunaayush.looply.domain.model.ReelSortOrder
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.domain.model.applyFilterAndSort
import com.arjunaayush.looply.domain.usecase.DeleteVideoUseCase
import com.arjunaayush.looply.domain.usecase.GetSavedVideosUseCase
import com.arjunaayush.looply.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.domain.usecase.RecordVideoViewUseCase
import kotlinx.coroutines.flow.asStateFlow

data class FeedUiState(
    val videos: List<Video> = emptyList(),
    val isLooping: Boolean = true,
    val isMuted: Boolean = false,
    val selectedIndex: Int = 0,
    val sortOrder: ReelSortOrder = ReelSortOrder.RECENTLY_ADDED,
    val isAmbientModeEnabled: Boolean = true
)

@HiltViewModel
class ReelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getSavedVideosUseCase: GetSavedVideosUseCase,
    private val deleteVideoUseCase: DeleteVideoUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val recordVideoViewUseCase: RecordVideoViewUseCase,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val isLooping = MutableStateFlow(true)
    private val isMuted = MutableStateFlow(isDeviceMutedByDefault(context))
    private val selectedIndex = MutableStateFlow(0)
    private val sortOrder = MutableStateFlow(ReelSortOrder.RECENTLY_ADDED)
    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    val ambientModeEnabled: StateFlow<Boolean> = preferencesManager.ambientModeEnabled
    val shakeToShuffleEnabled: StateFlow<Boolean> = preferencesManager.shakeToShuffleEnabled

    companion object {
        fun isDeviceMutedByDefault(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val isRingerSilentOrVibrate = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                    audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
            val isMusicStreamSilent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
            return isRingerSilentOrVibrate || isMusicStreamSilent
        }
    }

    val uiState: StateFlow<FeedUiState> = combine(
        combine(getSavedVideosUseCase(), isLooping, isMuted) { v, l, m -> Triple(v, l, m) },
        combine(selectedIndex, sortOrder, ambientModeEnabled) { idx, ord, amb -> Triple(idx, ord, amb) }
    ) { (videos, looping, muted), (index, order, ambient) ->
        val feedVideos = videos.applyFilterAndSort(ReelSmartFilter.ALL, order)
        FeedUiState(
            videos = feedVideos,
            isLooping = looping,
            isMuted = muted,
            selectedIndex = index.coerceIn(0, (feedVideos.size - 1).coerceAtLeast(0)),
            sortOrder = order,
            isAmbientModeEnabled = ambient
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

    fun updatePlaybackProgress(progress: Float) {
        _playbackProgress.value = progress.coerceIn(0f, 1f)
    }

    fun recordVideoView(videoId: String) {
        viewModelScope.launch {
            recordVideoViewUseCase(videoId)
        }
    }

    fun shuffleReel(): Int? {
        val count = uiState.value.videos.size
        if (count <= 1) return null
        val current = uiState.value.selectedIndex
        var next = (0 until count).random()
        if (next == current) {
            next = (next + 1) % count
        }
        setPage(next)
        return next
    }

    fun setSortOrder(order: ReelSortOrder) {
        sortOrder.value = order
    }

    fun toggleFavorite(videoId: String) {
        viewModelScope.launch {
            toggleFavoriteUseCase(videoId)
        }
    }

    fun likeVideo(videoId: String) {
        viewModelScope.launch {
            val video = uiState.value.videos.find { it.id == videoId }
            if (video != null && !video.isFavorite) {
                toggleFavoriteUseCase(videoId)
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            deleteVideoUseCase(videoId)
        }
    }
}
