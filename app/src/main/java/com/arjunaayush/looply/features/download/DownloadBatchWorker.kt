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
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arjunaayush.looply.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class DownloadBatchWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val engine: ReelIngestionEngine,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DownloadBatchWorker"
        const val UNIQUE_WORK_NAME = "looply_instant_batch_download"

        const val KEY_TARGET_MB = "extra_target_mb"
        const val KEY_DOWNLOADED_MB = "extra_downloaded_mb"
        const val KEY_PROGRESS_PERCENT = "extra_progress_percent"
        const val KEY_PROGRESS_FRACTION = "extra_progress_fraction"
        const val KEY_REELS_SAVED_COUNT = "extra_reels_saved_count"
        const val KEY_STATUS_MESSAGE = "extra_status_message"

        const val CHANNEL_ID = "looply_batch_downloads"
        const val NOTIFICATION_ID = 8833
        const val ACTION_BATCH_DOWNLOAD_COMPLETE = "com.arjunaayush.looply.ACTION_BATCH_DOWNLOAD_COMPLETE"

        fun enqueue(context: Context, targetMb: Int) {
            val inputData = workDataOf(KEY_TARGET_MB to targetMb)
            val request = OneTimeWorkRequestBuilder<DownloadBatchWorker>()
                .setInputData(inputData)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targetMb = inputData.getInt(KEY_TARGET_MB, 100)
        val targetCount = (targetMb / 7).coerceAtLeast(1)

        createNotificationChannel()

        val initialNotif = buildProgressNotification(0, targetCount, "Connecting to Instagram feed...")
        try {
            setForeground(createForegroundInfo(initialNotif))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground service: ${e.message}")
        }

        val result = engine.ingestBatch(
            targetMb = targetMb,
            isCancelled = { isStopped },
            onProgress = { done, target, mb, status ->
                val notif = buildProgressNotification(done, target, "$status ($mb MB / $targetMb MB)")
                notificationManager.notify(NOTIFICATION_ID, notif)
                val fraction = (mb.toFloat() / targetMb.toFloat()).coerceIn(0f, 1f)
                setProgressAsync(workDataOf(
                    KEY_REELS_SAVED_COUNT to done,
                    KEY_DOWNLOADED_MB to mb,
                    KEY_PROGRESS_PERCENT to (fraction * 100).toInt(),
                    KEY_PROGRESS_FRACTION to fraction,
                    KEY_STATUS_MESSAGE to status
                ))
            }
        )

        val downloadedCount = when (result) {
            is BatchIngestionResult.Success -> result.downloadedCount
            is BatchIngestionResult.Stopped -> result.downloadedCount
            is BatchIngestionResult.Failed -> 0
        }
        val downloadedBytes = when (result) {
            is BatchIngestionResult.Success -> result.downloadedBytes
            is BatchIngestionResult.Stopped -> result.downloadedBytes
            is BatchIngestionResult.Failed -> 0L
        }
        val downloadedMb = (downloadedBytes / (1024 * 1024)).toInt()
        val statusMsg = when (result) {
            is BatchIngestionResult.Success -> result.message
            is BatchIngestionResult.Stopped -> result.reason
            is BatchIngestionResult.Failed -> "Download failed: ${result.error}"
        }

        // Final notification
        val finalNotif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Batch Download Complete")
            .setContentText("Successfully saved $downloadedCount reels ($downloadedMb MB)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, finalNotif)

        context.sendBroadcast(Intent(ACTION_BATCH_DOWNLOAD_COMPLETE).setPackage(context.packageName))

        val output = workDataOf(
            KEY_REELS_SAVED_COUNT to downloadedCount,
            KEY_DOWNLOADED_MB to downloadedMb,
            KEY_STATUS_MESSAGE to statusMsg
        )

        when (result) {
            is BatchIngestionResult.Success -> Result.success(output)
            is BatchIngestionResult.Stopped -> Result.success(output)
            is BatchIngestionResult.Failed -> Result.failure(output)
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

    private fun buildProgressNotification(
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
            .setContentTitle("Looply Reel Downloader")
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
                "Batch Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of batch reel downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
