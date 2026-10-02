package com.arjunaayush.looply.features.instagram

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.core.network.instagram.ReelJsonNormalizer
import com.arjunaayush.looply.features.importvideo.VideoImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Direct high-performance downloader for Instagram Reels.
 * Resolves streams directly from Instagram using official media endpoints when logged in,
 * and a headless offscreen Instagram WebView stream interceptor for public/unauthenticated reels.
 * Runs all network and file operations strictly on Dispatchers.IO.
 */
class InstagramDownloader(
    private val context: Context,
    private val repository: VideoRepository,
    private val videoImport: VideoImport,
    @Suppress("UNUSED_PARAMETER") private val rootContainer: ViewGroup? = null
) {

    private val appContext: Context = context.applicationContext
    private val TAG = "InstagramDownloader"
    private val mainHandler = Handler(Looper.getMainLooper())

    interface DownloadCallback {
        fun onProgress(message: String)
        fun onSuccess(savedVideo: Video)
        fun onFailure(errorMessage: String)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Instagram 319.0.0.36.108 Android (33/13; 480dpi; 1080x2400; samsung; SM-G998B; p3s; exynos2100; en_US; 570123456)"
        private const val IG_APP_ID = "936619743392459"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun extractShortcode(url: String): String? {
            val cleanUrl = url.split("?").first().trimEnd('/')
            val regex = Regex("""/(?:reels?|p|share/reel|share/p)/([A-Za-z0-9_-]+)""")
            val match = regex.find(cleanUrl)?.groupValues?.get(1)
            if (match != null) return match

            val parts = cleanUrl.split('/')
            return parts.lastOrNull()?.takeIf { it.length in 9..14 }
        }

        fun shortcodeToMediaId(shortcode: String): String {
            val cleanCode = if (shortcode.length > 28) shortcode.substring(0, shortcode.length - 28) else shortcode
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (ch in cleanCode) {
                val index = ALPHABET.indexOf(ch)
                if (index >= 0) {
                    id = id.multiply(base).add(BigInteger.valueOf(index.toLong()))
                }
            }
            return id.toString()
        }

        fun cleanCdnVideoUrl(url: String): String {
            return url.replace(Regex("""[&?]bytestart=\d+"""), "")
                .replace(Regex("""[&?]byteend=\d+"""), "")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .replace("&amp;", "&")
        }

        fun isValidVideoStream(url: String): Boolean {
            val lower = url.lowercase()
            if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".webp") ||
                lower.contains(".png") || lower.contains("dst-jpg") || lower.contains("dst-png") ||
                lower.contains("/photo") || lower.contains("thumbnail")
            ) {
                return false
            }

            return lower.contains(".mp4") || lower.contains("/o1/v/") || lower.contains("video_dashinit") ||
                    lower.contains("mime_type=video") || lower.contains("/v/t50") ||
                    (lower.contains("cdninstagram.com") && lower.contains("video"))
        }
    }

    private fun getInstagramCookies(): String {
        val prefCookies = try {
            PreferencesManager(appContext).getInstagramCookies()
        } catch (_: Exception) { "" }

        val cm = try { CookieManager.getInstance() } catch (_: Exception) { null }
        val c1 = cm?.getCookie("https://www.instagram.com") ?: ""
        val c2 = cm?.getCookie("https://instagram.com") ?: ""
        val c3 = cm?.getCookie("https://m.instagram.com") ?: ""
        val c4 = cm?.getCookie(".instagram.com") ?: ""

        val combined = "$prefCookies; $c1; $c2; $c3; $c4"
        val parts = combined.split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return parts.joinToString("; ")
    }

    /**
     * Direct suspendable method for Coroutines and WorkManager.
     */
    suspend fun downloadReelSuspend(
        instagramUrl: String,
        onProgress: (String) -> Unit = {}
    ): Video? = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(instagramUrl)
        Log.d(TAG, "Starting reel download for: $instagramUrl (extracted shortcode: $shortcode)")

        // 1. Direct video link (.mp4)
        if (isValidVideoStream(instagramUrl)) {
            Log.d(TAG, "URL is already a valid video stream: $instagramUrl")
            onProgress("Downloading direct video stream...")
            return@withContext downloadAndSaveVideo(instagramUrl, shortcode)
        }

        if (shortcode.isNullOrBlank()) {
            Log.w(TAG, "Could not extract valid shortcode from: $instagramUrl")
            return@withContext null
        }

        val cookies = getInstagramCookies()
        val mediaId = shortcodeToMediaId(shortcode)
        Log.d(TAG, "Resolved shortcode $shortcode -> mediaId: $mediaId (has session: ${cookies.contains("sessionid")})")

        // 2. Direct Instagram Media API (Fast path for authenticated sessions)
        onProgress("Connecting to Instagram... ⚡")
        var streamUrl: String? = null

        if (cookies.contains("sessionid") && mediaId.isNotBlank()) {
            onProgress("Querying reel metadata... ⏳")
            streamUrl = fetchFromMediaInfoApi(mediaId, shortcode, cookies)
            Log.d(TAG, "Media info API returned stream: ${streamUrl != null}")
        }

        // 3. Direct /?__a=1&__d=dis endpoint
        if (streamUrl == null) {
            streamUrl = fetchFromDirectEndpoint(shortcode, cookies)
            Log.d(TAG, "Direct /?__a=1 endpoint returned stream: ${streamUrl != null}")
        }

        // 4. Fallback: Headless Offscreen Instagram WebView Stream Interceptor
        if (streamUrl == null) {
            onProgress("Resolving stream via Instagram Web Player... ⏳")
            Log.d(TAG, "Launching Headless Instagram WebView Resolver for shortcode: $shortcode")
            streamUrl = resolveViaHeadlessWebView(shortcode)
            Log.d(TAG, "Headless WebView Resolver returned stream: ${streamUrl != null}")
        }

        // 5. Download video stream if resolved
        if (streamUrl != null && isValidVideoStream(streamUrl)) {
            onProgress("Stream found! Downloading video file... 📥")
            Log.d(TAG, "Downloading video stream: $streamUrl")
            val savedVideo = downloadAndSaveVideo(streamUrl, shortcode)
            if (savedVideo != null) {
                Log.d(TAG, "Reel saved successfully: ${savedVideo.id} (${savedVideo.title})")
                return@withContext savedVideo
            } else {
                Log.e(TAG, "Failed to download and import stream from $streamUrl")
            }
        } else {
            Log.w(TAG, "Could not resolve video stream for shortcode: $shortcode")
        }

        return@withContext null
    }

    /**
     * Legacy callback method for non-coroutine callers.
     */
    fun downloadReel(instagramUrl: String, callback: DownloadCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val video = downloadReelSuspend(instagramUrl) { msg ->
                    mainHandler.post { callback.onProgress(msg) }
                }
                withContext(Dispatchers.Main) {
                    if (video != null) callback.onSuccess(video)
                    else callback.onFailure("Could not resolve or download reel from Instagram.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "downloadReel error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback.onFailure(e.message ?: "Download failed")
                }
            }
        }
    }

    /**
     * Strategy 1: Authenticated Media Info API
     */
    private fun fetchFromMediaInfoApi(mediaId: String, shortcode: String, cookies: String): String? {
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/media/$mediaId/info/",
            "https://i.instagram.com/api/v1/media/$mediaId/info/"
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Cookie", cookies)
                    setRequestProperty("X-IG-App-ID", IG_APP_ID)
                    setRequestProperty("X-ASBD-ID", "359341")
                    setRequestProperty("X-IG-WWW-Claim", "0")
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Referer", "https://www.instagram.com/reel/$shortcode/")
                }

                val code = conn.responseCode
                Log.d(TAG, "Media info API ($endpoint) HTTP $code")
                if (code in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val candidate = ReelJsonNormalizer.parse(jsonStr).items.firstOrNull()
                    val stream = candidate?.progressiveUrl
                    if (!stream.isNullOrBlank()) {
                        return cleanCdnVideoUrl(stream)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Media info endpoint failed ($endpoint): ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /**
     * Strategy 2: Direct web JSON endpoint
     */
    private fun fetchFromDirectEndpoint(shortcode: String, cookies: String): String? {
        val endpoints = listOf(
            "https://www.instagram.com/reel/$shortcode/?__a=1&__d=dis",
            "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis"
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Cookie", cookies)
                    setRequestProperty("X-IG-App-ID", IG_APP_ID)
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Referer", "https://www.instagram.com/reel/$shortcode/")
                }

                val code = conn.responseCode
                Log.d(TAG, "Direct /?__a=1 endpoint ($endpoint) HTTP $code")
                if (code in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val candidate = ReelJsonNormalizer.parse(jsonStr).items.firstOrNull()
                    val stream = candidate?.progressiveUrl
                    if (!stream.isNullOrBlank()) {
                        return cleanCdnVideoUrl(stream)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct endpoint failed ($endpoint): ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    /**
     * Strategy 3: Headless Offscreen Instagram WebView Stream Interceptor.
     * Uses Instagram's official web player to render and intercept the video CDN stream directly.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveViaHeadlessWebView(shortcode: String): String? =
        suspendCancellableCoroutine { continuation ->
            val isResolved = AtomicBoolean(false)
            var webView: WebView? = null

            val cleanup = Runnable {
                try {
                    webView?.stopLoading()
                    webView?.removeJavascriptInterface("LooplyResolver")
                    webView?.destroy()
                    webView = null
                } catch (_: Exception) {}
            }

            val timeoutRunnable = Runnable {
                if (isResolved.compareAndSet(false, true)) {
                    Log.w(TAG, "Headless WebView timed out for shortcode: $shortcode")
                    cleanup.run()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 15000)

            continuation.invokeOnCancellation {
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post(cleanup)
            }

            mainHandler.post {
                try {
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)

                    webView = WebView(appContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = USER_AGENT

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onVideoFound(streamUrl: String) {
                                if (isValidVideoStream(streamUrl) && isResolved.compareAndSet(false, true)) {
                                    Log.d(TAG, "Captured video stream via JS: $streamUrl")
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    mainHandler.post(cleanup)
                                    if (continuation.isActive) {
                                        continuation.resume(cleanCdnVideoUrl(streamUrl))
                                    }
                                }
                            }
                        }, "LooplyResolver")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                if (isValidVideoStream(reqUrl) && isResolved.compareAndSet(false, true)) {
                                    Log.d(TAG, "Intercepted video stream via WebViewClient: $reqUrl")
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    mainHandler.post(cleanup)
                                    if (continuation.isActive) {
                                        continuation.resume(cleanCdnVideoUrl(reqUrl))
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val js = """
                                    (function() {
                                        function check() {
                                            var v = document.querySelector('video');
                                            if (v && v.src && v.src.startsWith('http')) {
                                                window.LooplyResolver.onVideoFound(v.src);
                                                return true;
                                            }
                                            return false;
                                        }
                                        if (!check()) {
                                            var count = 0;
                                            var itv = setInterval(function() {
                                                count++;
                                                if (check() || count > 30) clearInterval(itv);
                                            }, 300);
                                        }
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }
                        }

                        loadUrl("https://www.instagram.com/reel/$shortcode/")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing headless WebView: ${e.message}", e)
                    if (isResolved.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cleanup.run()
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            }
        }

    /**
     * Downloads video stream bytes into a cache file and imports into Looply storage & database.
     */
    suspend fun downloadAndSaveVideo(streamUrl: String, shortcode: String? = null): Video? {
        val cleanedUrl = cleanCdnVideoUrl(streamUrl)
        val tempFile = File(appContext.cacheDir, "temp_ig_${System.currentTimeMillis()}.mp4")
        var currentUrl = cleanedUrl
        var redirects = 0

        while (redirects < 5) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Referer", "https://www.instagram.com/")
                    setRequestProperty("Accept", "*/*")
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 40000
                }

                val code = conn.responseCode
                Log.d(TAG, "Stream download ($currentUrl) HTTP $code")
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrEmpty()) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }

                if (code !in 200..299) {
                    Log.w(TAG, "Stream download failed HTTP $code from $currentUrl")
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

                Log.d(TAG, "Downloaded temp file size: ${tempFile.length()} bytes")
                if (tempFile.exists() && tempFile.length() > 50_000) {
                    val videoTitle = if (!shortcode.isNullOrEmpty()) "Reel • $shortcode.mp4" else "Reel_${System.currentTimeMillis()}.mp4"
                    val importedDetails = videoImport.importVideo(Uri.fromFile(tempFile), videoTitle)
                    val reelUrl = if (!shortcode.isNullOrEmpty()) "https://www.instagram.com/reel/$shortcode/" else ""
                    return repository.saveVideo(importedDetails, reelUrl = reelUrl)
                } else {
                    Log.w(TAG, "Downloaded file too small: ${tempFile.length()} bytes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed from $currentUrl: ${e.message}", e)
            } finally {
                conn?.disconnect()
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
            break
        }
        return null
    }
}
