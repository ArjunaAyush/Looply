package com.arjunaayush.looply.ui.screens

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.arjunaayush.looply.ui.components.BottomNavigation
import com.arjunaayush.looply.ui.theme.LooplyColors

class HomeScreen(private val context: Context) {

    // Status text exposed for MainActivity to update progress & feedback
    val statusText: TextView = TextView(context).apply {
        text = "Ready to play offline reels"
        textSize = 12f
        setTextColor(LooplyColors.TextSecondary)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(2), dp(12), dp(2))
    }

    // Video container exposed for MainActivity to attach the video player
    val videoContainer: FrameLayout = FrameLayout(context).apply {
        val containerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(LooplyColors.VideoBackground)
        }
        background = containerBg
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            setMargins(dp(12), dp(4), dp(12), dp(8))
        }
    }

    // Action button to jump directly into full-screen Reels feed
    private val watchReelsButton: TextView = TextView(context).apply {
        text = "🎬 Watch in Reels Feed"
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(LooplyColors.TextPrimary)
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(10), dp(16), dp(10))

        val btnBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(LooplyColors.SurfaceElevated)
            setStroke(dp(1), LooplyColors.Primary)
        }
        background = btnBg
        isClickable = true
        isFocusable = true

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(16), 0, dp(16), dp(8))
        }
    }

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Builds and returns the complete HomeScreen layout.
     */
    fun create(
        onImportClick: () -> Unit,
        onReelsClick: () -> Unit = {},
        onSavedClick: () -> Unit = {},
        onSettingsClick: () -> Unit = {}
    ): LinearLayout {
        // Root vertical container
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(LooplyColors.Background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 1. Top Header Bar (Title + Subtitle on left, Import button on right)
        val headerLayout = createHeader(onImportClick)

        // 2. Setup Empty State inside video container before video loads
        setupEmptyState()

        // 3. Connect Watch Reels Feed button action
        watchReelsButton.setOnClickListener {
            onReelsClick()
        }

        // 4. Bottom Navigation Bar
        val bottomNav = BottomNavigation(context).create(BottomNavigation.Tab.HOME) { tab ->
            when (tab) {
                BottomNavigation.Tab.REELS -> onReelsClick()
                BottomNavigation.Tab.SAVED -> onSavedClick()
                BottomNavigation.Tab.SETTINGS -> onSettingsClick()
                BottomNavigation.Tab.HOME -> { /* Already on Home */ }
            }
        }

        // Safely detach persistent member views from any previous parent before adding
        (statusText.parent as? ViewGroup)?.removeView(statusText)
        (videoContainer.parent as? ViewGroup)?.removeView(videoContainer)
        (watchReelsButton.parent as? ViewGroup)?.removeView(watchReelsButton)

        // Add views to root
        rootLayout.addView(headerLayout)
        rootLayout.addView(statusText)
        rootLayout.addView(videoContainer)
        rootLayout.addView(watchReelsButton)
        rootLayout.addView(bottomNav)

        return rootLayout
    }

    private fun createHeader(onImportClick: () -> Unit): LinearLayout {
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(16), dp(16), dp(16), dp(4))
            }
        }

        val titleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val title = TextView(context).apply {
            text = "Looply ❤️"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
        }

        val subtitle = TextView(context).apply {
            text = "Your offline reels space"
            textSize = 12f
            setTextColor(LooplyColors.TextSecondary)
        }

        titleContainer.addView(title)
        titleContainer.addView(subtitle)

        val importButton = TextView(context).apply {
            text = "＋ Import"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))

            val buttonBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(LooplyColors.Primary)
            }
            background = buttonBg
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onImportClick()
            }
        }

        headerLayout.addView(titleContainer)
        headerLayout.addView(importButton)

        return headerLayout
    }

    private fun setupEmptyState() {
        val emptyStateLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val emptyIcon = TextView(context).apply {
            text = "▶"
            textSize = 28f
            setTextColor(LooplyColors.TextPrimary)
            gravity = Gravity.CENTER

            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(LooplyColors.SurfaceElevated)
            }
            background = iconBg

            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                bottomMargin = dp(14)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val emptyTitle = TextView(context).apply {
            text = "No Video Loaded"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        val emptySubtitle = TextView(context).apply {
            text = "Tap '+ Import' above to choose a video from your device"
            textSize = 13f
            setTextColor(LooplyColors.TextSecondary)
            gravity = Gravity.CENTER
        }

        emptyStateLayout.addView(emptyIcon)
        emptyStateLayout.addView(emptyTitle)
        emptyStateLayout.addView(emptySubtitle)

        videoContainer.removeAllViews()
        videoContainer.addView(emptyStateLayout)
    }
}