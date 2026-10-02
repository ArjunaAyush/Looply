package com.arjunaayush.looply.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arjunaayush.looply.BuildConfig
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyCategorizedCard
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyOutlinedButton
import com.arjunaayush.looply.core.designsystem.LinkerlySwitch
import com.arjunaayush.looply.core.designsystem.theme.LooplyPink
import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.feature.settings.components.InstagramLoginDialog
import com.arjunaayush.looply.features.download.BlockReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLoginDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Instagram Headless Session Card
        LinkerlyCategorizedCard(title = "Instagram Connection") {
            Column(modifier = Modifier.padding(16.dp)) {
                if (uiState.isInstagramLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connected as @${uiState.instagramUsername}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ready to auto-fetch reels from your algorithm & feed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinkerlyOutlinedButton(
                            onClick = { viewModel.logoutInstagram() }
                        ) {
                            Text("Disconnect")
                        }
                    }
                } else {
                    Text(
                        text = "Connect your account to allow Looply to fetch and download reels automatically from your personal feed and algorithm.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinkerlyButton(
                        onClick = { showLoginDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinkerlyIcon(
                            imageVector = LinkerlyIcons.Profile.Account,
                            contentDescription = null,
                            size = 18.dp
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Connect Instagram Account")
                    }
                }
            }
        }

        // 2. Account Safety / Feed Ingestion Status Card
        val blockReason = uiState.ingestionBlockReason
        if (blockReason != null) {
            LinkerlyCategorizedCard(title = "Account Safety & Status") {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (blockReason) {
                        is BlockReason.CoolingDown -> {
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(blockReason.untilMillis))
                            Text(
                                text = "Paused to protect your account until $timeStr",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        is BlockReason.DailyBudgetUsed -> {
                            Text(
                                text = "Daily download limit reached. Resumes automatically tomorrow.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        is BlockReason.NeedsUserVerification -> {
                            Text(
                                text = "Instagram wants to confirm it's you. Open Instagram, then tap Resume.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinkerlyButton(
                                onClick = { viewModel.clearVerificationBlock() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Resume")
                            }
                        }
                        is BlockReason.NeedsLogin -> {
                            Text(
                                text = "Instagram session has expired. Please reconnect your account.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinkerlyButton(
                                onClick = { showLoginDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Log In")
                            }
                        }
                        is BlockReason.DisabledRemotely -> {
                            Text(
                                text = "Feed downloading is temporarily unavailable via remote configuration.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Instant Batch Download Slider Card (Direct flavor only)
        if (uiState.feedIngestionEnabled) {
            LinkerlyCategorizedCard(title = "Download Reels Now") {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!uiState.isInstagramLoggedIn) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Log in with your Instagram account above to enable instant reel downloads.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Text(
                        text = "Download ${uiState.instantBatchSizeMb} MB of reels now from your feed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.isInstagramLoggedIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                    Slider(
                        value = uiState.instantBatchSizeMb.toFloat(),
                        onValueChange = { viewModel.setInstantBatchSizeMb(it.roundToInt()) },
                        valueRange = 100f..500f,
                        steps = 3,
                        enabled = uiState.isInstagramLoggedIn,
                        colors = SliderDefaults.colors(
                            thumbColor = LooplyPink,
                            activeTrackColor = LooplyPink,
                            disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "100 MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isInstagramLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                        Text(
                            "300 MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isInstagramLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                        Text(
                            "500 MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isInstagramLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinkerlyButton(
                        onClick = { viewModel.downloadBatchNow(uiState.instantBatchSizeMb) },
                        enabled = uiState.isInstagramLoggedIn && !uiState.isDownloadingBatch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isDownloadingBatch) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Fetching Reels...")
                        } else {
                            Text("Download ${uiState.instantBatchSizeMb} MB of Reels Now")
                        }
                    }

                    if (uiState.isDownloadingBatch || uiState.batchDownloadProgress.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.isDownloadingBatch) {
                            LinearProgressIndicator(
                                progress = { uiState.batchDownloadProgressFraction },
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            text = uiState.batchDownloadProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.isDownloadingBatch) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinkerlyOutlinedButton(
                                onClick = { viewModel.stopDownloading() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.Close,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 18.dp
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Stop Download",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }

            // 4. Background WiFi Auto-Download & Cache Limit Card (Direct flavor only)
            LinkerlyCategorizedCard(title = "Auto-Download & Cache Management") {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!uiState.isInstagramLoggedIn) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Auto-download features are disabled. Please log in with Instagram to allow background fetching on WiFi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Download on Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Silently fetch fresh reels when connected to unmetered Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinkerlySwitch(
                            checked = uiState.autoDownloadOnWifi,
                            onCheckedChange = { viewModel.setAutoDownloadOnWifi(it) },
                            enabled = uiState.isInstagramLoggedIn
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Cache Limit", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Maximum storage Looply uses before pruning old videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = uiState.cacheLimitIndex.toFloat(),
                        onValueChange = { viewModel.setCacheLimitIndex(it.roundToInt()) },
                        valueRange = 0f..3f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = LooplyPink,
                            activeTrackColor = LooplyPink
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PreferencesManager.CACHE_LIMIT_OPTIONS.forEachIndexed { index, option ->
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == uiState.cacheLimitIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Delete Watched Reels", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Automatically remove watched loops after 24 hours to save storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinkerlySwitch(
                            checked = uiState.autoDeleteWatchedAfter24h,
                            onCheckedChange = { viewModel.setAutoDeleteWatchedAfter24h(it) }
                        )
                    }

                    if (uiState.isAutoDownloading || uiState.autoDownloadProgress.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.isAutoDownloading) {
                            LinearProgressIndicator(
                                progress = { uiState.autoDownloadProgressFraction },
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Text(
                            text = uiState.autoDownloadProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.isAutoDownloading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinkerlyOutlinedButton(
                                onClick = { viewModel.stopDownloading() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.Close,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 18.dp
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Stop Auto-Download",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Playback Defaults Card
        LinkerlyCategorizedCard(title = "Playback Preferences") {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Infinite Loop by Default", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Reels automatically loop continuously",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinkerlySwitch(
                        checked = uiState.infiniteLoopDefault,
                        onCheckedChange = { viewModel.setInfiniteLoopDefault(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tactile Haptics", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Vibrational feedback on gestures and actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinkerlySwitch(
                        checked = uiState.hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) }
                    )
                }
            }
        }

        // 6. Debug Tools (Debug builds only)
        if (BuildConfig.DEBUG && uiState.feedIngestionEnabled) {
            LinkerlyCategorizedCard(title = "Debug Tools") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Export latest intercepted feed payload for diagnosis and fixture generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkerlyOutlinedButton(
                        onClick = {
                            val file = viewModel.getLatestDebugCaptureFile()
                            if (file != null && file.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Capture Dump"))
                            } else {
                                Toast.makeText(context, "No capture dump available yet", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Last Capture")
                    }
                }
            }
        }

        // 7. About
        LinkerlyCategorizedCard(title = "About") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Looply v1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Offline-first Instagram Reel player and creator organizer with calm, ad-free serenity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }

    if (showLoginDialog) {
        InstagramLoginDialog(
            onLoginSuccess = { username, cookies ->
                viewModel.onInstagramLoginSuccess(username, cookies)
                showLoginDialog = false
            },
            onDismiss = { showLoginDialog = false }
        )
    }
}
