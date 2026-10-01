package com.arjunaayush.looply.features.importvideo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Handles incoming share intents from other apps (Gallery, Google Photos, WhatsApp, etc.).
 * Supports single and multiple video sharing, extracting URIs from EXTRA_STREAM, clipData, or Intent data.
 */
class ShareIntentHandler(
    private val context: Context,
    private val videoImport: VideoImport
) {

    /**
     * Checks if the incoming intent has shared video data.
     */
    fun isVideoShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action ?: return false
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return false

        val type = intent.type
        if (type != null && (type.startsWith("video/") || type == "application/mp4")) {
            return true
        }

        // Also check if any uri has a video mime or video extension
        val uris = extractSharedVideoUris(intent)
        return uris.isNotEmpty()
    }

    /**
     * Checks if the incoming intent has shared text or links (e.g. from Instagram, browser, YouTube).
     */
    fun isTextShareIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action ?: return false
        if (action != Intent.ACTION_SEND) return false

        val type = intent.type ?: ""
        if (type.startsWith("text/")) return true
        return intent.hasExtra(Intent.EXTRA_TEXT)
    }

    /**
     * Extracts text or URL from an incoming text share intent.
     */
    fun extractSharedText(intent: Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }

    /**
     * Extracts a web URL (e.g. https://www.instagram.com/reel/...) from the given text.
     */
    fun extractUrl(text: String?): String? {
        if (text == null) return null
        val regex = Regex("""https?://[^\s<>"']+""")
        return regex.find(text)?.value
    }

    /**
     * Checks if a URL is an Instagram post or reel.
     */
    fun isInstagramUrl(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("instagram.com/reel") ||
                lower.contains("instagram.com/reels") ||
                lower.contains("instagram.com/p/") ||
                lower.contains("instagram.com/share") ||
                lower.contains("instagr.am/")
    }

    /**
     * Extracts all shared video Uris from single or multiple send intents,
     * checking EXTRA_STREAM, clipData, and intent data.
     */
    fun extractSharedVideoUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val uris = mutableListOf<Uri>()

        // 1. Check EXTRA_STREAM as single Parcelable Uri (ACTION_SEND)
        val singleUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        if (singleUri != null && isVideoUri(singleUri)) {
            uris.add(singleUri)
        }

        // 2. Check EXTRA_STREAM as ArrayList<Uri> (ACTION_SEND_MULTIPLE)
        val streamList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        streamList?.forEach { uri ->
            if (uri != null && uri !in uris && isVideoUri(uri)) {
                uris.add(uri)
            }
        }

        // 3. Check ClipData (standard across Android when sharing files)
        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val itemUri = clipData.getItemAt(i)?.uri
                if (itemUri != null && itemUri !in uris && isVideoUri(itemUri)) {
                    uris.add(itemUri)
                }
            }
        }

        // 4. Check Intent.data as fallback
        val dataUri = intent.data
        if (dataUri != null && dataUri !in uris && isVideoUri(dataUri)) {
            uris.add(dataUri)
        }

        return uris
    }

    private fun isVideoUri(uri: Uri): Boolean {
        try {
            val type = context.contentResolver.getType(uri)
            if (type != null && type.startsWith("video/")) return true
        } catch (_: Exception) {}

        val path = uri.path?.lowercase() ?: ""
        return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".mov") ||
                path.endsWith(".webm") || path.endsWith(".3gp") || path.contains("video")
    }

    /**
     * Imports all shared video Uris into local storage.
     */
    fun handleSharedVideos(intent: Intent?): List<ImportedVideoDetails> {
        val uris = extractSharedVideoUris(intent)
        val importedList = mutableListOf<ImportedVideoDetails>()

        for (uri in uris) {
            try {
                val imported = videoImport.importVideo(uri)
                importedList.add(imported)
            } catch (_: Exception) {
                // Continue importing remaining
            }
        }
        return importedList
    }
}
