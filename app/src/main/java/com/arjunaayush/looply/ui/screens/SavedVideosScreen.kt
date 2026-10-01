package com.arjunaayush.looply.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.ui.components.BottomNavigation
import com.arjunaayush.looply.ui.components.EmptyState
import com.arjunaayush.looply.ui.theme.LooplyColors
import com.arjunaayush.looply.utils.ThumbnailLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedVideosScreen(
    private val context: Context,
    private val videos: List<Video>
) {

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun create(
        onHomeClick: () -> Unit,
        onReelsClick: () -> Unit,
        onSettingsClick: () -> Unit,
        onImportClick: () -> Unit,
        onPlayVideo: (Video) -> Unit,
        onDeleteVideo: (Video) -> Unit
    ): View {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LooplyColors.Background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Header Bar
        val header = createHeader(onImportClick)

        // 2. Main content: List of videos or Empty state
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        if (videos.isEmpty()) {
            val emptyView = EmptyState(context).create(
                icon = "📁",
                title = "No Saved Videos",
                subtitle = "Imported reels are stored offline right here on your device.",
                actionText = "＋ Import Video",
                onActionClick = onImportClick
            )
            contentLayout.addView(emptyView)
        } else {
            val recyclerView = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                adapter = SavedVideosAdapter(onPlayVideo, onDeleteVideo)
                setPadding(dp(16), dp(8), dp(16), dp(16))
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            contentLayout.addView(recyclerView)
        }

        // 3. Bottom Navigation Bar (Saved tab is active)
        val bottomNav = BottomNavigation(context).create(BottomNavigation.Tab.SAVED) { tab ->
            when (tab) {
                BottomNavigation.Tab.HOME -> onHomeClick()
                BottomNavigation.Tab.REELS -> onReelsClick()
                BottomNavigation.Tab.SETTINGS -> onSettingsClick()
                BottomNavigation.Tab.SAVED -> { /* Already on Saved */ }
            }
        }

        rootLayout.addView(header)
        rootLayout.addView(contentLayout)
        rootLayout.addView(bottomNav)

        return rootLayout
    }

    private fun createHeader(onImportClick: () -> Unit): LinearLayout {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(context).apply {
            text = "Saved Reels 📁"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
        }

        val subtitle = TextView(context).apply {
            text = "${videos.size} offline reels stored on device"
            textSize = 12f
            setTextColor(LooplyColors.TextSecondary)
        }

        titleContainer.addView(title)
        titleContainer.addView(subtitle)

        val importBtn = TextView(context).apply {
            text = "＋ Import"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(LooplyColors.Primary)
            }
            background = bg
            isClickable = true
            isFocusable = true
            setOnClickListener { onImportClick() }
        }

        header.addView(titleContainer)
        header.addView(importBtn)

        return header
    }

    private inner class SavedVideosAdapter(
        private val onPlayVideo: (Video) -> Unit,
        private val onDeleteVideo: (Video) -> Unit
    ) : RecyclerView.Adapter<SavedVideosAdapter.VideoViewHolder>() {

        inner class VideoViewHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
            val thumbnailView: ImageView
            val durationBadge: TextView
            val titleView: TextView
            val subtitleView: TextView
            val playBtn: TextView
            val deleteBtn: TextView

            init {
                root.orientation = LinearLayout.HORIZONTAL
                root.gravity = Gravity.CENTER_VERTICAL
                root.setPadding(dp(12), dp(10), dp(12), dp(10))

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(LooplyColors.Surface)
                }
                root.background = bg

                // Thumbnail Container with duration overlay
                val thumbContainer = FrameLayout(context).apply {
                    val tBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(LooplyColors.SurfaceElevated)
                    }
                    background = tBg
                    clipToOutline = true
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                    layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                        rightMargin = dp(12)
                    }
                }

                thumbnailView = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                durationBadge = TextView(context).apply {
                    textSize = 9f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    setPadding(dp(4), dp(1), dp(4), dp(1))
                    val dBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(4).toFloat()
                        setColor(Color.parseColor("#B3000000"))
                    }
                    background = dBg
                    visibility = View.GONE
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.BOTTOM or Gravity.END
                        setMargins(0, 0, dp(4), dp(4))
                    }
                }

                thumbContainer.addView(thumbnailView)
                thumbContainer.addView(durationBadge)

                val infoCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                titleView = TextView(context).apply {
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(LooplyColors.TextPrimary)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }

                subtitleView = TextView(context).apply {
                    textSize = 11f
                    setTextColor(LooplyColors.TextSecondary)
                }

                infoCol.addView(titleView)
                infoCol.addView(subtitleView)

                // Play Button
                playBtn = TextView(context).apply {
                    text = "▶ Play"
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(LooplyColors.TextPrimary)
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(6), dp(12), dp(6))

                    val pBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(12).toFloat()
                        setColor(LooplyColors.Primary)
                    }
                    background = pBg
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        rightMargin = dp(8)
                    }
                }

                // Delete Button
                deleteBtn = TextView(context).apply {
                    text = "🗑"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(6), dp(8), dp(6))

                    val dBg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(LooplyColors.SurfaceElevated)
                    }
                    background = dBg
                    isClickable = true
                    isFocusable = true
                }

                root.addView(thumbContainer)
                root.addView(infoCol)
                root.addView(playBtn)
                root.addView(deleteBtn)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
            val root = LinearLayout(context).apply {
                val params = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
                layoutParams = params
            }
            return VideoViewHolder(root)
        }

        override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
            val video = videos[position]
            val displayName = if (video.title.startsWith("reel_") || video.title.isBlank()) {
                "Offline Reel #${position + 1}"
            } else {
                video.title.substringBeforeLast(".")
            }
            holder.titleView.text = displayName
            holder.subtitleView.text = "${video.formattedSize} • ${formatDate(video.createdAt)}"

            if (video.formattedDuration.isNotEmpty()) {
                holder.durationBadge.text = video.formattedDuration
                holder.durationBadge.visibility = View.VISIBLE
            } else {
                holder.durationBadge.visibility = View.GONE
            }

            // Load real video thumbnail asynchronously
            ThumbnailLoader.loadThumbnail(video.file, holder.thumbnailView)

            holder.playBtn.setOnClickListener {
                onPlayVideo(video)
            }

            holder.deleteBtn.setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle("Delete Reel")
                    .setMessage("Permanently delete \"$displayName\" from device?")
                    .setPositiveButton("Delete") { _, _ ->
                        onDeleteVideo(video)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = videos.size
    }
}