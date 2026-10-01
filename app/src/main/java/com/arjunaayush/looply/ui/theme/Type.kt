package com.arjunaayush.looply.ui.theme

import android.graphics.Typeface
import android.widget.TextView

object LooplyTypography {
    // Font Sizes in SP
    const val SizeTitleLarge: Float = 24f
    const val SizeTitleMedium: Float = 20f
    const val SizeTitleSmall: Float = 16f
    const val SizeBody: Float = 14f
    const val SizeCaption: Float = 12f
    const val SizeBadge: Float = 11f

    /**
     * Styles a TextView as a Large Title (e.g. App headers)
     */
    fun applyTitleLarge(textView: TextView) {
        textView.textSize = SizeTitleLarge
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.setTextColor(LooplyColors.TextPrimary)
    }

    /**
     * Styles a TextView as a Medium Title (e.g. Card headers, screen titles)
     */
    fun applyTitleMedium(textView: TextView) {
        textView.textSize = SizeTitleMedium
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.setTextColor(LooplyColors.TextPrimary)
    }

    /**
     * Styles a TextView as a Small Title (e.g. Reel item title)
     */
    fun applyTitleSmall(textView: TextView) {
        textView.textSize = SizeTitleSmall
        textView.typeface = Typeface.DEFAULT_BOLD
        textView.setTextColor(LooplyColors.TextPrimary)
    }

    /**
     * Styles a TextView as Body text
     */
    fun applyBody(textView: TextView) {
        textView.textSize = SizeBody
        textView.typeface = Typeface.DEFAULT
        textView.setTextColor(LooplyColors.TextPrimary)
    }

    /**
     * Styles a TextView as a Secondary Caption or hint
     */
    fun applyCaption(textView: TextView) {
        textView.textSize = SizeCaption
        textView.typeface = Typeface.DEFAULT
        textView.setTextColor(LooplyColors.TextSecondary)
    }
}
