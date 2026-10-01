package com.arjunaayush.looply

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ShareIntentHandler
import com.arjunaayush.looply.features.importvideo.VideoImport
import com.arjunaayush.looply.features.instagram.InstagramDownloader
import com.arjunaayush.looply.features.storage.VideoStorageManager
import com.arjunaayush.looply.navigation.AppNavigation
import com.arjunaayush.looply.navigation.Screen
import com.arjunaayush.looply.ui.screens.HomeScreen
import com.arjunaayush.looply.ui.screens.ReelsScreen
import com.arjunaayush.looply.ui.screens.SavedVideosScreen
import com.arjunaayush.looply.ui.screens.SettingsScreen
import com.arjunaayush.looply.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var repository: VideoRepository
    private lateinit var storageManager: VideoStorageManager
    private lateinit var videoImport: VideoImport
    private lateinit var shareIntentHandler: ShareIntentHandler
    private lateinit var instagramDownloader: InstagramDownloader
    private lateinit var navigation: AppNavigation

    private lateinit var homeScreen: HomeScreen
    private var reelsScreen: ReelsScreen? = null
    private var homePlayer: ExoPlayer? = null

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val total = repository.getAllVideos().size
            Toast.makeText(this@MainActivity, "Reel saved to offline library! ($total total) ❤️", Toast.LENGTH_SHORT).show()
            when (navigation.currentScreen) {
                is Screen.Home -> showHomeScreen()
                is Screen.Reels -> showReelsScreen(0)
                is Screen.Saved -> showSavedVideosScreen()
                else -> {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = VideoRepository(this)
        storageManager = repository.storageManager

        videoImport = VideoImport(this, storageManager)
        shareIntentHandler = ShareIntentHandler(this, videoImport)

        navigation = AppNavigation { destination ->
            renderScreen(destination)
        }

        // Handle system back navigation (including Android 13+ predictive back gesture)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigation.handleBackPressed()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#121214"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootContainer)

        instagramDownloader = InstagramDownloader(this, repository, videoImport, rootContainer)

        navigation.navigateTo(Screen.Home)

        // Register download receiver for background worker completions
        val filter = android.content.IntentFilter(com.arjunaayush.looply.features.download.DownloadReelWorker.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }

        // Handle video shared from another app (e.g. Gallery, WhatsApp, Photos)
        handleIncomingShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.hasExtra("EXTRA_PLAY_VIDEO_ID")) {
            val videoId = intent.getStringExtra("EXTRA_PLAY_VIDEO_ID")
            if (!videoId.isNullOrEmpty()) {
                val allVideos = repository.getAllVideos()
                val index = allVideos.indexOfFirst { it.id == videoId }
                showReelsScreen(if (index >= 0) index else 0)
                return
            }
        }

        // 1. Handle text/link sharing (e.g. from Instagram, YouTube, Browser, etc.)
        if (shareIntentHandler.isTextShareIntent(intent)) {
            val text = shareIntentHandler.extractSharedText(intent)
            val url = shareIntentHandler.extractUrl(text)
            handleIncomingSharedText(url, text)
            return
        }

        // 2. Handle video files sharing (e.g. from Gallery, Google Photos, Files, WhatsApp)
        if (!shareIntentHandler.isVideoShareIntent(intent)) return

        val uris = shareIntentHandler.extractSharedVideoUris(intent)
        if (uris.isEmpty()) return

        Toast.makeText(this, "Saving ${uris.size} reel(s) automatically...", Toast.LENGTH_SHORT).show()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val importedList = mutableListOf<com.arjunaayush.looply.data.model.Video>()
            for (uri in uris) {
                try {
                    val imported = videoImport.importVideo(uri)
                    val saved = repository.saveVideo(imported)
                    importedList.add(saved)
                } catch (e: Exception) {
                    android.util.Log.e("LooplyShare", "Error importing shared reel: ${e.message}", e)
                }
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (importedList.isNotEmpty()) {
                    val total = repository.getAllVideos().size
                    Toast.makeText(this@MainActivity, "Saved ${importedList.size} new reel(s)! ($total total) ❤️", Toast.LENGTH_SHORT).show()
                    // Instantly open and play the newly saved reel in the Reels feed!
                    showReelsScreen(0)
                } else {
                    Toast.makeText(this@MainActivity, "Could not save shared video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleIncomingSharedText(url: String?, rawText: String?) {
        val targetUrl = url ?: rawText ?: return
        val isInstagram = shareIntentHandler.isInstagramUrl(targetUrl)

        if (isInstagram) {
            startInstagramDownload(targetUrl)
        } else if (targetUrl.endsWith(".mp4") || targetUrl.endsWith(".mov") || targetUrl.endsWith(".webm") || targetUrl.contains("video")) {
            downloadDirectVideo(targetUrl)
        } else {
            android.app.AlertDialog.Builder(this)
                .setTitle("Shared Link Received")
                .setMessage("Looply received the shared link:\n\n$targetUrl\n\nTo save videos into Looply, share a video file directly from your Gallery or Files, or download the Reel first from Instagram.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun startInstagramDownload(instagramUrl: String) {
        Toast.makeText(this, "Looply: Downloading reel in background... ⚡", Toast.LENGTH_SHORT).show()
        com.arjunaayush.looply.features.download.DownloadReelWorker.enqueue(applicationContext, instagramUrl)
    }

    private fun downloadDirectVideo(videoUrl: String) {
        Toast.makeText(this, "Looply: Downloading video in background... ⚡", Toast.LENGTH_SHORT).show()
        com.arjunaayush.looply.features.download.DownloadReelWorker.enqueue(applicationContext, videoUrl)
    }

    private fun renderScreen(screen: Screen) {
        when (screen) {
            is Screen.Home -> showHomeScreen()
            is Screen.Reels -> showReelsScreen(screen.initialIndex)
            is Screen.Saved -> showSavedVideosScreen()
            is Screen.Settings -> showSettingsScreen()
        }
    }

    private fun showHomeScreen() {
        reelsScreen?.onDestroy()
        reelsScreen = null

        rootContainer.removeAllViews()

        homeScreen = HomeScreen(this)

        val homeView = homeScreen.create(
            onImportClick = {
                showImportOptionsDialog()
            },
            onReelsClick = {
                navigation.navigateTo(Screen.Reels())
            },
            onSavedClick = {
                navigation.navigateTo(Screen.Saved)
            },
            onSettingsClick = {
                navigation.navigateTo(Screen.Settings)
            }
        )

        rootContainer.addView(homeView)

        val latestVideo = repository.getLatestVideo()
        if (latestVideo != null) {
            val total = repository.getAllVideos().size
            homeScreen.statusText.text = "Loaded latest reel ($total offline) ❤️"
            playHomeVideo(latestVideo.uri)
        } else {
            homeScreen.statusText.text = "Ready to play offline reels"
        }
    }

    private fun showReelsScreen(initialIndex: Int = 0) {
        homePlayer?.pause()

        val videos = repository.getAllVideos()
        val screen = ReelsScreen(this, videos, initialPosition = initialIndex)
        reelsScreen = screen

        rootContainer.removeAllViews()

        val reelsView = screen.create(
            onHomeClick = {
                navigation.navigateTo(Screen.Home)
            },
            onSavedClick = {
                navigation.navigateTo(Screen.Saved)
            },
            onSettingsClick = {
                navigation.navigateTo(Screen.Settings)
            },
            onImportClick = {
                openVideoPicker()
            },
            onDeleteVideo = { videoToDelete ->
                repository.deleteVideo(videoToDelete)
                Toast.makeText(this, "Reel deleted", Toast.LENGTH_SHORT).show()
                showReelsScreen(0)
            }
        )

        rootContainer.addView(reelsView)
    }

    private fun showSavedVideosScreen() {
        homePlayer?.pause()
        reelsScreen?.onDestroy()
        reelsScreen = null

        val videos = repository.getAllVideos()
        val screen = SavedVideosScreen(this, videos)

        rootContainer.removeAllViews()

        val savedView = screen.create(
            onHomeClick = {
                navigation.navigateTo(Screen.Home)
            },
            onReelsClick = {
                navigation.navigateTo(Screen.Reels())
            },
            onSettingsClick = {
                navigation.navigateTo(Screen.Settings)
            },
            onImportClick = {
                showImportOptionsDialog()
            },
            onPlayVideo = { selectedVideo ->
                val allVideos = repository.getAllVideos()
                val index = allVideos.indexOfFirst { it.id == selectedVideo.id }
                navigation.navigateTo(Screen.Reels(initialIndex = if (index >= 0) index else 0))
            },
            onDeleteVideo = { videoToDelete ->
                val deleted = repository.deleteVideo(videoToDelete)
                if (deleted) {
                    Toast.makeText(this, "Reel deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not delete reel", Toast.LENGTH_SHORT).show()
                }
                showSavedVideosScreen()
            }
        )

        rootContainer.addView(savedView)
    }

    private fun showImportOptionsDialog() {
        val options = arrayOf("📂 Choose from Gallery / Files", "📲 Download Instagram Reel (Paste Link)")
        android.app.AlertDialog.Builder(this)
            .setTitle("Add Reel to Looply ❤️")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openVideoPicker()
                    1 -> showPasteInstagramLinkDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPasteInstagramLinkDialog() {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: ""
        val initialUrl = if (shareIntentHandler.isInstagramUrl(clipText)) clipText else ""

        val inputEditText = android.widget.EditText(this).apply {
            hint = "Paste Instagram Reel link here..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888890"))
            setText(initialUrl)
            setPadding(40, 36, 40, 36)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#24242A"))
                cornerRadius = 18f
                setStroke(1, Color.parseColor("#3E3E48"))
            }
            background = bg
        }

        val container = android.widget.FrameLayout(this).apply {
            setPadding(50, 30, 50, 20)
            addView(inputEditText)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Download Instagram Reel 📲")
            .setMessage("Paste an Instagram Reel link to download and store it for offline looping:")
            .setView(container)
            .setPositiveButton("Download") { _, _ ->
                val enteredUrl = inputEditText.text.toString().trim()
                if (enteredUrl.isNotEmpty()) {
                    startInstagramDownload(enteredUrl)
                } else {
                    Toast.makeText(this, "Please enter an Instagram Reel link", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsScreen() {
        homePlayer?.pause()
        reelsScreen?.onDestroy()
        reelsScreen = null

        val videos = repository.getAllVideos()
        val screen = SettingsScreen(this, videos)

        rootContainer.removeAllViews()

        val settingsView = screen.create(
            onHomeClick = {
                navigation.navigateTo(Screen.Home)
            },
            onReelsClick = {
                navigation.navigateTo(Screen.Reels())
            },
            onSavedClick = {
                navigation.navigateTo(Screen.Saved)
            },
            onClearAllVideos = {
                repository.deleteAllVideos()
                Toast.makeText(this, "All saved reels cleared", Toast.LENGTH_SHORT).show()
                showSettingsScreen()
            }
        )

        rootContainer.addView(settingsView)
    }

    private fun openVideoPicker() {
        val intent = videoImport.createPickerIntent()
        startActivityForResult(intent, Constants.REQUEST_CODE_PICK_VIDEO)
    }

    @Deprecated("Deprecated in Android, but works for this basic file picker")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == Constants.REQUEST_CODE_PICK_VIDEO && resultCode == RESULT_OK) {
            val videoUri = data?.data ?: return

            try {
                val imported = videoImport.importVideo(videoUri)
                val savedVideo = repository.saveVideo(imported)
                val totalVideos = repository.getAllVideos().size

                when (navigation.currentScreen) {
                    is Screen.Reels -> {
                        Toast.makeText(this, "Reel imported! ($totalVideos total) ❤️", Toast.LENGTH_SHORT).show()
                        showReelsScreen()
                    }
                    is Screen.Saved -> {
                        Toast.makeText(this, "Reel added to library! ($totalVideos total) ❤️", Toast.LENGTH_SHORT).show()
                        showSavedVideosScreen()
                    }
                    is Screen.Settings -> {
                        Toast.makeText(this, "Reel imported! ($totalVideos total) ❤️", Toast.LENGTH_SHORT).show()
                        showSettingsScreen()
                    }
                    else -> {
                        homeScreen.statusText.text = "Reel saved! ($totalVideos total) ❤️"
                        playHomeVideo(savedVideo.uri)
                    }
                }
            } catch (e: Exception) {
                if (navigation.currentScreen is Screen.Home) {
                    homeScreen.statusText.text = "Could not save video: ${e.message}"
                } else {
                    Toast.makeText(this, "Could not save video: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun playHomeVideo(uri: Uri) {
        homePlayer?.release()

        val newPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
        homePlayer = newPlayer

        val playerView = PlayerView(this).apply {
            player = newPlayer
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.TRANSPARENT)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            setOnClickListener {
                if (newPlayer.isPlaying) {
                    newPlayer.pause()
                    homeScreen.statusText.text = "Paused ⏸"
                } else {
                    newPlayer.play()
                    homeScreen.statusText.text = "Playing ▶"
                }
            }
        }

        homeScreen.videoContainer.removeAllViews()
        homeScreen.videoContainer.addView(playerView)
    }

    override fun onPause() {
        super.onPause()
        homePlayer?.pause()
        reelsScreen?.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (navigation.currentScreen is Screen.Home) {
            if (homePlayer != null && homePlayer?.playbackState == Player.STATE_READY) {
                homePlayer?.play()
            }
        } else if (navigation.currentScreen is Screen.Reels) {
            reelsScreen?.onResume()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {}
        homePlayer?.release()
        homePlayer = null
        reelsScreen?.onDestroy()
        reelsScreen = null
        super.onDestroy()
    }
}