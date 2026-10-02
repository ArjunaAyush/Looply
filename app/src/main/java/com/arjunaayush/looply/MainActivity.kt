package com.arjunaayush.looply

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIconButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyNavigationBar
import com.arjunaayush.looply.core.designsystem.LinkerlyNavigationBarItem
import com.arjunaayush.looply.core.designsystem.LinkerlyTopBar
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme
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

    val screenTitle = when (currentTab) {
        MainTab.FEED -> "Feed"
        MainTab.SAVED -> "Saved Loops"
        MainTab.SETTINGS -> "Settings"
    }

    Scaffold(
        topBar = {
            LinkerlyTopBar(
                title = {
                    Text(
                        text = screenTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    LinkerlyIconButton(onClick = { showImportDialog = true }) {
                        LinkerlyIcon(
                            imageVector = LinkerlyIcons.Buttons.Paste,
                            contentDescription = "Paste Reel Link",
                            size = 20.dp
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                when (currentTab) {
                    MainTab.FEED -> ReelsScreen(
                        viewModel = reelsViewModel,
                        onImportClick = { showImportDialog = true }
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
                        onImportClick = { showImportDialog = true }
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        viewModel = settingsViewModel
                    )
                }
            }

            // Glassmorphic floating nav pill overlaying content
            LinkerlyNavigationBar(
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