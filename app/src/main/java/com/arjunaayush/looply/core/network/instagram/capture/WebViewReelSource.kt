package com.arjunaayush.looply.core.network.instagram.capture

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View.MeasureSpec
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.arjunaayush.looply.BuildConfig
import com.arjunaayush.looply.core.network.instagram.config.IngestionConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class CaptureException(val code: String, message: String? = null) : Exception("$code ${message.orEmpty()}")

class WebViewReelSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val events = Channel<BridgeEvent>(Channel.UNLIMITED)
    private val backlog = ArrayDeque<BridgeEvent>()
    private var webView: WebView? = null
    var hasTemplate = false
        private set

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun open(config: IngestionConfig) = withContext(Dispatchers.Main) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            throw CaptureException("WEBVIEW_UNSUPPORTED")
        }

        val script = context.assets.open(SCRIPT_ASSET).bufferedReader().use { it.readText() }
            .replace("/*__CONFIG__*/{}", config.toJsJson())
        val origins = setOf(INSTAGRAM_ORIGIN)

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true   // no video bytes wasted by autoplay
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            measure(
                MeasureSpec.makeMeasureSpec(VIEWPORT_W, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(VIEWPORT_H, MeasureSpec.EXACTLY),
            )
            layout(0, 0, VIEWPORT_W, VIEWPORT_H)

            WebViewCompat.addWebMessageListener(this, BRIDGE_NAME, origins) { _, message, _, isMainFrame, _ ->
                if (!isMainFrame) return@addWebMessageListener
                val parsed = message.data?.let(BridgeEvent::parse)
                if (parsed != null) {
                    if (BuildConfig.DEBUG && parsed is BridgeEvent.Payload) {
                        recordDebugCapture(parsed.body)
                    }
                    events.trySend(parsed)
                }
            }

            WebViewCompat.addDocumentStartJavaScript(this, script, origins)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    events.trySend(BridgeEvent.Navigated(url))
                }
            }
            onResume()
            resumeTimers()
            loadUrl(config.startUrl)
        }
    }

    /** Collects the page's own first payloads. Returns early once a template and at least one payload exist. */
    suspend fun awaitInitial(timeoutMs: Long): List<BridgeEvent.Payload> {
        val payloads = mutableListOf<BridgeEvent.Payload>()
        withTimeoutOrNull(timeoutMs) {
            for (e in events) {
                when (e) {
                    is BridgeEvent.Payload -> payloads += e
                    is BridgeEvent.Template -> hasTemplate = true
                    is BridgeEvent.Navigated -> failIfLoggedOut(e.url)
                    else -> Unit
                }
                if (hasTemplate && payloads.isNotEmpty()) break
            }
        }
        return payloads
    }

    suspend fun requestPage(cursor: String, timeoutMs: Long): BridgeEvent.Page {
        val reqId = UUID.randomUUID().toString()
        withContext(Dispatchers.Main) {
            webView?.evaluateJavascript(
                "window.__looply && window.__looply.next(${JSONObject.quote(cursor)}, ${JSONObject.quote(reqId)})",
                null,
            )
        }
        return withTimeoutOrNull(timeoutMs) {
            for (e in events) {
                when {
                    e is BridgeEvent.Page && e.reqId == reqId -> {
                        if (BuildConfig.DEBUG && e.body.isNotBlank()) {
                            recordDebugCapture(e.body)
                        }
                        return@withTimeoutOrNull e
                    }
                    e is BridgeEvent.Failure && e.reqId == reqId -> throw CaptureException(e.code, e.message)
                    e is BridgeEvent.Navigated -> failIfLoggedOut(e.url)
                    else -> backlog.addLast(e)   // passive payloads keep arriving; don't lose them
                }
            }
            null
        } ?: throw CaptureException("PAGE_TIMEOUT")
    }

    fun drainBacklogPayloads(): List<BridgeEvent.Payload> =
        backlog.filterIsInstance<BridgeEvent.Payload>().also { backlog.clear() }

    suspend fun close() = withContext(NonCancellable + Dispatchers.Main) {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        events.close()
    }

    private fun failIfLoggedOut(url: String) {
        when {
            "/challenge" in url || "/checkpoint" in url -> throw CaptureException("CHALLENGE")
            "/accounts/login" in url -> throw CaptureException("SESSION_EXPIRED")
        }
    }

    private fun recordDebugCapture(body: String) {
        try {
            val debugDir = File(context.filesDir, "debug/captures").apply { if (!exists()) mkdirs() }
            val captureFile = File(debugDir, "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.json")
            captureFile.writeText(body)

            // Prune to keep only newest 20
            val all = debugDir.listFiles() ?: return
            if (all.size > 20) {
                all.sortedBy { it.lastModified() }
                    .take(all.size - 20)
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.d("WebViewReelSource", "Failed to save debug capture: ${e.message}")
        }
    }

    private companion object {
        const val INSTAGRAM_ORIGIN = "https://www.instagram.com"
        const val BRIDGE_NAME = "LooplyBridge"
        const val SCRIPT_ASSET = "looply_capture.js"
        const val VIEWPORT_W = 1080
        const val VIEWPORT_H = 1920
    }
}
