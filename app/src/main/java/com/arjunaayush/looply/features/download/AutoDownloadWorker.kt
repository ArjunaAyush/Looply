package com.arjunaayush.looply.features.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arjunaayush.looply.MainActivity
import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.data.repository.VideoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class AutoDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val engine: ReelIngestionEngine,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "AutoDownloadWorker"
        const val UNIQUE_WORK_NAME = "looply_wifi_autodownload"

        const val KEY_DOWNLOADED_MB = "auto_downloaded_mb"
        const val KEY_PROGRESS_PERCENT = "auto_progress_percent"
        const val KEY_PROGRESS_FRACTION = "auto_progress_fraction"
        const val KEY_STATUS_MESSAGE = "auto_status_message"

        const val CHANNEL_ID = "looply_auto_downloads"
        const val NOTIFICATION_ID = 8844
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)

        // 1. Verify user preferences and login state
        if (!prefs.autoDownloadOnWifi.value || !prefs.isInstagramLoggedIn.value) {
            Log.d(TAG, "Auto-download skipped: feature disabled or user not logged in")
            return@withContext Result.success()
        }

        val repository = VideoRepository(context)

        // 2. Perform 24-hour auto-deletion of watched reels if enabled
        if (prefs.autoDeleteWatchedAfter24h.value) {
            cleanUpOldWatchedVideos(repository)
        }

        // 3. Check current storage vs cache size limit
        val currentStorageBytes = repository.storageManager.getTotalStorageUsedBytes()
        val cacheLimitBytes = getCacheLimitBytes(prefs.cacheLimitIndex.value)

        val remainingAllowanceBytes = cacheLimitBytes - currentStorageBytes
        if (remainingAllowanceBytes <= 30L * 1024L * 1024L) { // Less than 30 MB available
            Log.d(TAG, "Auto-download skipped: Cache limit reached ($currentStorageBytes / $cacheLimitBytes bytes)")
            return@withContext Result.success()
        }

        val targetCount = ((remainingAllowanceBytes / (8L * 1024 * 1024)).toInt()).coerceIn(4, 20)

        createNotificationChannel()

        val initialNotif = buildNotification(0, targetCount, "Syncing reels on Wi-Fi...")
        try {
            setForeground(createForegroundInfo(initialNotif))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground service: ${e.message}")
        }

        // 1. Capture stage
        val capture = engine.capture(targetCount) { queued ->
            val notif = buildNotification(queued, targetCount, "Found $queued new reels...")
            notificationManager.notify(NOTIFICATION_ID, notif)
        }

        // 2. Download stage
        val downloaded = engine.downloadQueued(targetCount) { done ->
            val notif = buildNotification(done, targetCount, "Downloading reels on Wi-Fi ($done/$targetCount)...")
            notificationManager.notify(NOTIFICATION_ID, notif)
        }

        val finalNotif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Wi-Fi Auto-Download Complete")
            .setContentText("Synced $downloaded reels in background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, finalNotif)

        val output = workDataOf(
            KEY_DOWNLOADED_MB to (downloaded * 7),
            KEY_STATUS_MESSAGE to "Auto-download finished: $downloaded reels saved"
        )

        when (capture) {
            is CaptureResult.Queued -> Result.success(output)
            is CaptureResult.RateLimited, is CaptureResult.Blocked,
            is CaptureResult.NeedsVerification, is CaptureResult.NotLoggedIn -> Result.success(output)
            is CaptureResult.Failed -> if (runAttemptCount < 2) Result.retry() else Result.failure(output)
            else -> Result.success(output)
        }
    }

    private suspend fun cleanUpOldWatchedVideos(repository: VideoRepository) {
        try {
            val all = repository.getAllVideos()
            val now = System.currentTimeMillis()
            val oneDayMillis = 24L * 60 * 60 * 1000

            val toDelete = all.filter { video ->
                video.isWatched && (now - video.createdAt) > oneDayMillis
            }

            for (video in toDelete) {
                repository.deleteVideo(video)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning watched videos: ${e.message}")
        }
    }

    private fun getCacheLimitBytes(index: Int): Long {
        return when (index) {
            0 -> 1L * 1024 * 1024 * 1024       // 1 GB
            1 -> 2L * 1024 * 1024 * 1024       // 2 GB
            2 -> 5L * 1024 * 1024 * 1024       // 5 GB
            3 -> 10L * 1024 * 1024 * 1024      // 10 GB
            else -> 2L * 1024 * 1024 * 1024
        }
    }

    private fun createForegroundInfo(notification: android.app.Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        current: Int,
        max: Int,
        status: String
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Looply Background Sync")
            .setContentText(status)
            .setProgress(max, current, false)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background Wi-Fi downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
