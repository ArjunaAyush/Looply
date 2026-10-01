package com.arjunaayush.looply.features.instagram

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.VideoImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Automated background downloader for Instagram Reels.
 * Runs all network, extraction, and file operations on Dispatchers.IO.
 * Supports offscreen headless FastVideoSave automation when direct extraction is unavailable.
 */
class InstagramDownloader(
    private val context: Context,
    private val repository: VideoRepository,
    private val videoImport: VideoImport,
    private val rootContainer: ViewGroup? = null
) {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "LooplyFastVideoSave"

    interface DownloadCallback {
        fun onProgress(message: String)
        fun onSuccess(savedVideo: Video)
        fun onFailure(errorMessage: String)
    }

    companion object {
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
         * Cleans Meta CDN URLs by stripping byte-range chunk parameters.
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
            // Reject thumbnail/poster images
            if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".webp") ||
                lower.contains(".png") || lower.contains("dst-jpg") || lower.contains("dst-png") ||
                lower.contains("/photo") || lower.contains("thumbnail")
            ) {
                return false
            }

            // If it's a proxy link, verify the decoded target is a video
            if (lower.contains("dl.videodropper.app/?url=")) {
                val decodedTarget = try {
                    URLDecoder.decode(url.substringAfter("url="), "UTF-8").lowercase()
                } catch (_: Exception) {
                    lower
                }
                if (decodedTarget.contains(".jpg") || decodedTarget.contains(".jpeg") ||
                    decodedTarget.contains(".webp") || decodedTarget.contains(".png") ||
                    decodedTarget.contains("dst-jpg") || decodedTarget.contains("thumbnail")
                ) {
                    return false
                }
                return decodedTarget.contains(".mp4") || decodedTarget.contains("/o1/v/") ||
                        decodedTarget.contains("video") || decodedTarget.contains("/v/t")
            }

            return lower.contains(".mp4") || lower.contains("/o1/v/") || lower.contains("video_dashinit") ||
                    lower.contains("mime_type=video") || lower.contains("/v/t50")
        }
    }

    /**
     * Suspend-friendly download method for Coroutines and WorkManager.
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
     * Main entry point to download an Instagram Reel automatically.
     * Starts asynchronously without blocking the calling thread.
     */
    fun downloadReel(instagramUrl: String, callback: DownloadCallback) {
        val normalizedUrl = normalizeInstagramUrl(instagramUrl)
        val shortcode = extractShortcode(instagramUrl)

        callback.onProgress("Connecting to FastVideoSave... ⚡")

        val isCompleted = AtomicBoolean(false)

        CoroutineScope(Dispatchers.IO).launch {
            // Fast path: Try direct OpenGraph metadata extraction concurrently on Dispatchers.IO
            if (shortcode != null) {
                val directStreamUrl = tryExtractFromHtml(shortcode)
                if (directStreamUrl != null && isValidVideoStream(directStreamUrl) && isCompleted.compareAndSet(false, true)) {
                    withContext(Dispatchers.Main) { callback.onProgress("Stream found! Downloading reel...") }
                    val video = downloadAndSaveVideo(directStreamUrl, shortcode)
                    withContext(Dispatchers.Main) {
                        if (video != null) {
                            callback.onSuccess(video)
                        } else {
                            callback.onFailure("Failed to save downloaded reel.")
                        }
                    }
                    return@launch
                }
            }

            // Primary engine: Background FastVideoSave automated scraper
            withContext(Dispatchers.Main) {
                if (!isCompleted.get()) {
                    startFastVideoSaveAutomation(normalizedUrl, shortcode, isCompleted, callback)
                }
            }
        }
    }

    /**
     * Background WebView automation for https://fastvideosave.net/.
     * Runs offscreen on the Main looper without blocking UI rendering or touches.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun startFastVideoSaveAutomation(
        normalizedUrl: String,
        shortcode: String?,
        isCompleted: AtomicBoolean,
        callback: DownloadCallback
    ) {
        var webView: WebView? = null

        val timeoutRunnable = Runnable {
            if (isCompleted.compareAndSet(false, true)) {
                webView?.let { destroyWebView(it) }
                callback.onFailure("FastVideoSave took too long to resolve the reel. Please check your internet connection or verify the reel is public.")
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 25000)

        fun triggerSuccess(streamUrl: String) {
            if (!isValidVideoStream(streamUrl)) {
                Log.d(TAG, "Ignoring non-video URL: $streamUrl")
                return
            }
            if (isCompleted.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post {
                    webView?.let { destroyWebView(it) }
                    callback.onProgress("Video stream captured! Downloading reel...")
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val savedVideo = downloadAndSaveVideo(streamUrl, shortcode)
                    withContext(Dispatchers.Main) {
                        if (savedVideo != null) {
                            callback.onSuccess(savedVideo)
                        } else {
                            callback.onFailure("Could not save video stream to offline storage.")
                        }
                    }
                }
            }
        }

        try {
            // Can use rootContainer if attached, or headless unattached WebView with applicationContext
            webView = WebView(rootContainer?.context ?: appContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onVideoFound(streamUrl: String) {
                        Log.d(TAG, "FastVideoSave JS interface received stream: $streamUrl")
                        triggerSuccess(streamUrl)
                    }

                    @JavascriptInterface
                    fun onStatus(status: String) {
                        mainHandler.post {
                            if (!isCompleted.get()) {
                                callback.onProgress(status)
                            }
                        }
                    }
                }, "LooplyFastVideoSave")

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: ""
                        val lower = reqUrl.lowercase()

                        // Block ad networks for fast resolution
                        if (lower.contains("googlesyndication") || lower.contains("googleads") ||
                            lower.contains("doubleclick") || lower.contains("adservice") ||
                            lower.contains("pagead") || lower.contains("adnxs") ||
                            lower.contains("taboola") || lower.contains("outbrain")
                        ) {
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }

                        // Intercept direct video stream requests
                        if (isValidVideoStream(reqUrl)) {
                            Log.d(TAG, "Intercepted valid video request: $reqUrl")
                            triggerSuccess(reqUrl)
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        callback.onProgress("Resolving Reel on FastVideoSave... ⏳")

                        val js = """
                            (function() {
                                if (window._looplyInjected) return;
                                window._looplyInjected = true;

                                var targetUrl = "$normalizedUrl";

                                try {
                                    if (!navigator.clipboard) {
                                        navigator.clipboard = {};
                                    }
                                    navigator.clipboard.readText = function() {
                                        return Promise.resolve(targetUrl);
                                    };
                                } catch(e) {}

                                try {
                                    var origFetch = window.fetch;
                                    window.fetch = function(resource, init) {
                                        return origFetch.apply(this, arguments).then(function(res) {
                                            try {
                                                var u = typeof resource === 'string' ? resource : (resource && resource.url ? resource.url : '');
                                                if (u.indexOf('videodropper.app') !== -1 || u.indexOf('allinone') !== -1) {
                                                    var clone = res.clone();
                                                    clone.json().then(function(data) {
                                                        if (data) {
                                                            var stream = null;
                                                            if (data.video) {
                                                                stream = Array.isArray(data.video) ? (data.video[0] && (data.video[0].video || data.video[0])) : data.video;
                                                            } else if (data.media) {
                                                                stream = Array.isArray(data.media) ? (data.media[0] && (data.media[0].video || data.media[0])) : data.media;
                                                            } else if (data.original) {
                                                                stream = data.original;
                                                            }
                                                            if (typeof stream === 'string' && stream.indexOf('http') === 0) {
                                                                var ls = stream.toLowerCase();
                                                                if (ls.indexOf('.jpg') === -1 && ls.indexOf('.png') === -1 && ls.indexOf('.webp') === -1) {
                                                                    window.LooplyFastVideoSave.onVideoFound(stream);
                                                                }
                                                            }
                                                        }
                                                    }).catch(function(){});
                                                }
                                            } catch(e) {}
                                            return res;
                                        });
                                    };
                                } catch(e) {}

                                function checkAndTrigger() {
                                    var sources = document.querySelectorAll('video source, video');
                                    for (var i = 0; i < sources.length; i++) {
                                        var src = sources[i].src || sources[i].getAttribute('src');
                                        if (src && src.indexOf('http') === 0 && src.indexOf('blob:') !== 0) {
                                            var lsrc = src.toLowerCase();
                                            if (lsrc.indexOf('.jpg') === -1 && lsrc.indexOf('.png') === -1 && lsrc.indexOf('.webp') === -1) {
                                                window.LooplyFastVideoSave.onVideoFound(src);
                                                return true;
                                            }
                                        }
                                    }

                                    var links = document.querySelectorAll('a');
                                    for (var j = 0; j < links.length; j++) {
                                        var href = links[j].href || links[j].getAttribute('href');
                                        var text = (links[j].innerText || '').toLowerCase();
                                        var aria = (links[j].getAttribute('aria-label') || '').toLowerCase();
                                        if (href && href.indexOf('http') === 0) {
                                            var lhref = href.toLowerCase();
                                            if (lhref.indexOf('.jpg') === -1 && lhref.indexOf('.png') === -1 &&
                                                lhref.indexOf('.webp') === -1 && lhref.indexOf('dst-jpg') === -1 &&
                                                lhref.indexOf('thumbnail') === -1) {
                                                if (text.indexOf('download video') !== -1 || aria.indexOf('download video') !== -1) {
                                                    window.LooplyFastVideoSave.onVideoFound(href);
                                                    return true;
                                                }
                                                if (lhref.indexOf('dl.videodropper.app') !== -1 && (lhref.indexOf('.mp4') !== -1 || lhref.indexOf('/o1/v/') !== -1)) {
                                                    window.LooplyFastVideoSave.onVideoFound(href);
                                                    return true;
                                                }
                                            }
                                        }
                                    }

                                    var input = document.querySelector('input[name="url"], input[type="url"], input[type="text"]');
                                    if (input) {
                                        if (input.value !== targetUrl) {
                                            input.value = targetUrl;
                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                            input.dispatchEvent(new Event('change', { bubbles: true }));
                                        }
                                    }

                                    var pasteBtn = document.querySelector('#paste-btn');
                                    if (pasteBtn && !window._looplyClicked) {
                                        window._looplyClicked = true;
                                        pasteBtn.click();
                                        window.LooplyFastVideoSave.onStatus("Resolving stream on FastVideoSave... ⚡");
                                    }

                                    return false;
                                }

                                checkAndTrigger();
                                var attempts = 0;
                                var interval = setInterval(function() {
                                    attempts++;
                                    if (checkAndTrigger() || attempts > 50) {
                                        clearInterval(interval);
                                    }
                                }, 350);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }
            }

            if (rootContainer != null) {
                webView.alpha = 0.01f
                webView.translationY = 6000f
                val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 400)
                rootContainer.addView(webView, layoutParams)
            }
            webView.loadUrl("https://fastvideosave.net/")
        } catch (e: Exception) {
            Log.e(TAG, "FastVideoSave automation failed: ${e.message}", e)
            if (isCompleted.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                callback.onFailure("Could not initialize downloader engine: ${e.message}")
            }
        }
    }

    private fun destroyWebView(webView: WebView) {
        try {
            rootContainer?.removeView(webView)
            webView.stopLoading()
            webView.removeJavascriptInterface("LooplyFastVideoSave")
            webView.destroy()
        } catch (_: Exception) {}
    }

    /**
     * Downloads video stream bytes into a cache file and imports into Looply storage & database.
     * All network and file I/O operations execute strictly on Dispatchers.IO.
     */
    fun downloadAndSaveVideo(streamUrl: String, shortcode: String? = null): Video? {
        val candidates = mutableListOf<String>()

        candidates.add(cleanCdnVideoUrl(streamUrl))

        if (streamUrl.contains("dl.videodropper.app/?url=")) {
            try {
                val encoded = streamUrl.substringAfter("url=")
                val decoded = URLDecoder.decode(encoded, "UTF-8")
                if (decoded.startsWith("http") && isValidVideoStream(decoded)) {
                    candidates.add(cleanCdnVideoUrl(decoded))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not decode videodropper url param: ${e.message}")
            }
        }

        for (candidateUrl in candidates) {
            val video = tryDownloadFromUrl(candidateUrl, shortcode)
            if (video != null) return video
        }

        return null
    }

    private fun tryDownloadFromUrl(candidateUrl: String, shortcode: String? = null): Video? {
        val tempFile = File(appContext.cacheDir, "temp_ig_${System.currentTimeMillis()}.mp4")
        var currentUrl = candidateUrl
        var redirects = 0

        while (redirects < 5) {
            try {
                val url = URL(currentUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                conn.setRequestProperty("Referer", "https://fastvideosave.net/")
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

                if (tempFile.exists() && tempFile.length() > 80_000) {
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

    /**
     * Attempts fast HTML metadata extraction from public Instagram embed or page on Dispatchers.IO.
     */
    private fun tryExtractFromHtml(shortcode: String): String? {
        val endpoints = listOf(
            "https://www.instagram.com/reel/$shortcode/embed/",
            "https://www.instagram.com/p/$shortcode/embed/",
            "https://www.instagram.com/reel/$shortcode/"
        )

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode in 200..299) {
                    val html = conn.inputStream.bufferedReader().use { it.readText() }

                    val videoUrlRegex = Regex(""""video_url"\s*:\s*"([^"]+)"""")
                    val videoUrlMatch = videoUrlRegex.find(html)
                    if (videoUrlMatch != null) {
                        return cleanCdnVideoUrl(videoUrlMatch.groupValues[1])
                    }

                    val ogRegex = Regex("""<meta\s+property=["']og:video(?::secure_url)?["']\s+content=["']([^"']+)["']""")
                    val ogMatch = ogRegex.find(html)
                    if (ogMatch != null) {
                        return cleanCdnVideoUrl(ogMatch.groupValues[1])
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct HTML extraction unavailable: ${e.message}")
            }
        }
        return null
    }
}
