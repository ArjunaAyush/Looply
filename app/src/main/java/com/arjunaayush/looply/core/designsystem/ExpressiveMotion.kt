package com.arjunaayush.looply.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Reusable spring physics animation specifications for Material 3 Expressive motions.
 */
object LinkerlySprings {
    val Snappy: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val Bouncy: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val Gentle: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Applies a tactile, spring-based compression micro-interaction on press.
 */
@Composable
fun Modifier.expressivePressScale(
    targetScale: Float = 0.96f,
    interactionSource: MutableInteractionSource? = null,
    hapticFeedbackOnPress: Boolean = false,
    animationSpec: AnimationSpec<Float> = LinkerlySprings.Snappy
): Modifier {
    val resolvedSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    if (hapticFeedbackOnPress) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = animationSpec,
        label = "expressivePressScale"
    )

    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
