package com.arjunaayush.looply.ui.components

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.arjunaayush.looply.ui.theme.LooplyColors

class BottomNavigation(private val context: Context) {

    enum class Tab {
        HOME,
        REELS,
        SAVED,
        SETTINGS
    }

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun create(
        activeTab: Tab,
        onTabSelected: (Tab) -> Unit
    ): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Top subtle divider line
        val divider = View(context).apply {
            setBackgroundColor(LooplyColors.Divider)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        }

        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(LooplyColors.Background)
            setPadding(0, dp(8), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Add 4 tabs: Home, Reels, Saved, Settings
        navRow.addView(createNavItem("🏠", "Home", activeTab == Tab.HOME) { onTabSelected(Tab.HOME) })
        navRow.addView(createNavItem("🎬", "Reels", activeTab == Tab.REELS) { onTabSelected(Tab.REELS) })
        navRow.addView(createNavItem("📁", "Saved", activeTab == Tab.SAVED) { onTabSelected(Tab.SAVED) })
        navRow.addView(createNavItem("⚙️", "Settings", activeTab == Tab.SETTINGS) { onTabSelected(Tab.SETTINGS) })

        container.addView(divider)
        container.addView(navRow)

        return container
    }

    private fun createNavItem(
        icon: String,
        label: String,
        isActive: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val iconView = TextView(context).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 11f
            typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(if (isActive) LooplyColors.Primary else LooplyColors.IconInactive)
            gravity = Gravity.CENTER
        }

        item.addView(iconView)
        item.addView(labelView)

        return item
    }
}