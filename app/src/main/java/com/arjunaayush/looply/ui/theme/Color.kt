package com.arjunaayush.looply.ui.theme

import android.graphics.Color

object LooplyColors {
    // Brand & Accent Colors
    val Primary: Int = Color.parseColor("#FF3366")        // Looply Rose / Pink Accent
    val PrimaryVariant: Int = Color.parseColor("#E02856") // Darker Pink
    val Accent: Int = Color.parseColor("#7C4DFF")         // Purple Accent

    // Backgrounds & Surfaces (Modern Dark Theme)
    val Background: Int = Color.parseColor("#121214")     // Main Screen Background
    val Surface: Int = Color.parseColor("#1C1C22")        // Card / Container Surface
    val SurfaceElevated: Int = Color.parseColor("#25252E")// Elevated buttons & badges
    val VideoBackground: Int = Color.parseColor("#000000")// Pure Black for Video Player

    // Typography & Content
    val TextPrimary: Int = Color.parseColor("#FFFFFF")    // High Emphasis Text
    val TextSecondary: Int = Color.parseColor("#8E8E9F")  // Medium Emphasis Text
    val TextMuted: Int = Color.parseColor("#5A5A6A")      // Low Emphasis / Captions
    val IconActive: Int = Color.parseColor("#FFFFFF")     // Selected Nav Icon
    val IconInactive: Int = Color.parseColor("#6E6E7E")   // Unselected Nav Icon

    // Status & Utility Colors
    val Success: Int = Color.parseColor("#4ECCA3")        // Success Green
    val Error: Int = Color.parseColor("#FF4C4C")          // Error Red
    val Divider: Int = Color.parseColor("#24242D")        // Subtle Border Lines
}
