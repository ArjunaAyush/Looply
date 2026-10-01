package com.arjunaayush.looply.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.features.player.ReelPlayerManager
import com.arjunaayush.looply.ui.components.BottomNavigation
import com.arjunaayush.looply.ui.components.EmptyState
import com.arjunaayush.looply.ui.components.ReelItem
import com.arjunaayush.looply.ui.theme.LooplyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReelsScreen(
    private val context: Context,
    private val videos: List<Video>,
    private val initialPosition: Int = 0
) {

    private val playerManager: ReelPlayerManager = ReelPlayerManager(context)
    private var viewPager: ViewPager2? = null
    private var currentPosition: Int = initialPosition
    private var counterBadge: TextView? = null

    // Track liked status in memory for reels
    private val likedIds = mutableSetOf<String>()

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun create(
        onHomeClick: () -> Unit,
        onSavedClick: () -> Unit,
        onSettingsClick: () -> Unit,
        onImportClick: () -> Unit,
        onDeleteVideo: ((Video) -> Unit)? = null
    ): View {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LooplyColors.Background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Bottom Navigation Bar using the reusable BottomNavigation component
        val bottomNav = BottomNavigation(context).create(BottomNavigation.Tab.REELS) { tab ->
            when (tab) {
                BottomNavigation.Tab.HOME -> onHomeClick()
                BottomNavigation.Tab.SAVED -> onSavedClick()
                BottomNavigation.Tab.SETTINGS -> onSettingsClick()
                BottomNavigation.Tab.REELS -> { /* Already on Reels */ }
            }
        }

        // Empty state if no videos are saved yet
        if (videos.isEmpty()) {
            val emptyView = EmptyState(context).create(
                icon = "🎬",
                title = "No Reels Found",
                subtitle = "Import videos from your device to watch them offline in this swipeable feed.",
                actionText = "＋ Import First Reel",
                onActionClick = onImportClick
            )

            rootLayout.addView(
                emptyView,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            rootLayout.addView(bottomNav)
            return rootLayout
        }

        // Full-screen reels container with overlay header
        val reelsContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val vp = ViewPager2(context).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            adapter = ReelsAdapter(onDeleteVideo)
        }
        viewPager = vp

        val headerOverlay = createHeaderOverlay()

        reelsContainer.addView(vp)
        reelsContainer.addView(headerOverlay)

        // Setup page change listener
        vp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                updateCounter(position)
                vp.post {
                    playVideoAt(position)
                }
            }
        })

        rootLayout.addView(reelsContainer)
        rootLayout.addView(bottomNav)

        // Start playing the selected reel
        vp.post {
            if (initialPosition in videos.indices) {
                vp.setCurrentItem(initialPosition, false)
                updateCounter(initialPosition)
                playVideoAt(initialPosition)
            } else {
                playVideoAt(0)
            }
        }

        return rootLayout
    }

    private fun playVideoAt(position: Int) {
        if (position !in videos.indices) return

        val rv = viewPager?.getChildAt(0) as? RecyclerView ?: return
        val holder = rv.findViewHolderForAdapterPosition(position) as? ReelViewHolder

        if (holder != null) {
            playerManager.attachTo(holder.reelItem.playerView)
            holder.reelItem.showPause(false)
            playerManager.playVideo(videos[position].file)
        }
    }

    private fun updateCounter(position: Int) {
        counterBadge?.text = "${position + 1} / ${videos.size}"
    }

    private fun createHeaderOverlay(): LinearLayout {
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }

        val title = TextView(context).apply {
            text = "Reels ❤️"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badge = TextView(context).apply {
            text = "1 / ${videos.size}"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            setPadding(dp(10), dp(4), dp(10), dp(4))

            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#80000000"))
            }
            background = badgeBg
        }
        counterBadge = badge

        overlay.addView(title)
        overlay.addView(badge)

        return overlay
    }

    private fun showVideoDetailsDialog(video: Video) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(video.createdAt))
        val resolutionStr = if (video.width > 0 && video.height > 0) "${video.width} × ${video.height}" else "Standard"

        val details = """
            Title: ${video.title}
            Duration: ${if (video.formattedDuration.isNotEmpty()) video.formattedDuration else "Unknown"}
            Resolution: $resolutionStr
            File Size: ${video.formattedSize}
            Imported: $dateStr
            Storage: ${video.filePath}
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("Reel Details ℹ️")
            .setMessage(details)
            .setPositiveButton("Close", null)
            .show()
    }

    fun onPause() {
        playerManager.pause()
    }

    fun onResume() {
        playerManager.resume()
    }

    fun onDestroy() {
        playerManager.release()
        viewPager = null
    }

    // ViewHolder leveraging ReelItem component
    inner class ReelViewHolder(
        val reelItem: ReelItem,
        private val onDeleteVideo: ((Video) -> Unit)?
    ) : RecyclerView.ViewHolder(reelItem.container) {

        fun bind(position: Int, video: Video) {
            val isLiked = video.id in likedIds
            reelItem.bind(position, video, liked = isLiked)

            reelItem.setOnTapListener {
                val isPlaying = playerManager.togglePlayPause()
                reelItem.showPause(!isPlaying)
            }

            reelItem.setOnDoubleTapListener {
                likedIds.add(video.id)
            }

            reelItem.setOnLikeToggleListener { liked ->
                if (liked) likedIds.add(video.id) else likedIds.remove(video.id)
            }

            reelItem.setOnInfoClickListener {
                showVideoDetailsDialog(video)
            }

            reelItem.setOnDeleteClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Delete Reel")
                    .setMessage("Do you want to permanently delete \"${video.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        onDeleteVideo?.invoke(video)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private inner class ReelsAdapter(
        private val onDeleteVideo: ((Video) -> Unit)?
    ) : RecyclerView.Adapter<ReelViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReelViewHolder {
            val item = ReelItem(context)
            return ReelViewHolder(item, onDeleteVideo)
        }

        override fun onBindViewHolder(holder: ReelViewHolder, position: Int) {
            val video = videos[position]
            holder.bind(position, video)

            if (position == currentPosition) {
                playerManager.attachTo(holder.reelItem.playerView)
            }
        }

        override fun getItemCount(): Int = videos.size
    }
}