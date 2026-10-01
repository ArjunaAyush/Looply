package com.arjunaayush.looply.features.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arjunaayush.looply.MainActivity
import com.arjunaayush.looply.R
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.VideoImport
import com.arjunaayush.looply.features.instagram.InstagramDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Robust WorkManager CoroutineWorker for downloading Instagram Reels and direct videos.
 * Runs completely in the background on Dispatchers.IO, survives activity destruction,
 * updates notifications, prevents duplicate downloads, and keeps UI completely responsive.
 */
class DownloadReelWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DownloadReelWorker"
        const val KEY_URL = "extra_download_url"
        const val KEY_VIDEO_ID = "extra_video_id"
        const val KEY_ERROR_MESSAGE = "extra_error_message"
        const val KEY_PROGRESS_MESSAGE = "extra_progress_message"

        const val CHANNEL_ID = "looply_bg_downloads"
        const val NOTIFICATION_ID = 8822
        const val ACTION_DOWNLOAD_COMPLETE = "com.arjunaayush.looply.ACTION_DOWNLOAD_COMPLETE"

        /**
         * Enqueues a background download with duplicate prevention.
         */
        fun enqueue(context: Context, url: String) {
            val shortcode = InstagramDownloader.extractShortcode(url)
            val uniqueWorkName = "download_reel_${shortcode ?: url.hashCode()}"

            val inputData = workDataOf(KEY_URL to url)
            val request = OneTimeWorkRequestBuilder<DownloadReelWorker>()
                .setInputData(inputData)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL)
        if (url.isNullOrBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Invalid download URL"))
        }

        createNotificationChannel()

        val repository = VideoRepository(context)
        val videoImport = VideoImport(context, repository.storageManager)
        val downloader = InstagramDownloader(context, repository, videoImport)

        val shortcode = InstagramDownloader.extractShortcode(url)

        // 1. Prevent duplicate download if already saved in database
        if (shortcode != null && repository.isVideoAlreadyDownloaded(shortcode)) {
            showSuccessNotification("Reel already saved in offline library! ❤️", "Reel • $shortcode.mp4")
            return@withContext Result.success(workDataOf(KEY_PROGRESS_MESSAGE to "Already downloaded"))
        }

        // 2. Post initial download progress notification
        showProgressNotification("Connecting to FastVideoSave... ⚡")

        try {
            if (isStopped) return@withContext Result.failure()

            val savedVideo = if (downloader.run { InstagramDownloader.extractShortcode(url) } != null ||
                url.contains("instagram.com") || url.contains("instagr.am")) {
                downloader.downloadReelSuspend(url) { progressMsg ->
                    showProgressNotification(progressMsg)
                    // Update worker progress for LiveData observers
                    setProgressAsync(workDataOf(KEY_PROGRESS_MESSAGE to progressMsg))
                }
            } else {
                // Direct video link (.mp4, .mov, etc.)
                showProgressNotification("Downloading video file... ⚡")
                downloader.downloadAndSaveVideo(url)
            }

            if (isStopped) {
                notificationManager.cancel(NOTIFICATION_ID)
                return@withContext Result.failure()
            }

            if (savedVideo != null) {
                showSuccessNotification("Reel Saved Offline ❤️", savedVideo.title, savedVideo.id)

                // Send broadcast so active screens (Reels, Saved) update without user refresh
                val broadcastIntent = Intent(ACTION_DOWNLOAD_COMPLETE).apply {
                    setPackage(context.packageName)
                    putExtra(KEY_VIDEO_ID, savedVideo.id)
                }
                context.sendBroadcast(broadcastIntent)

                Result.success(workDataOf(KEY_VIDEO_ID to savedVideo.id))
            } else {
                showFailureNotification("Could not resolve or download reel.")
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Could not resolve video stream"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in DownloadReelWorker: ${e.message}", e)
            showFailureNotification("Download failed: ${e.message}")
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Looply Background Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when downloading reels in background"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Looply • Downloading Reel ⚡")
            .setContentText(message)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun showSuccessNotification(title: String, subtitle: String, videoId: String? = null) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (videoId != null) {
                putExtra("EXTRA_PLAY_VIDEO_ID", videoId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (videoId ?: subtitle).hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun showFailureNotification(message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Looply • Download Failed")
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }
}
