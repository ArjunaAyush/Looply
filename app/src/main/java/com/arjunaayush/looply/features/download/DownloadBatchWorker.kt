package com.arjunaayush.looply.features.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arjunaayush.looply.MainActivity
import com.arjunaayush.looply.core.network.FeedReel
import com.arjunaayush.looply.core.network.InstagramFeedClient
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadBatchWorker(
    private val context: Context,
    workerParams: WorkerParameters
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
        val targetBytes = targetMb * 1024L * 1024L

        createNotificationChannel()

        // Set Foreground Service to prevent background task death
        val initialNotification = buildProgressNotification(0, targetMb, 0, "Connecting to feed...")
        try {
            setForeground(createForegroundInfo(initialNotification))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground service: ${e.message}")
        }

        try {
            val repository = VideoRepository(context)
            val feedClient = InstagramFeedClient(context)

            updateProgress(0, targetMb, 0, "Fetching reels from your algorithm...")

            val candidateReels = feedClient.fetchAlgorithmReels(minCount = (targetMb / 15).coerceAtLeast(10))
            if (candidateReels.isEmpty()) {
                Log.w(TAG, "No reels returned from algorithm fetch")
                showFinishedNotification("No reels found", "Could not fetch reels. Please verify Instagram login in Settings.")
                return@withContext Result.failure(workDataOf(KEY_STATUS_MESSAGE to "No reels found"))
            }

            var totalBytesDownloaded = 0L
            var savedCount = 0

            for (reel in candidateReels) {
                if (isStopped) break

                // Skip if already downloaded
                if (repository.isVideoAlreadyDownloaded(reel.shortcode)) {
                    continue
                }

                val downloadedFile = downloadVideoFile(reel, totalBytesDownloaded, targetBytes, targetMb, savedCount) { bytesInFile ->
                    val currentTotal = totalBytesDownloaded + bytesInFile
                    val currentMb = (currentTotal / (1024 * 1024)).toInt()
                    val percent = ((currentTotal.toDouble() / targetBytes) * 100).toInt().coerceIn(0, 100)
                    val status = "Downloading: $currentMb MB / $targetMb MB ($percent%) • Reel #${savedCount + 1}"

                    updateProgress(currentMb, targetMb, percent, status)
                    val notif = buildProgressNotification(currentMb, targetMb, percent, status)
                    notificationManager.notify(NOTIFICATION_ID, notif)
                }

                if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
                    totalBytesDownloaded += downloadedFile.length()
                    savedCount++

                    // Register file in database
                    registerVideoInRepository(repository, downloadedFile, reel)

                    // Send broadcast for UI update
                    val broadcastIntent = Intent(DownloadReelWorker.ACTION_DOWNLOAD_COMPLETE).apply {
                        setPackage(context.packageName)
                        putExtra(DownloadReelWorker.KEY_VIDEO_ID, downloadedFile.nameWithoutExtension)
                    }
                    context.sendBroadcast(broadcastIntent)
                }

                if (totalBytesDownloaded >= targetBytes) {
                    break
                }
            }

            val finalDownloadedMb = (totalBytesDownloaded / (1024 * 1024)).toInt()
            showFinishedNotification(
                title = "Batch Download Complete",
                subtitle = "Saved $finalDownloadedMb MB of loops offline ($savedCount reels)"
            )

            updateProgress(finalDownloadedMb, targetMb, 100, "Completed: Saved $finalDownloadedMb MB ($savedCount reels)")

            val completionIntent = Intent(ACTION_BATCH_DOWNLOAD_COMPLETE).apply {
                setPackage(context.packageName)
                putExtra(KEY_DOWNLOADED_MB, finalDownloadedMb)
                putExtra(KEY_REELS_SAVED_COUNT, savedCount)
            }
            context.sendBroadcast(completionIntent)

            Result.success(
                workDataOf(
                    KEY_DOWNLOADED_MB to finalDownloadedMb,
                    KEY_REELS_SAVED_COUNT to savedCount
                )
            )
        } catch (e: CancellationException) {
            Log.d(TAG, "DownloadBatchWorker was cancelled gracefully.")
            notificationManager.cancel(NOTIFICATION_ID)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "DownloadBatchWorker failed: ${e.message}", e)
            showFinishedNotification("Batch Download Interrupted", e.message ?: "Network error")
            Result.failure(workDataOf(KEY_STATUS_MESSAGE to (e.message ?: "Failed")))
        }
    }

    private fun updateProgress(
        downloadedMb: Int,
        targetMb: Int,
        percent: Int,
        message: String
    ) {
        val fraction = (percent / 100f).coerceIn(0f, 1f)
        setProgressAsync(
            workDataOf(
                KEY_DOWNLOADED_MB to downloadedMb,
                KEY_TARGET_MB to targetMb,
                KEY_PROGRESS_PERCENT to percent,
                KEY_PROGRESS_FRACTION to fraction,
                KEY_STATUS_MESSAGE to message
            )
        )
    }

    private fun downloadVideoFile(
        reel: FeedReel,
        alreadyDownloadedBytes: Long,
        targetTotalBytes: Long,
        targetMb: Int,
        savedCount: Int,
        onChunkProgress: (bytesInThisFile: Long) -> Unit
    ): File? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(reel.videoUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 20000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                )
            }

            if (conn.responseCode !in 200..299) {
                return null
            }

            val storageDir = File(context.filesDir, "videos").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(storageDir, "reel_${reel.shortcode}_${System.currentTimeMillis()}.mp4")

            conn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16384) // 16KB buffer
                    var bytesRead: Int
                    var totalFileBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            destinationFile.delete()
                            return null
                        }
                        output.write(buffer, 0, bytesRead)
                        totalFileBytes += bytesRead

                        if (totalFileBytes % (64 * 1024) == 0L || totalFileBytes >= 500 * 1024) {
                            onChunkProgress(totalFileBytes)
                        }

                        // Check if total batch threshold reached
                        if (alreadyDownloadedBytes + totalFileBytes >= targetTotalBytes) {
                            break
                        }
                    }
                }
            }

            destinationFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading reel ${reel.shortcode}: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun registerVideoInRepository(
        repository: VideoRepository,
        file: File,
        reel: FeedReel
    ) {
        try {
            var durationMs = 0L
            var width = 0
            var height = 0

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.fromFile(file))
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            val details = ImportedVideoDetails(
                file = file,
                originalName = reel.title.ifBlank { "Reel by @${reel.creatorHandle}" },
                durationMs = durationMs,
                width = width,
                height = height,
                sizeBytes = file.length()
            )
            repository.saveVideo(details)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video to repository: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Looply Batch Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while downloading batch of reels"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
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
        currentMb: Int,
        targetMb: Int,
        percent: Int,
        status: String
    ): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Looply • Downloading Loops ($currentMb MB / $targetMb MB)")
            .setContentText(status)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun showFinishedNotification(title: String, subtitle: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
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
}
