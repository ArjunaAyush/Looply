package com.arjunaayush.looply.features.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.download.DownloadReelWorker
import com.arjunaayush.looply.features.importvideo.ShareIntentHandler
import com.arjunaayush.looply.features.importvideo.VideoImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Headless / Transparent Activity that handles incoming shares from Instagram and other apps.
 * Immediately delegates the work to WorkManager/Dispatchers.IO and finishes instantly (< 20ms),
 * ensuring the screen and Instagram UI never freeze or become unresponsive.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val repository = VideoRepository(applicationContext)
        val videoImport = VideoImport(applicationContext, repository.storageManager)
        val shareIntentHandler = ShareIntentHandler(applicationContext, videoImport)

        // 1. Text & links shared from Instagram, YouTube, Browser
        if (shareIntentHandler.isTextShareIntent(intent)) {
            val text = shareIntentHandler.extractSharedText(intent)
            val url = shareIntentHandler.extractUrl(text) ?: text
            if (url.isNullOrEmpty()) {
                finishAndRemoveTask()
                return
            }

            if (shareIntentHandler.isInstagramUrl(url)) {
                Toast.makeText(applicationContext, "Looply: Downloading reel in background... ⚡", Toast.LENGTH_SHORT).show()
                DownloadReelWorker.enqueue(applicationContext, url)
                finishAndRemoveTask()
                return
            } else if (url.endsWith(".mp4") || url.endsWith(".mov") || url.endsWith(".webm") || url.contains("video")) {
                Toast.makeText(applicationContext, "Looply: Downloading video in background... ⚡", Toast.LENGTH_SHORT).show()
                DownloadReelWorker.enqueue(applicationContext, url)
                finishAndRemoveTask()
                return
            } else {
                Toast.makeText(applicationContext, "Looply: Not an Instagram reel link", Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
                return
            }
        }

        // 2. Direct video file shared from Gallery, Files, WhatsApp
        if (shareIntentHandler.isVideoShareIntent(intent)) {
            Toast.makeText(applicationContext, "Looply: Saving video to offline library... ⚡", Toast.LENGTH_SHORT).show()

            val appContext = applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val uris = shareIntentHandler.extractSharedVideoUris(intent)
                for (uri in uris) {
                    try {
                        val imported = videoImport.importVideo(uri)
                        repository.saveVideo(imported)
                    } catch (_: Exception) {}
                }
            }

            finishAndRemoveTask()
            return
        }

        finishAndRemoveTask()
    }
}
