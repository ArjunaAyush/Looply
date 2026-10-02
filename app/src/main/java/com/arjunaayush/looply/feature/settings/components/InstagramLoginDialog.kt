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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIconButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyOutlinedButton
import com.arjunaayush.looply.core.designsystem.LinkerlyTextButton
import kotlinx.coroutines.delay

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginDialog(
    onLoginSuccess: (username: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Initializing Instagram login...") }
    var canGoBack by remember { mutableStateOf(false) }
    var useDesktopMode by remember { mutableStateOf(true) }
    var showAdvancedImport by remember { mutableStateOf(false) }
    var sessionCookieInput by remember { mutableStateOf("") }
    var importErrorMessage by remember { mutableStateOf("") }

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
            onLoginSuccess(detectedUser)
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Connect Instagram",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (useDesktopMode) "Desktop Web Engine" else "Mobile Web Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canGoBack) {
                            LinkerlyIconButton(onClick = { webViewInstance?.goBack() }) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.ArrowBack,
                                    contentDescription = "Back",
                                    size = 18.dp
                                )
                            }
                        }
                        LinkerlyIconButton(onClick = {
                            webViewInstance?.let { webView ->
                                useDesktopMode = !useDesktopMode
                                webView.settings.userAgentString =
                                    if (useDesktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                webView.reload()
                                statusMessage = "Switching browser engine..."
                            }
                        }) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Tune,
                                contentDescription = "Toggle Engine",
                                size = 18.dp
                            )
                        }
                        LinkerlyIconButton(onClick = {
                            webViewInstance?.reload()
                            statusMessage = "Refreshing page..."
                        }) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Buttons.Refresh,
                                contentDescription = "Refresh",
                                size = 18.dp
                            )
                        }
                        LinkerlyIconButton(onClick = onDismiss) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Close,
                                contentDescription = "Close",
                                size = 20.dp
                            )
                        }
                    }
                }

                // Advanced Session Cookie Import Accordion
                AnimatedVisibility(visible = showAdvancedImport) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Advanced Session Cookie Import",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Paste your Instagram sessionid or full cookie header string to link instantly without using the web form:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sessionCookieInput,
                            onValueChange = {
                                sessionCookieInput = it
                                importErrorMessage = ""
                            },
                            label = { Text("sessionid or Cookie String") },
                            placeholder = { Text("e.g. 6283910283%3AAbCdEf...") },
                            singleLine = false,
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (importErrorMessage.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = importErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            LinkerlyOutlinedButton(onClick = { showAdvancedImport = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            LinkerlyButton(
                                onClick = {
                                    val input = sessionCookieInput.trim()
                                    if (input.isBlank()) {
                                        importErrorMessage = "Please paste a sessionid or cookie string."
                                        return@LinkerlyButton
                                    }
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)

                                    var sessionIdVal = ""
                                    var dsUserIdVal = ""

                                    if (input.contains(";")) {
                                        // Header string format
                                        val parts = input.split(";")
                                        for (p in parts) {
                                            val trimmed = p.trim()
                                            if (trimmed.startsWith("sessionid=")) {
                                                sessionIdVal = trimmed.substringAfter("sessionid=")
                                            }
                                            if (trimmed.startsWith("ds_user_id=")) {
                                                dsUserIdVal = trimmed.substringAfter("ds_user_id=")
                                            }
                                        }
                                    } else if (input.startsWith("sessionid=")) {
                                        sessionIdVal = input.substringAfter("sessionid=")
                                    } else {
                                        sessionIdVal = input
                                    }

                                    if (sessionIdVal.isBlank()) {
                                        importErrorMessage = "No valid sessionid found in input."
                                        return@LinkerlyButton
                                    }

                                    cookieManager.setCookie(
                                        "https://www.instagram.com",
                                        "sessionid=$sessionIdVal; Domain=.instagram.com; Path=/; Secure; HttpOnly"
                                    )
                                    if (dsUserIdVal.isNotBlank()) {
                                        cookieManager.setCookie(
                                            "https://www.instagram.com",
                                            "ds_user_id=$dsUserIdVal; Domain=.instagram.com; Path=/; Secure"
                                        )
                                    }
                                    cookieManager.flush()
                                    val user = if (dsUserIdVal.isNotBlank()) dsUserIdVal else "user"
                                    onLoginSuccess(user)
                                }
                            ) {
                                Text("Apply Session")
                            }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinkerlyTextButton(onClick = { showAdvancedImport = !showAdvancedImport }) {
                        Text(
                            text = if (showAdvancedImport) "Hide Advanced" else "Paste Session Cookie",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        LinkerlyOutlinedButton(
                            onClick = {
                                val detected = checkAndNotifyCookies(CookieManager.getInstance())
                                if (!detected) {
                                    statusMessage = "No active session detected yet. Please submit login."
                                }
                            }
                        ) {
                            Text("I'm Logged In")
                        }
                    }
                }
            }
        }
    }
}
