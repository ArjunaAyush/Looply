package com.arjunaayush.looply.features.instagram

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.VideoImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import kotlin.coroutines.resume

/**
 * Direct high-performance downloader for Instagram Reels.
 * Downloads directly from Instagram CDN endpoints without using third-party proxy websites.
 * Runs all network, extraction, and file operations on Dispatchers.IO.
 */
class InstagramDownloader(
    private val context: Context,
    private val repository: VideoRepository,
    private val videoImport: VideoImport,
    @Suppress("UNUSED_PARAMETER") private val rootContainer: ViewGroup? = null
) {

    private val appContext: Context = context.applicationContext
    private val TAG = "InstagramDownloader"

    interface DownloadCallback {
        fun onProgress(message: String)
        fun onSuccess(savedVideo: Video)
        fun onFailure(errorMessage: String)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val IG_APP_ID = "936619743392459"

        /**
         * Extracts the shortcode from any Instagram URL format.
         */
        fun extractShortcode(url: String): String? {
            val cleanUrl = url.split("?").first().trimEnd('/')
            val regex = Regex("""/(?:reels?|p|share/reel|share/p)/([A-Za-z0-9_-]+)""")
            val match = regex.find(cleanUrl)?.groupValues?.get(1)
            if (match != null) return match

            val parts = cleanUrl.split('/')
            return parts.lastOrNull()?.takeIf { it.length in 9..14 }
        }

        /**
         * Normalizes an Instagram URL to a standard reel endpoint.
         */
        fun normalizeInstagramUrl(rawUrl: String): String {
            val shortcode = extractShortcode(rawUrl)
            return if (shortcode != null) {
                "https://www.instagram.com/reel/$shortcode/"
            } else {
                rawUrl.split("?").first()
            }
        }

        /**
         * Cleans Meta CDN URLs by stripping byte-range chunk parameters and unescaping symbols.
         */
        fun cleanCdnVideoUrl(url: String): String {
            return url.replace(Regex("""[&?]bytestart=\d+"""), "")
                .replace(Regex("""[&?]byteend=\d+"""), "")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .replace("&amp;", "&")
        }

        /**
         * Strictly verifies if a given URL is a video stream and NOT a thumbnail image.
         */
        fun isValidVideoStream(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".webp") ||
                lower.contains(".png") || lower.contains("dst-jpg") || lower.contains("dst-png") ||
                lower.contains("/photo") || lower.contains("thumbnail")
            ) {
                return false
            }

            return lower.contains(".mp4") || lower.contains("/o1/v/") || lower.contains("video_dashinit") ||
                    lower.contains("mime_type=video") || lower.contains("/v/t50") || lower.contains("cdninstagram.com")
        }
    }

    /**
     * Extracts active cookies from system CookieManager for authenticated requests.
     */
    private fun getInstagramCookies(): String {
        return try {
            val cm = CookieManager.getInstance()
            val c1 = cm.getCookie("https://www.instagram.com") ?: ""
            val c2 = cm.getCookie("https://instagram.com") ?: ""
            val c3 = cm.getCookie(".instagram.com") ?: ""
            val parts = "$c1; $c2; $c3".split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            parts.joinToString("; ")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Suspend-friendly direct download method for Coroutines and WorkManager.
     */
    suspend fun downloadReelSuspend(
        instagramUrl: String,
        onProgress: (String) -> Unit = {}
    ): Video? = suspendCancellableCoroutine { continuation ->
        downloadReel(instagramUrl, object : DownloadCallback {
            override fun onProgress(message: String) {
                onProgress(message)
            }

            override fun onSuccess(savedVideo: Video) {
                if (continuation.isActive) {
                    continuation.resume(savedVideo)
                }
            }

            override fun onFailure(errorMessage: String) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        })
    }

    /**
     * Main entry point to directly download an Instagram Reel.
     * Executes entirely on Dispatchers.IO with progress reported back to the caller.
     */
    fun downloadReel(instagramUrl: String, callback: DownloadCallback) {
        val shortcode = extractShortcode(instagramUrl)

        callback.onProgress("Connecting to Instagram... ⚡")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // If url is already a direct video stream
                if (isValidVideoStream(instagramUrl)) {
                    withContext(Dispatchers.Main) { callback.onProgress("Downloading direct video stream...") }
                    val saved = downloadAndSaveVideo(instagramUrl, shortcode)
                    withContext(Dispatchers.Main) {
                        if (saved != null) callback.onSuccess(saved)
                        else callback.onFailure("Failed to download video stream.")
                    }
                    return@launch
                }

                if (shortcode.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        callback.onFailure("Could not extract a valid Instagram reel identifier from link.")
                    }
                    return@launch
                }

                val cookies = getInstagramCookies()

                // Strategy 1: Direct Instagram API /?__a=1&__d=dis
                withContext(Dispatchers.Main) { callback.onProgress("Fetching reel stream from Instagram... ⏳") }
                var streamUrl = fetchFromDirectApi(shortcode, cookies)

                // Strategy 2: GraphQL query fallback
                if (streamUrl == null) {
                    streamUrl = fetchFromGraphQl(shortcode, cookies)
                }

                // Strategy 3: Embed page metadata extraction
                if (streamUrl == null) {
                    streamUrl = fetchFromEmbed(shortcode, cookies)
                }

                // Strategy 4: Web HTML OpenGraph & Script tags fallback
                if (streamUrl == null) {
                    streamUrl = fetchFromHtml(shortcode, cookies)
                }

                if (streamUrl != null && isValidVideoStream(streamUrl)) {
                    withContext(Dispatchers.Main) { callback.onProgress("Stream found! Downloading video... 📥") }
                    val saved = downloadAndSaveVideo(streamUrl, shortcode)
                    withContext(Dispatchers.Main) {
                        if (saved != null) {
                            callback.onSuccess(saved)
                        } else {
                            callback.onFailure("Failed to save downloaded video to offline library.")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val authHint = if (!cookies.contains("sessionid")) " (Tip: Connect your Instagram account in Settings to access reels reliably)" else ""
                        callback.onFailure("Could not resolve video stream from Instagram direct link$authHint.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading reel: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback.onFailure("Error downloading reel: ${e.message}")
                }
            }
        }
    }

    /**
     * Strategy 1: Fetch reel stream via Instagram's web info endpoint.
     */
    private fun fetchFromDirectApi(shortcode: String, cookies: String): String? {
        val endpoints = listOf(
            "https://www.instagram.com/reel/$shortcode/?__a=1&__d=dis",
            "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis"
        )

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("X-IG-App-ID", IG_APP_ID)
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("Referer", "https://www.instagram.com/reel/$shortcode/")
                if (cookies.isNotBlank()) {
                    conn.setRequestProperty("Cookie", cookies)
                }
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                if (conn.responseCode in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                    val stream = extractStreamFromJson(responseText)
                    if (stream != null) return stream
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct API endpoint failed for $endpoint: ${e.message}")
            }
        }
        return null
    }

    /**
     * Strategy 2: Fetch reel stream via GraphQL query endpoint.
     */
    private fun fetchFromGraphQl(shortcode: String, cookies: String): String? {
        try {
            val queryUrl = "https://www.instagram.com/graphql/query/?query_hash=b3055c2c4779f6046347356bae831726&variables=%7B%22shortcode%22%3A%22$shortcode%22%7D"
            val url = URL(queryUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("X-IG-App-ID", IG_APP_ID)
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            conn.setRequestProperty("Referer", "https://www.instagram.com/reel/$shortcode/")
            if (cookies.isNotBlank()) {
                conn.setRequestProperty("Cookie", cookies)
            }
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val stream = extractStreamFromJson(responseText)
                if (stream != null) return stream
            }
        } catch (e: Exception) {
            Log.d(TAG, "GraphQL query failed: ${e.message}")
        }
        return null
    }

    /**
     * Extracts direct video stream URL from Instagram JSON structures.
     */
    private fun extractStreamFromJson(jsonStr: String): String? {
        try {
            val root = JSONObject(jsonStr)

            // Format A: items array
            val items = root.optJSONArray("items")
            if (items != null && items.length() > 0) {
                val item = items.getJSONObject(0)
                val videoVersions = item.optJSONArray("video_versions")
                if (videoVersions != null && videoVersions.length() > 0) {
                    val bestVideo = videoVersions.getJSONObject(0)
                    val rawUrl = bestVideo.optString("url")
                    if (rawUrl.isNotBlank()) {
                        return cleanCdnVideoUrl(rawUrl)
                    }
                }
            }

            // Format B: graphql shortcode_media
            val dataObj = root.optJSONObject("data")
            val mediaObj = dataObj?.optJSONObject("shortcode_media")
                ?: root.optJSONObject("graphql")?.optJSONObject("shortcode_media")

            if (mediaObj != null) {
                val videoUrl = mediaObj.optString("video_url")
                if (videoUrl.isNotBlank()) {
                    return cleanCdnVideoUrl(videoUrl)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "JSON parse error: ${e.message}")
        }
        return null
    }

    /**
     * Strategy 3: Embed page video extraction (works without login for public reels).
     */
    private fun fetchFromEmbed(shortcode: String, cookies: String): String? {
        val embedUrls = listOf(
            "https://www.instagram.com/p/$shortcode/embed/captioned/",
            "https://www.instagram.com/reel/$shortcode/embed/captioned/"
        )

        for (endpoint in embedUrls) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                if (cookies.isNotBlank()) {
                    conn.setRequestProperty("Cookie", cookies)
                }
                conn.connectTimeout = 6000
                conn.readTimeout = 6000

                if (conn.responseCode in 200..299) {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }

                    // Pattern 1: "video_url":"..."
                    val videoUrlRegex = Regex(""""video_url"\s*:\s*"([^"]+)"""")
                    val match1 = videoUrlRegex.find(html)
                    if (match1 != null) {
                        return cleanCdnVideoUrl(match1.groupValues[1])
                    }

                    // Pattern 2: <video ... src="..."
                    val videoTagRegex = Regex("""<video[^>]+src=["']([^"']+)["']""")
                    val match2 = videoTagRegex.find(html)
                    if (match2 != null) {
                        return cleanCdnVideoUrl(match2.groupValues[1])
                    }

                    // Pattern 3: og:video in embed
                    val ogRegex = Regex("""<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""")
                    val match3 = ogRegex.find(html)
                    if (match3 != null) {
                        return cleanCdnVideoUrl(match3.groupValues[1])
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Embed extraction failed for $endpoint: ${e.message}")
            }
        }
        return null
    }

    /**
     * Strategy 4: Web page HTML direct extraction.
     */
    private fun fetchFromHtml(shortcode: String, cookies: String): String? {
        try {
            val endpoint = "https://www.instagram.com/reel/$shortcode/"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            if (cookies.isNotBlank()) {
                conn.setRequestProperty("Cookie", cookies)
            }
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode in 200..299) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }

                // Pattern 1: OpenGraph meta tag
                val ogRegex = Regex("""<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""")
                val ogMatch = ogRegex.find(html)
                if (ogMatch != null) {
                    return cleanCdnVideoUrl(ogMatch.groupValues[1])
                }

                // Pattern 2: "video_url":"..."
                val videoUrlRegex = Regex(""""video_url"\s*:\s*"([^"]+)"""")
                val match = videoUrlRegex.find(html)
                if (match != null) {
                    return cleanCdnVideoUrl(match.groupValues[1])
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTML extraction failed: ${e.message}")
        }
        return null
    }

    /**
     * Downloads video stream bytes into a cache file and imports into Looply storage & database.
     * All network and file I/O operations execute strictly on Dispatchers.IO.
     */
    fun downloadAndSaveVideo(streamUrl: String, shortcode: String? = null): Video? {
        val cleanedUrl = cleanCdnVideoUrl(streamUrl)
        return tryDownloadFromUrl(cleanedUrl, shortcode)
    }

    private fun tryDownloadFromUrl(candidateUrl: String, shortcode: String? = null): Video? {
        val tempFile = File(appContext.cacheDir, "temp_ig_${System.currentTimeMillis()}.mp4")
        var currentUrl = candidateUrl
        var redirects = 0

        while (redirects < 5) {
            try {
                val url = URL(currentUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Referer", "https://www.instagram.com/")
                conn.setRequestProperty("Accept", "*/*")
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 40000

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrEmpty()) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }

                if (code !in 200..299) {
                    Log.w(TAG, "HTTP $code from $currentUrl")
                    return null
                }

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }

                if (tempFile.exists() && tempFile.length() > 50_000) {
                    val videoTitle = if (!shortcode.isNullOrEmpty()) "Reel • $shortcode.mp4" else "Reel_${System.currentTimeMillis()}.mp4"
                    val importedDetails = videoImport.importVideo(Uri.fromFile(tempFile), videoTitle)
                    return repository.saveVideo(importedDetails)
                } else {
                    Log.w(TAG, "Downloaded file too small: ${tempFile.length()} bytes from $currentUrl")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download failed from $currentUrl: ${e.message}")
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
            break
        }
        return null
    }
}
