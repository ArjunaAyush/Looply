package com.arjunaayush.looply.features.share

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.arjunaayush.looply.MainActivity
import com.arjunaayush.looply.R
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ShareIntentHandler
import com.arjunaayush.looply.features.importvideo.VideoImport
import com.arjunaayush.looply.features.instagram.InstagramDownloader

/**
 * Headless / Transparent Activity that handles incoming shares from Instagram and other apps.
 * Downloads and saves the reel completely in the background without redirecting or taking over
 * the user's screen. The user stays right in Instagram uninterrupted.
 */
class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var repository: VideoRepository
    private lateinit var videoImport: VideoImport
    private lateinit var shareIntentHandler: ShareIntentHandler
    private lateinit var instagramDownloader: InstagramDownloader
    private lateinit var rootContainer: FrameLayout

    private val channelId = "looply_bg_downloads"
    private val notificationId = 7711
    private lateinit var notificationManager: NotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make activity completely non-intrusive and transparent
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        rootContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootContainer)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        repository = VideoRepository(this)
        val storageManager = repository.storageManager
        videoImport = VideoImport(this, storageManager)
        shareIntentHandler = ShareIntentHandler(this, videoImport)
        instagramDownloader = InstagramDownloader(this, repository, videoImport, rootContainer)

        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent?) {
        if (intent == null) {
            finishAndRemoveTask()
            return
        }

        // 1. Text & links shared from Instagram, YouTube, Browser
        if (shareIntentHandler.isTextShareIntent(intent)) {
            val text = shareIntentHandler.extractSharedText(intent)
            val url = shareIntentHandler.extractUrl(text) ?: text
            if (url.isNullOrEmpty()) {
                finishAndRemoveTask()
                return
            }

            if (shareIntentHandler.isInstagramUrl(url)) {
                downloadInstagramReelInBackground(url)
            } else if (url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".webm") || url.contains("video")) {
                downloadDirectVideoInBackground(url)
            } else {
                Toast.makeText(applicationContext, "Looply: Not an Instagram reel link", Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
            }
            return
        }

        // 2. Direct video file shared from Gallery, Files, WhatsApp
        if (shareIntentHandler.isVideoShareIntent(intent)) {
            importSharedVideosInBackground(intent)
            return
        }

        finishAndRemoveTask()
    }

    private fun downloadInstagramReelInBackground(instagramUrl: String) {
        Toast.makeText(applicationContext, "Looply: Downloading reel in background... ⚡", Toast.LENGTH_SHORT).show()
        showProgressNotification("Resolving & downloading reel...")

        instagramDownloader.downloadReel(instagramUrl, object : InstagramDownloader.DownloadCallback {
            override fun onProgress(message: String) {
                updateProgressNotification(message)
            }

            override fun onSuccess(savedVideo: Video) {
                Toast.makeText(applicationContext, "Looply: Reel saved to offline library! ❤️", Toast.LENGTH_SHORT).show()
                showSuccessNotification(savedVideo)
                finishAndRemoveTask()
            }

            override fun onFailure(errorMessage: String) {
                Toast.makeText(applicationContext, "Looply: Could not download reel", Toast.LENGTH_SHORT).show()
                notificationManager.cancel(notificationId)
                finishAndRemoveTask()
            }
        })
    }

    private fun downloadDirectVideoInBackground(videoUrl: String) {
        Toast.makeText(applicationContext, "Looply: Downloading video in background... ⚡", Toast.LENGTH_SHORT).show()
        showProgressNotification("Downloading video file...")

        Thread {
            val savedVideo = instagramDownloader.downloadAndSaveVideo(videoUrl)
            runOnUiThread {
                if (savedVideo != null) {
                    Toast.makeText(applicationContext, "Looply: Video saved to offline library! ❤️", Toast.LENGTH_SHORT).show()
                    showSuccessNotification(savedVideo)
                } else {
                    Toast.makeText(applicationContext, "Looply: Download failed", Toast.LENGTH_SHORT).show()
                    notificationManager.cancel(notificationId)
                }
                finishAndRemoveTask()
            }
        }.start()
    }

    private fun importSharedVideosInBackground(intent: Intent) {
        Toast.makeText(applicationContext, "Looply: Saving video to offline library... ⚡", Toast.LENGTH_SHORT).show()

        Thread {
            val uris = shareIntentHandler.extractSharedVideoUris(intent)
            var lastSavedVideo: Video? = null
            for (uri in uris) {
                try {
                    val imported = videoImport.importVideo(uri)
                    lastSavedVideo = repository.saveVideo(imported)
                } catch (_: Exception) {}
            }

            runOnUiThread {
                if (lastSavedVideo != null) {
                    val total = repository.getAllVideos().size
                    Toast.makeText(applicationContext, "Looply: Video saved! ($total total) ❤️", Toast.LENGTH_SHORT).show()
                    showSuccessNotification(lastSavedVideo)
                }
                finishAndRemoveTask()
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
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
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Looply • Downloading Reel ⚡")
            .setContentText(message)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {}
    }

    private fun updateProgressNotification(message: String) {
        showProgressNotification(message)
    }

    private fun showSuccessNotification(savedVideo: Video) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PLAY_VIDEO_ID", savedVideo.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            savedVideo.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Reel Saved Offline ❤️")
            .setContentText(savedVideo.title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {}
    }
}
