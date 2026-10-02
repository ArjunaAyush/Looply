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
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arjunaayush.looply.MainActivity
import com.arjunaayush.looply.core.network.FeedReel
import com.arjunaayush.looply.core.network.InstagramFeedClient
import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AutoDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
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

        // Cap each background WiFi auto-sync cycle to 300 MB maximum for battery efficiency
        val targetBytes = remainingAllowanceBytes.coerceAtMost(300L * 1024 * 1024)
        val targetMb = (targetBytes / (1024 * 1024)).toInt()

        createNotificationChannel()

        val initialNotif = buildNotification(0, targetMb, 0, "Checking for new reels on WiFi...")
        try {
            setForeground(createForegroundInfo(initialNotif))
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground service: ${e.message}")
        }

        try {
            val feedClient = InstagramFeedClient(context)
            val candidateReels = feedClient.fetchAlgorithmReels(minCount = (targetMb / 15).coerceAtLeast(8))

            if (candidateReels.isEmpty()) {
                Log.d(TAG, "Auto-download: No new candidate reels found in feed")
                notificationManager.cancel(NOTIFICATION_ID)
                return@withContext Result.success()
            }

            var totalBytesDownloaded = 0L
            var savedCount = 0

            for (reel in candidateReels) {
                if (isStopped) break

                if (repository.isVideoAlreadyDownloaded(reel.shortcode)) {
                    continue
                }

                val downloadedFile = downloadVideoFile(reel, totalBytesDownloaded, targetBytes) { bytesInFile ->
                    val currentTotal = totalBytesDownloaded + bytesInFile
                    val currentMb = (currentTotal / (1024 * 1024)).toInt()
                    val percent = ((currentTotal.toDouble() / targetBytes) * 100).toInt().coerceIn(0, 100)
                    val status = "Auto-downloading: $currentMb MB / $targetMb MB • Reel #${savedCount + 1}"

                    updateProgress(currentMb, targetMb, percent, status)
                    val notif = buildNotification(currentMb, targetMb, percent, status)
                    notificationManager.notify(NOTIFICATION_ID, notif)
                }

                if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
                    totalBytesDownloaded += downloadedFile.length()
                    savedCount++

                    registerVideoInRepository(repository, downloadedFile, reel)

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

            val finalMb = (totalBytesDownloaded / (1024 * 1024)).toInt()
            if (savedCount > 0) {
                showCompletedNotification("Auto-Download Complete", "Saved $savedCount fresh loops offline ($finalMb MB)")
            } else {
                notificationManager.cancel(NOTIFICATION_ID)
            }

            Result.success(
                workDataOf(
                    KEY_DOWNLOADED_MB to finalMb,
                    KEY_STATUS_MESSAGE to "Auto-download finished ($savedCount reels)"
                )
            )
        } catch (e: CancellationException) {
            Log.d(TAG, "AutoDownloadWorker was cancelled gracefully.")
            notificationManager.cancel(NOTIFICATION_ID)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AutoDownloadWorker failed: ${e.message}", e)
            notificationManager.cancel(NOTIFICATION_ID)
            Result.failure(workDataOf(KEY_STATUS_MESSAGE to (e.message ?: "Failed")))
        }
    }

    private suspend fun cleanUpOldWatchedVideos(repository: VideoRepository) {
        try {
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            val allVideos = repository.getAllVideos()
            for (video in allVideos) {
                if (video.createdAt < oneDayAgo) {
                    repository.deleteVideo(video)
                    Log.d(TAG, "Auto-deleted old video after 24h: ${video.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old videos: ${e.message}")
        }
    }

    private fun getCacheLimitBytes(index: Int): Long {
        return when (index) {
            0 -> 300L * 1024 * 1024
            1 -> 500L * 1024 * 1024
            2 -> 1024L * 1024 * 1024
            3 -> 2048L * 1024 * 1024
            4 -> 3072L * 1024 * 1024
            5 -> 5120L * 1024 * 1024
            else -> 500L * 1024 * 1024
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

            if (conn.responseCode !in 200..299) return null

            val storageDir = File(context.filesDir, "videos").apply {
                if (!exists()) mkdirs()
            }
            val destinationFile = File(storageDir, "reel_${reel.shortcode}_${System.currentTimeMillis()}.mp4")

            conn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var totalFileBytes = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            destinationFile.delete()
                            return null
                        }
                        output.write(buffer, 0, bytesRead)
                        totalFileBytes += bytesRead

                        if (totalFileBytes % (64 * 1024) == 0L) {
                            onChunkProgress(totalFileBytes)
                        }

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

    private suspend fun registerVideoInRepository(
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
            repository.saveVideo(
                details = details,
                reelUrl = "https://www.instagram.com/reel/${reel.shortcode}/",
                author = reel.creatorHandle,
                caption = reel.title
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving video in auto-download: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Looply WiFi Auto-Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when auto-downloading reels on WiFi"
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

    private fun buildNotification(
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
            .setContentTitle("Looply • Auto-Downloading on WiFi ($currentMb MB / $targetMb MB)")
            .setContentText(status)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun showCompletedNotification(title: String, subtitle: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 2,
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
