package com.arjunaayush.looply.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.ui.theme.LooplyColors

/**
 * Full-screen vertical Reel item view with:
 * - Immersive video player surface
 * - Instagram-style double-tap animated heart pop
 * - Right-side floating action column (Like, Info, Delete)
 * - Bottom metadata overlay (Reel index, title, duration, size)
 * - Tap-to-pause toggle with indicator
 */
class ReelItem(private val context: Context) {

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    val container: FrameLayout = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(LooplyColors.VideoBackground)
    }

    val playerView: PlayerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(Color.TRANSPARENT)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    // Centered pause / play indicator badge
    val pauseIndicator: TextView = TextView(context).apply {
        text = "⏸"
        textSize = 34f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        visibility = View.GONE

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#99000000"))
        }
        background = bg

        layoutParams = FrameLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.CENTER
        }
    }

    // Large floating heart for double-tap animation
    private val heartBurstView: TextView = TextView(context).apply {
        text = "❤️"
        textSize = 80f
        gravity = Gravity.CENTER
        visibility = View.GONE
        scaleX = 0f
        scaleY = 0f
        alpha = 0f

        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    // Metadata Views
    private val titleView: TextView = TextView(context).apply {
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        setShadowLayer(8f, 0f, 2f, Color.BLACK)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private val detailsView: TextView = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.parseColor("#E0FFFFFF"))
        setShadowLayer(6f, 0f, 1f, Color.BLACK)
    }

    private val hintView: TextView = TextView(context).apply {
        text = "Tap to pause • Double tap to ❤️"
        textSize = 11f
        setTextColor(Color.parseColor("#99FFFFFF"))
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }

    // Right-side action buttons
    private val likeIconView: TextView = TextView(context).apply {
        text = "🤍"
        textSize = 28f
        gravity = Gravity.CENTER
    }

    private val likeLabelView: TextView = TextView(context).apply {
        text = "Like"
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
    }

    private var isLiked: Boolean = false

    // Callbacks
    private var onTapListener: (() -> Unit)? = null
    private var onDoubleTapListener: (() -> Unit)? = null
    private var onLikeToggleListener: ((Boolean) -> Unit)? = null
    private var onInfoClickListener: (() -> Unit)? = null
    private var onDeleteClickListener: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTapListener?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            playHeartAnimation()
            setLiked(true)
            onDoubleTapListener?.invoke()
            return true
        }
    })

    init {
        // 1. Bottom Left Info Overlay
        val infoOverlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(80), dp(20)) // Leave right padding for action buttons
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        infoOverlay.addView(titleView)
        infoOverlay.addView(detailsView)
        infoOverlay.addView(hintView)

        // 2. Right-side Vertical Action Column
        val actionColumn = createActionColumn()

        // Touch surface for video gestures (single tap play/pause, double tap heart)
        val touchOverlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
            }
        }

        // Assembly (touchOverlay sits under overlays and action column)
        container.addView(playerView)
        container.addView(touchOverlay)
        container.addView(pauseIndicator)
        container.addView(heartBurstView)
        container.addView(infoOverlay)
        container.addView(actionColumn)
    }

    private fun createActionColumn(): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(12), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        }

        // Like Button Item
        val likeItem = createActionButton(likeIconView, likeLabelView) {
            setLiked(!isLiked)
            if (isLiked) {
                playHeartAnimation()
            }
            onLikeToggleListener?.invoke(isLiked)
        }

        // Info Button Item
        val infoIcon = TextView(context).apply {
            text = "ℹ️"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val infoLabel = TextView(context).apply {
            text = "Info"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        val infoItem = createActionButton(infoIcon, infoLabel) {
            onInfoClickListener?.invoke()
        }

        // Delete Button Item
        val deleteIcon = TextView(context).apply {
            text = "🗑"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val deleteLabel = TextView(context).apply {
            text = "Delete"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        val deleteItem = createActionButton(deleteIcon, deleteLabel) {
            onDeleteClickListener?.invoke()
        }

        column.addView(likeItem)
        column.addView(infoItem)
        column.addView(deleteItem)

        return column
    }

    private fun createActionButton(
        iconView: View,
        labelView: View,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
            setOnClickListener { onClick() }

            addView(iconView)
            addView(labelView)
        }
    }

    fun bind(position: Int, video: Video, liked: Boolean = false) {
        val reelName = if (video.title.startsWith("reel_") || video.title.isBlank() || video.title == "Reel") {
            "Offline Reel #${position + 1}"
        } else {
            video.title.substringBeforeLast(".")
        }
        titleView.text = "Reel #${position + 1} • $reelName"

        val durationPart = if (video.formattedDuration.isNotEmpty()) "⏱ ${video.formattedDuration}  •  " else ""
        detailsView.text = "$durationPart${video.formattedSize}"

        setLiked(liked)
        pauseIndicator.visibility = View.GONE
    }

    fun setLiked(liked: Boolean) {
        isLiked = liked
        if (liked) {
            likeIconView.text = "❤️"
            likeLabelView.text = "Liked"
            likeLabelView.setTextColor(LooplyColors.Primary)
        } else {
            likeIconView.text = "🤍"
            likeLabelView.text = "Like"
            likeLabelView.setTextColor(Color.WHITE)
        }
    }

    fun playHeartAnimation() {
        heartBurstView.visibility = View.VISIBLE
        heartBurstView.scaleX = 0.3f
        heartBurstView.scaleY = 0.3f
        heartBurstView.alpha = 1f
        heartBurstView.rotation = (-15..15).random().toFloat()

        heartBurstView.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .setDuration(240)
            .setInterpolator(OvershootInterpolator(2.5f))
            .withEndAction {
                heartBurstView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        heartBurstView.visibility = View.GONE
                    }
                    .start()
            }
            .start()
    }

    fun showPause(show: Boolean) {
        pauseIndicator.text = "⏸"
        pauseIndicator.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun setOnTapListener(onTap: () -> Unit) {
        this.onTapListener = onTap
    }

    fun setOnDoubleTapListener(onDoubleTap: () -> Unit) {
        this.onDoubleTapListener = onDoubleTap
    }

    fun setOnLikeToggleListener(listener: (Boolean) -> Unit) {
        this.onLikeToggleListener = listener
    }

    fun setOnInfoClickListener(listener: () -> Unit) {
        this.onInfoClickListener = listener
    }

    fun setOnDeleteClickListener(listener: () -> Unit) {
        this.onDeleteClickListener = listener
    }
}
