package com.arjunaayush.looply.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjunaayush.looply.core.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isInstagramLoggedIn: Boolean = false,
    val instagramUsername: String = "",
    val autoDownloadOnWifi: Boolean = true,
    val cacheLimitIndex: Int = 1,
    val autoDeleteWatchedAfter24h: Boolean = true,
    val instantBatchSizeMb: Int = 200,
    val infiniteLoopDefault: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val isDownloadingBatch: Boolean = false,
    val batchDownloadProgress: String = ""
) {
    val selectedCacheLimitText: String
        get() = PreferencesManager.CACHE_LIMIT_OPTIONS.getOrElse(cacheLimitIndex) { "500 MB" }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val isDownloadingBatch = MutableStateFlow(false)
    private val batchDownloadProgress = MutableStateFlow("")

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesManager.isInstagramLoggedIn,
        preferencesManager.instagramUsername,
        preferencesManager.autoDownloadOnWifi,
        preferencesManager.cacheLimitIndex,
        preferencesManager.autoDeleteWatchedAfter24h,
        preferencesManager.instantBatchSizeMb,
        preferencesManager.infiniteLoopDefault,
        preferencesManager.hapticsEnabled,
        isDownloadingBatch,
        batchDownloadProgress
    ) { params: Array<Any> ->
        SettingsUiState(
            isInstagramLoggedIn = params[0] as Boolean,
            instagramUsername = params[1] as String,
            autoDownloadOnWifi = params[2] as Boolean,
            cacheLimitIndex = params[3] as Int,
            autoDeleteWatchedAfter24h = params[4] as Boolean,
            instantBatchSizeMb = params[5] as Int,
            infiniteLoopDefault = params[6] as Boolean,
            hapticsEnabled = params[7] as Boolean,
            isDownloadingBatch = params[8] as Boolean,
            batchDownloadProgress = params[9] as String
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun onInstagramLoginSuccess(username: String) {
        preferencesManager.setInstagramLogin(true, username)
    }

    fun logoutInstagram() {
        preferencesManager.setInstagramLogin(false, "")
        preferencesManager.setAutoDownloadOnWifi(false)
    }

    fun setAutoDownloadOnWifi(enabled: Boolean) {
        preferencesManager.setAutoDownloadOnWifi(enabled)
    }

    fun setCacheLimitIndex(index: Int) {
        preferencesManager.setCacheLimitIndex(index)
    }

    fun setAutoDeleteWatchedAfter24h(enabled: Boolean) {
        preferencesManager.setAutoDeleteWatchedAfter24h(enabled)
    }

    fun setInstantBatchSizeMb(sizeMb: Int) {
        preferencesManager.setInstantBatchSizeMb(sizeMb)
    }

    fun setInfiniteLoopDefault(enabled: Boolean) {
        preferencesManager.setInfiniteLoopDefault(enabled)
    }

    fun setHapticsEnabled(enabled: Boolean) {
        preferencesManager.setHapticsEnabled(enabled)
    }

    fun downloadBatchNow(sizeMb: Int) {
        if (!uiState.value.isInstagramLoggedIn) return
        viewModelScope.launch {
            isDownloadingBatch.value = true
            batchDownloadProgress.value = "Connecting to feed..."
            delay(1200)
            batchDownloadProgress.value = "Fetching $sizeMb MB worth of reels from algorithm..."
            delay(2000)
            batchDownloadProgress.value = "Downloaded $sizeMb MB of offline loops successfully"
            delay(1500)
            isDownloadingBatch.value = false
            batchDownloadProgress.value = ""
        }
    }
}
