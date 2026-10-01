package com.arjunaayush.looply.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.arjunaayush.looply.ui.theme.LooplyColors

class EmptyState(private val context: Context) {

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Builds and returns a clean, centered Empty State view.
     *
     * @param icon Text/emoji icon to display in the circular badge (e.g. "▶", "🎬", "📁")
     * @param title Main headline (e.g. "No Reels Found")
     * @param subtitle Subtitle description with guidance
     * @param actionText Optional button text (e.g. "＋ Import Video")
     * @param onActionClick Optional callback when the button is clicked
     */
    fun create(
        icon: String,
        title: String,
        subtitle: String,
        actionText: String? = null,
        onActionClick: (() -> Unit)? = null
    ): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Circular Icon Badge
        val iconView = TextView(context).apply {
            text = icon
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(LooplyColors.TextPrimary)

            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(LooplyColors.SurfaceElevated)
            }
            background = iconBg

            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply {
                bottomMargin = dp(16)
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // Title
        val titleView = TextView(context).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(LooplyColors.TextPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        // Subtitle
        val subtitleView = TextView(context).apply {
            text = subtitle
            textSize = 13f
            setTextColor(LooplyColors.TextSecondary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = if (actionText != null) dp(20) else 0
            }
        }

        layout.addView(iconView)
        layout.addView(titleView)
        layout.addView(subtitleView)

        // Optional Action Button
        if (actionText != null && onActionClick != null) {
            val actionButton = TextView(context).apply {
                text = actionText
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(LooplyColors.TextPrimary)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(12), dp(20), dp(12))

                val buttonBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(24).toFloat()
                    setColor(LooplyColors.Primary)
                }
                background = buttonBg
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onActionClick()
                }
            }
            layout.addView(actionButton)
        }

        return layout
    }
}
