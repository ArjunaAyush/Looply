package com.arjunaayush.looply.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.ui.components.BottomNavigation
import com.arjunaayush.looply.ui.theme.LooplyColors

class SettingsScreen(
    private val context: Context,
    private val videos: List<Video>
) {

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getTotalStorageBytes(): Long {
        return videos.sumOf { it.sizeBytes }
    }

    private fun formatStorage(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.2f GB".format(mb / 1024.0)
        } else if (mb >= 1.0) {
            "%.1f MB".format(mb)
        } else {
            "${bytes / 1024} KB"
        }
    }

    fun create(
        onHomeClick: () -> Unit,
        onReelsClick: () -> Unit,
        onSavedClick: () -> Unit,
        onClearAllVideos: () -> Unit
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
        val header = createHeader()

        // 2. Scrollable Settings Content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Section Cards
        scrollContent.addView(createAppInfoCard())
        scrollContent.addView(createStorageCard(onClearAllVideos))
        scrollContent.addView(createPlaybackCard())
        scrollContent.addView(createPrivacyCard())

        scrollView.addView(scrollContent)

        // 3. Bottom Navigation Bar (Settings active)
        val bottomNav = BottomNavigation(context).create(BottomNavigation.Tab.SETTINGS) { tab ->
            when (tab) {
                BottomNavigation.Tab.HOME -> onHomeClick()
                BottomNavigation.Tab.REELS -> onReelsClick()
                BottomNavigation.Tab.SAVED -> onSavedClick()
                BottomNavigation.Tab.SETTINGS -> { /* Already on Settings */ }
            }
        }

        rootLayout.addView(header)
        rootLayout.addView(scrollView)
        rootLayout.addView(bottomNav)

        return rootLayout
    }

    private fun createHeader(): LinearLayout {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(context).apply {
            text = "Settings ⚙️"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
        }

        val subtitle = TextView(context).apply {
            text = "Preferences, storage & app details"
            textSize = 12f
            setTextColor(LooplyColors.TextSecondary)
        }

        header.addView(title)
        header.addView(subtitle)

        return header
    }

    private fun createAppInfoCard(): LinearLayout {
        val card = createCardContainer()

        val brandRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(context).apply {
            text = "❤️"
            textSize = 28f
            gravity = Gravity.CENTER
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(LooplyColors.SurfaceElevated)
            }
            background = iconBg
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(12)
            }
        }

        val infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appName = TextView(context).apply {
            text = "Looply"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
        }

        val version = TextView(context).apply {
            text = "Version 1.0 • Offline Reels Space"
            textSize = 12f
            setTextColor(LooplyColors.TextSecondary)
        }

        infoCol.addView(appName)
        infoCol.addView(version)

        brandRow.addView(icon)
        brandRow.addView(infoCol)

        card.addView(brandRow)
        return card
    }

    private fun createStorageCard(onClearAllVideos: () -> Unit): LinearLayout {
        val card = createCardContainer()

        val sectionTitle = TextView(context).apply {
            text = "Storage & Library"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val countRow = createKeyValueRow("Saved Reels", "${videos.size} videos")
        val sizeRow = createKeyValueRow("Storage Used", formatStorage(getTotalStorageBytes()))
        val storageLocationRow = createKeyValueRow("Storage Location", "Internal App Storage (Private)")

        // Clear All Button
        val clearBtn = TextView(context).apply {
            text = "🗑 Clear All Saved Videos"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.Error)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#20FF4C4C"))
                setStroke(dp(1), LooplyColors.Error)
            }
            background = bg
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }

            setOnClickListener {
                if (videos.isEmpty()) {
                    AlertDialog.Builder(context)
                        .setTitle("Library Empty")
                        .setMessage("There are no saved reels to clear.")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(context)
                        .setTitle("Clear All Videos")
                        .setMessage("Are you sure you want to delete all ${videos.size} saved reels? This cannot be undone.")
                        .setPositiveButton("Clear All") { _, _ ->
                            onClearAllVideos()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        card.addView(sectionTitle)
        card.addView(countRow)
        card.addView(sizeRow)
        card.addView(storageLocationRow)
        card.addView(clearBtn)

        return card
    }

    private fun createPlaybackCard(): LinearLayout {
        val card = createCardContainer()

        val sectionTitle = TextView(context).apply {
            text = "Player & Playback"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }

        val playerEngine = createKeyValueRow("Video Engine", "AndroidX Media3 ExoPlayer")
        val loopMode = createKeyValueRow("Loop Mode", "Continuous Infinite Loop")
        val scaling = createKeyValueRow("Aspect Ratio", "Original (No Distortion)")
        val tapAction = createKeyValueRow("Tap Gesture", "Toggle Play / Pause")
        val doubleTapAction = createKeyValueRow("Double Tap", "Like Reel ❤️ Animation")

        card.addView(sectionTitle)
        card.addView(playerEngine)
        card.addView(loopMode)
        card.addView(scaling)
        card.addView(tapAction)
        card.addView(doubleTapAction)

        return card
    }

    private fun createPrivacyCard(): LinearLayout {
        val card = createCardContainer()

        val sectionTitle = TextView(context).apply {
            text = "Privacy & Offline Guarantee"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        val privacyText = TextView(context).apply {
            text = "Looply runs entirely on your device. Your imported videos are never uploaded to any cloud, database, or server. No internet connection is ever required for playback."
            textSize = 13f
            setTextColor(LooplyColors.TextSecondary)
            setLineSpacing(dp(2).toFloat(), 1.1f)
        }

        card.addView(sectionTitle)
        card.addView(privacyText)

        return card
    }

    private fun createCardContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(LooplyColors.Surface)
            }
            background = bg

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    private fun createKeyValueRow(key: String, value: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }

        val keyView = TextView(context).apply {
            text = key
            textSize = 13f
            setTextColor(LooplyColors.TextSecondary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(context).apply {
            text = value
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
        }

        row.addView(keyView)
        row.addView(valueView)

        return row
    }
}