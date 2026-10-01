package com.arjunaayush.looply.features.instagram

import android.annotation.SuppressLint
import android.app.Activity
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Completely automated background downloader for Instagram Reels.
 * Uses https://fastvideosave.net/ in the background (offscreen WebView engine)
 * to resolve and download the reel stream without requiring any user clicks.
 */
class InstagramDownloader(
    private val activity: Activity,
    private val repository: VideoRepository,
    private val videoImport: VideoImport,
    private val rootContainer: ViewGroup
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "LooplyFastVideoSave"

    interface DownloadCallback {
        fun onProgress(message: String)
        fun onSuccess(savedVideo: Video)
        fun onFailure(errorMessage: String)
    }

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

    /**
     * Main entry point to download an Instagram Reel automatically.
     */
    fun downloadReel(instagramUrl: String, callback: DownloadCallback) {
        val normalizedUrl = normalizeInstagramUrl(instagramUrl)
        val shortcode = extractShortcode(instagramUrl)

        callback.onProgress("Connecting to FastVideoSave... ⚡")

        val isCompleted = AtomicBoolean(false)

        // Fast path: Try direct OpenGraph metadata extraction concurrently
        if (shortcode != null) {
            Thread {
                val directStreamUrl = tryExtractFromHtml(shortcode)
                if (directStreamUrl != null && isValidVideoStream(directStreamUrl) && isCompleted.compareAndSet(false, true)) {
                    mainHandler.post { callback.onProgress("Stream found! Downloading reel...") }
                    val video = downloadAndSaveVideo(directStreamUrl, shortcode)
                    mainHandler.post {
                        if (video != null) {
                            callback.onSuccess(video)
                        } else {
                            callback.onFailure("Failed to save downloaded reel.")
                        }
                    }
                }
            }.start()
        }

        // Primary engine: Background FastVideoSave automated scraper
        mainHandler.post {
            if (!isCompleted.get()) {
                startFastVideoSaveAutomation(normalizedUrl, shortcode, isCompleted, callback)
            }
        }
    }

    /**
     * Background WebView automation for https://fastvideosave.net/.
     * Runs offscreen, enters the Instagram URL, blocks ads, intercepts the video stream,
     * and downloads the reel without any user interaction.
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
                Thread {
                    val savedVideo = downloadAndSaveVideo(streamUrl, shortcode)
                    mainHandler.post {
                        if (savedVideo != null) {
                            callback.onSuccess(savedVideo)
                        } else {
                            callback.onFailure("Could not save video stream to offline storage.")
                        }
                    }
                }.start()
            }
        }

        try {
            webView = WebView(activity).apply {
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

                        // 1. Block all ad networks to ensure fastest performance and no ad interstitials
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

                        // 2. Intercept video stream URL if requested
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

                                // 1. Mock clipboard readText so FastVideoSave's paste handler retrieves our reel link
                                try {
                                    if (!navigator.clipboard) {
                                        navigator.clipboard = {};
                                    }
                                    navigator.clipboard.readText = function() {
                                        return Promise.resolve(targetUrl);
                                    };
                                } catch(e) {}

                                // 2. Intercept window.fetch to capture videodropper API response directly
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

                                // 3. Automation loop to populate input, trigger paste button, and detect download elements
                                function checkAndTrigger() {
                                    // Check video elements
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

                                    // Check download links
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

                                    // Populate input field
                                    var input = document.querySelector('input[name="url"], input[type="url"], input[type="text"]');
                                    if (input) {
                                        if (input.value !== targetUrl) {
                                            input.value = targetUrl;
                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                            input.dispatchEvent(new Event('change', { bubbles: true }));
                                        }
                                    }

                                    // Trigger Paste button
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

            // Attach offscreen so Chromium measures, executes scripts, and performs networking
            webView.alpha = 0.01f
            webView.translationY = 6000f
            val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 400)
            rootContainer.addView(webView, layoutParams)
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
            rootContainer.removeView(webView)
            webView.stopLoading()
            webView.removeJavascriptInterface("LooplyFastVideoSave")
            webView.destroy()
        } catch (_: Exception) {}
    }

    /**
     * Downloads video stream bytes into a cache file and imports into Looply storage & database.
     * Supports both dl.videodropper.app proxy URLs and direct Instagram CDN URLs with automatic retry.
     */
    fun downloadAndSaveVideo(streamUrl: String, shortcode: String? = null): Video? {
        val candidates = mutableListOf<String>()

        // Add the provided stream URL first
        candidates.add(cleanCdnVideoUrl(streamUrl))

        // If it's a dl.videodropper.app URL containing an encoded URL query param, extract the direct target
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
        val tempFile = File(activity.cacheDir, "temp_ig_${System.currentTimeMillis()}.mp4")
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
     * Attempts fast HTML metadata extraction from public Instagram embed or page.
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
