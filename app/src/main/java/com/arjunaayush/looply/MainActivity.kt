package com.arjunaayush.looply

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIconButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyNavigationBar
import com.arjunaayush.looply.core.designsystem.LinkerlyNavigationBarItem
import com.arjunaayush.looply.core.designsystem.LinkerlyTopBar
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme
import com.arjunaayush.looply.core.util.HapticEffectType
import com.arjunaayush.looply.core.util.HapticsManager
import com.arjunaayush.looply.core.util.ShakeDetector
import com.arjunaayush.looply.feature.feed.ReelsScreen
import com.arjunaayush.looply.feature.feed.ReelsViewModel
import com.arjunaayush.looply.feature.feed.components.ImportReelDialog
import com.arjunaayush.looply.feature.saved.SavedVideosScreen
import com.arjunaayush.looply.feature.saved.SavedVideosViewModel
import com.arjunaayush.looply.feature.settings.SettingsScreen
import com.arjunaayush.looply.feature.settings.SettingsViewModel
import com.arjunaayush.looply.features.download.DownloadReelWorker
import dagger.hilt.android.AndroidEntryPoint

enum class MainTab {
    FEED,
    SAVED,
    SETTINGS
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val reelsViewModel: ReelsViewModel by viewModels()
    private val savedViewModel: SavedVideosViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            LooplyTheme {
                MainAppScaffold(
                    reelsViewModel = reelsViewModel,
                    savedViewModel = savedViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (action == Intent.ACTION_SEND && !text.isNullOrBlank()) {
            DownloadReelWorker.enqueue(this, text.trim())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    reelsViewModel: ReelsViewModel,
    savedViewModel: SavedVideosViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentTab by remember { mutableStateOf(MainTab.FEED) }
    var showImportDialog by remember { mutableStateOf(false) }

    val playbackProgress by reelsViewModel.playbackProgress.collectAsStateWithLifecycle()
    val shakeToShuffleEnabled by reelsViewModel.shakeToShuffleEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val hapticsManager = remember { HapticsManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, shakeToShuffleEnabled) {
        if (!shakeToShuffleEnabled) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val detector = ShakeDetector {
            hapticsManager.playHaptic(HapticEffectType.CONFIRM)
            reelsViewModel.shuffleReel()
            currentTab = MainTab.FEED
            Toast.makeText(context, "Shuffled to random loop", Toast.LENGTH_SHORT).show()
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                detector.start(sensorManager)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                detector.stop(sensorManager)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            detector.start(sensorManager)
        }

        onDispose {
            detector.stop(sensorManager)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val screenTitle = when (currentTab) {
        MainTab.FEED -> "Feed"
        MainTab.SAVED -> "Saved Loops"
        MainTab.SETTINGS -> "Settings"
    }

    val handleDirectDownloadFromClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        val clipText = if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        } else ""

        if (clipText.contains("instagram.com", ignoreCase = true) || clipText.contains("instagr.am", ignoreCase = true)) {
            hapticsManager.playHaptic(HapticEffectType.CONFIRM)
            DownloadReelWorker.enqueue(context, clipText)
            Toast.makeText(context, "Downloading copied reel...", Toast.LENGTH_SHORT).show()
        } else {
            hapticsManager.playHaptic(HapticEffectType.TICK)
            val msg = if (clipText.isBlank()) "Clipboard is empty" else "No Instagram link found on clipboard"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            if (currentTab != MainTab.FEED) {
                LinkerlyTopBar(
                    title = {
                        Text(
                            text = screenTitle,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (currentTab == MainTab.SAVED) {
                            LinkerlyIconButton(onClick = handleDirectDownloadFromClipboard) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.Buttons.Paste,
                                    contentDescription = "Download Copied Reel",
                                    size = 20.dp
                                )
                            }
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentTab != MainTab.FEED) {
                            Modifier.padding(top = innerPadding.calculateTopPadding())
                        } else {
                            Modifier
                        }
                    )
            ) {
                when (currentTab) {
                    MainTab.FEED -> ReelsScreen(
                        viewModel = reelsViewModel,
                        onImportClick = handleDirectDownloadFromClipboard,
                        isTabActive = currentTab == MainTab.FEED
                    )
                    MainTab.SAVED -> SavedVideosScreen(
                        viewModel = savedViewModel,
                        onVideoClick = { videoId ->
                            val index = reelsViewModel.uiState.value.videos.indexOfFirst { it.id == videoId }
                            if (index >= 0) {
                                reelsViewModel.setPage(index)
                            }
                            currentTab = MainTab.FEED
                        },
                        onImportClick = handleDirectDownloadFromClipboard
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel
                    )
                }
            }

            // Glassmorphic floating nav pill overlaying content
            LinkerlyNavigationBar(
                progress = if (currentTab == MainTab.FEED) playbackProgress else 0f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                LinkerlyNavigationBarItem(
                    selected = currentTab == MainTab.FEED,
                    onClick = { currentTab = MainTab.FEED },
                    icon = {
                        LinkerlyIcon(
                            imageVector = if (currentTab == MainTab.FEED) LinkerlyIcons.NavBar.HomeSelected else LinkerlyIcons.NavBar.Home,
                            contentDescription = "Feed"
                        )
                    }
                )

                LinkerlyNavigationBarItem(
                    selected = currentTab == MainTab.SAVED,
                    onClick = { currentTab = MainTab.SAVED },
                    icon = {
                        LinkerlyIcon(
                            imageVector = if (currentTab == MainTab.SAVED) LinkerlyIcons.Buttons.Bookmark else LinkerlyIcons.Bookmark,
                            contentDescription = "Saved"
                        )
                    }
                )

                LinkerlyNavigationBarItem(
                    selected = currentTab == MainTab.SETTINGS,
                    onClick = { currentTab = MainTab.SETTINGS },
                    icon = {
                        LinkerlyIcon(
                            imageVector = LinkerlyIcons.Profile.Settings,
                            contentDescription = "Settings"
                        )
                    }
                )
            }

            if (showImportDialog) {
                ImportReelDialog(onDismiss = { showImportDialog = false })
            }
        }
    }
}