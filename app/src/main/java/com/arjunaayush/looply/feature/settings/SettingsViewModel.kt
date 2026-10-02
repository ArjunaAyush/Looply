package com.arjunaayush.looply.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.arjunaayush.looply.BuildConfig
import com.arjunaayush.looply.core.network.instagram.config.IngestionConfigRepository
import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.features.download.AutoDownloadScheduler
import com.arjunaayush.looply.features.download.AutoDownloadWorker
import com.arjunaayush.looply.features.download.BlockReason
import com.arjunaayush.looply.features.download.DownloadBatchWorker
import com.arjunaayush.looply.features.download.IngestionGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val batchDownloadProgress: String = "",
    val batchDownloadProgressFraction: Float = 0f,
    val isAutoDownloading: Boolean = false,
    val autoDownloadProgress: String = "",
    val autoDownloadProgressFraction: Float = 0f,
    val feedIngestionEnabled: Boolean = BuildConfig.FEED_INGESTION,
    val ingestionBlockReason: BlockReason? = null
) {
    val selectedCacheLimitText: String
        get() = PreferencesManager.CACHE_LIMIT_OPTIONS.getOrElse(cacheLimitIndex) { "500 MB" }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val ingestionGuard: IngestionGuard,
    private val ingestionConfigRepository: IngestionConfigRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val isDownloadingBatch = MutableStateFlow(false)
    private val batchDownloadProgress = MutableStateFlow("")
    private val batchDownloadProgressFraction = MutableStateFlow(0f)

    private val isAutoDownloading = MutableStateFlow(false)
    private val autoDownloadProgress = MutableStateFlow("")
    private val autoDownloadProgressFraction = MutableStateFlow(0f)

    private val ingestionBlockReason = MutableStateFlow<BlockReason?>(null)

    init {
        refreshBlockReason()

        // Observe Batch Download Worker state & live progress
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(DownloadBatchWorker.UNIQUE_WORK_NAME)
                .collect { workInfos ->
                    val info = workInfos.firstOrNull()
                    if (info != null) {
                        val isRunning = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
                        isDownloadingBatch.value = isRunning
                        if (isRunning) {
                            val msg = info.progress.getString(DownloadBatchWorker.KEY_STATUS_MESSAGE)
                                ?: "Downloading reels in background..."
                            val fraction = info.progress.getFloat(DownloadBatchWorker.KEY_PROGRESS_FRACTION, 0f)
                            batchDownloadProgress.value = msg
                            batchDownloadProgressFraction.value = fraction
                        } else if (info.state == WorkInfo.State.SUCCEEDED) {
                            val savedCount = info.outputData.getInt(DownloadBatchWorker.KEY_REELS_SAVED_COUNT, 0)
                            val downloadedMb = info.outputData.getInt(DownloadBatchWorker.KEY_DOWNLOADED_MB, 0)
                            val statusMsg = info.outputData.getString(DownloadBatchWorker.KEY_STATUS_MESSAGE)
                            batchDownloadProgress.value = if (savedCount > 0) {
                                "Finished: Saved $savedCount loops ($downloadedMb MB)"
                            } else {
                                statusMsg ?: "Finished: No new reels found"
                            }
                            batchDownloadProgressFraction.value = 1f
                            isDownloadingBatch.value = false
                            refreshBlockReason()
                        } else if (info.state == WorkInfo.State.CANCELLED) {
                            batchDownloadProgress.value = "Downloads stopped"
                            batchDownloadProgressFraction.value = 0f
                            isDownloadingBatch.value = false
                        } else if (info.state == WorkInfo.State.FAILED) {
                            val statusMsg = info.outputData.getString(DownloadBatchWorker.KEY_STATUS_MESSAGE)
                            batchDownloadProgress.value = statusMsg ?: "Batch download finished or stopped"
                            batchDownloadProgressFraction.value = 0f
                            isDownloadingBatch.value = false
                            refreshBlockReason()
                        }
                    }
                }
        }

        // Observe Auto-Download Worker state & live progress
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(AutoDownloadWorker.TAG)
                .collect { workInfos ->
                    val runningInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    if (runningInfo != null) {
                        isAutoDownloading.value = true
                        autoDownloadProgress.value = runningInfo.progress.getString(AutoDownloadWorker.KEY_STATUS_MESSAGE)
                            ?: "Auto-downloading reels on WiFi..."
                        autoDownloadProgressFraction.value = runningInfo.progress.getFloat(AutoDownloadWorker.KEY_PROGRESS_FRACTION, 0f)
                    } else {
                        isAutoDownloading.value = false
                    }
                }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferencesManager.isInstagramLoggedIn,
            preferencesManager.instagramUsername,
            preferencesManager.autoDownloadOnWifi,
            preferencesManager.cacheLimitIndex,
            preferencesManager.autoDeleteWatchedAfter24h
        ) { p1, p2, p3, p4, p5 ->
            arrayOf<Any>(p1, p2, p3, p4, p5)
        },
        combine(
            preferencesManager.instantBatchSizeMb,
            preferencesManager.infiniteLoopDefault,
            preferencesManager.hapticsEnabled,
            isDownloadingBatch,
            batchDownloadProgress
        ) { p6, p7, p8, p9, p10 ->
            arrayOf<Any>(p6, p7, p8, p9, p10)
        },
        combine(
            batchDownloadProgressFraction,
            isAutoDownloading,
            autoDownloadProgress,
            autoDownloadProgressFraction,
            ingestionBlockReason
        ) { p11, p12, p13, p14, p15 ->
            arrayOf<Any?>(p11, p12, p13, p14, p15)
        }
    ) { group1, group2, group3 ->
        SettingsUiState(
            isInstagramLoggedIn = group1[0] as Boolean,
            instagramUsername = group1[1] as String,
            autoDownloadOnWifi = group1[2] as Boolean,
            cacheLimitIndex = group1[3] as Int,
            autoDeleteWatchedAfter24h = group1[4] as Boolean,
            instantBatchSizeMb = group2[0] as Int,
            infiniteLoopDefault = group2[1] as Boolean,
            hapticsEnabled = group2[2] as Boolean,
            isDownloadingBatch = group2[3] as Boolean,
            batchDownloadProgress = group2[4] as String,
            batchDownloadProgressFraction = group3[0] as Float,
            isAutoDownloading = group3[1] as Boolean,
            autoDownloadProgress = group3[2] as String,
            autoDownloadProgressFraction = group3[3] as Float,
            feedIngestionEnabled = BuildConfig.FEED_INGESTION,
            ingestionBlockReason = group3[4] as? BlockReason
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun refreshBlockReason() {
        viewModelScope.launch {
            val config = ingestionConfigRepository.current()
            ingestionBlockReason.value = ingestionGuard.blockReason(config)
        }
    }

    fun clearVerificationBlock() {
        ingestionGuard.clearUserActionFlags()
        refreshBlockReason()
    }

    fun getLatestDebugCaptureFile(): File? {
        val debugDir = File(context.filesDir, "debug/captures")
        if (!debugDir.exists()) return null
        return debugDir.listFiles()?.maxByOrNull { it.lastModified() }
    }

    fun onInstagramLoginSuccess(username: String, cookies: String = "") {
        preferencesManager.setInstagramLogin(true, username, cookies)
        ingestionGuard.clearUserActionFlags()
        refreshBlockReason()
        if (preferencesManager.autoDownloadOnWifi.value) {
            AutoDownloadScheduler.schedule(context)
        }
    }

    fun logoutInstagram() {
        preferencesManager.setInstagramLogin(false, "")
        preferencesManager.setAutoDownloadOnWifi(false)
        AutoDownloadScheduler.cancel(context)
        refreshBlockReason()
    }

    fun setAutoDownloadOnWifi(enabled: Boolean) {
        preferencesManager.setAutoDownloadOnWifi(enabled)
        if (enabled && uiState.value.isInstagramLoggedIn) {
            AutoDownloadScheduler.schedule(context)
        } else {
            AutoDownloadScheduler.cancel(context)
        }
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
        isDownloadingBatch.value = true
        batchDownloadProgress.value = "Enqueuing batch download..."
        batchDownloadProgressFraction.value = 0f
        DownloadBatchWorker.enqueue(context, sizeMb)
    }

    fun stopDownloading() {
        workManager.cancelUniqueWork(DownloadBatchWorker.UNIQUE_WORK_NAME)
        workManager.cancelAllWorkByTag(AutoDownloadWorker.TAG)
        isDownloadingBatch.value = false
        batchDownloadProgress.value = "Downloads stopped."
        batchDownloadProgressFraction.value = 0f
        isAutoDownloading.value = false
        autoDownloadProgress.value = ""
        autoDownloadProgressFraction.value = 0f
    }
}
