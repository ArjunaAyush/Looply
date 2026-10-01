package com.arjunaayush.looply.ui.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable

object LooplyTheme {

    /**
     * Converts density-independent pixels (dp) to screen pixels (px).
     */
    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Creates a rounded card background drawable.
     */
    fun createCardDrawable(
        context: Context,
        cornerRadiusDp: Int = 14,
        color: Int = LooplyColors.Surface
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, cornerRadiusDp).toFloat()
            setColor(color)
        }
    }

    /**
     * Creates a pill-shaped button background drawable.
     */
    fun createPillDrawable(
        context: Context,
        color: Int = LooplyColors.Primary,
        cornerRadiusDp: Int = 20
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, cornerRadiusDp).toFloat()
            setColor(color)
        }
    }

    /**
     * Creates a circular badge/icon background drawable.
     */
    fun createCircleDrawable(color: Int = LooplyColors.SurfaceElevated): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    /**
     * Creates a bordered card/button drawable with a stroke line.
     */
    fun createBorderDrawable(
        context: Context,
        strokeColor: Int,
        fillColor: Int = LooplyColors.Surface,
        cornerRadiusDp: Int = 12,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, cornerRadiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(context, strokeWidthDp), strokeColor)
        }
    }
}
