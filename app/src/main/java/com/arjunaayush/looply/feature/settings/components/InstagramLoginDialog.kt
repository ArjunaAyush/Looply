package com.arjunaayush.looply.feature.settings.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIconButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import kotlinx.coroutines.delay

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginDialog(
    onLoginSuccess: (username: String, cookies: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Initializing Instagram login...") }
    var canGoBack by remember { mutableStateOf(false) }
    var useDesktopMode by remember { mutableStateOf(true) }

    fun checkAndNotifyCookies(cookieManager: CookieManager): Boolean {
        val c1 = cookieManager.getCookie("https://www.instagram.com") ?: ""
        val c2 = cookieManager.getCookie("https://instagram.com") ?: ""
        val c3 = cookieManager.getCookie("https://m.instagram.com") ?: ""
        val c4 = cookieManager.getCookie(".instagram.com") ?: ""
        val allCookies = "$c1; $c2; $c3; $c4"

        if (allCookies.contains("sessionid")) {
            var detectedUser = ""
            val parts = allCookies.split("; ")
            for (part in parts) {
                if (part.startsWith("ds_user_id=")) {
                    detectedUser = part.substringAfter("ds_user_id=").trim()
                    break
                }
            }
            if (detectedUser.isBlank()) {
                detectedUser = "user"
            }
            statusMessage = "Authenticated as @$detectedUser"
            onLoginSuccess(detectedUser, allCookies)
            return true
        }
        return false
    }

    // Active Cookie Polling: Checks CookieManager every 500ms
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        while (true) {
            delay(500)
            if (checkAndNotifyCookies(cookieManager)) {
                break
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Connect Instagram",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (useDesktopMode) "Desktop Web Engine" else "Mobile Web Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (canGoBack) {
                            LinkerlyIconButton(
                                onClick = { webViewInstance?.goBack() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.ArrowBack,
                                    contentDescription = "Back",
                                    size = 18.dp
                                )
                            }
                        }
                        LinkerlyIconButton(
                            onClick = {
                                webViewInstance?.let { webView ->
                                    useDesktopMode = !useDesktopMode
                                    webView.settings.userAgentString =
                                        if (useDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                    webView.reload()
                                    statusMessage = "Switching browser engine..."
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            LinkerlyIcon(
                                imageVector = if (useDesktopMode) LinkerlyIcons.FolderOverlays.Laptop else LinkerlyIcons.FolderOverlays.Phone,
                                contentDescription = if (useDesktopMode) "Desktop Web Engine (Click for Mobile)" else "Mobile Web Engine (Click for Desktop)",
                                size = 18.dp
                            )
                        }
                        LinkerlyIconButton(
                            onClick = {
                                webViewInstance?.reload()
                                statusMessage = "Refreshing page..."
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Buttons.Refresh,
                                contentDescription = "Refresh",
                                size = 18.dp
                            )
                        }
                        LinkerlyIconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Close,
                                contentDescription = "Close",
                                size = 18.dp
                            )
                        }
                    }
                }

                // Loading progress indicator
                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { webProgress },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // WebView Container
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(false)
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    allowFileAccess = false
                                    allowContentAccess = true
                                    // Use Desktop Chrome UA to prevent Instagram's mobile web React SPA blank-page failure
                                    userAgentString = DESKTOP_USER_AGENT
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        webProgress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            isPageLoading = false
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                        statusMessage = "Loading Instagram..."
                                        canGoBack = view?.canGoBack() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        statusMessage = "Ready"
                                        canGoBack = view?.canGoBack() == true

                                        // Evaluate page cookies directly
                                        view?.evaluateJavascript("document.cookie") { _ ->
                                            checkAndNotifyCookies(CookieManager.getInstance())
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        // Intercept external app schemes so webview doesn't fail
                                        if (url.startsWith("intent://") || url.startsWith("instagram://") || url.startsWith("market://")) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            statusMessage = "Connection notice: ${error?.description ?: "Network issue"}"
                                        }
                                    }
                                }

                                loadUrl("https://www.instagram.com/accounts/login/")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isPageLoading && webProgress < 0.2f) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Bottom Action Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Status text and subtle spinner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPageLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Action button
                    LinkerlyButton(
                        onClick = {
                            val detected = checkAndNotifyCookies(CookieManager.getInstance())
                            if (!detected) {
                                statusMessage = "No active session detected yet. Please submit login."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "I'm Logged In",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
