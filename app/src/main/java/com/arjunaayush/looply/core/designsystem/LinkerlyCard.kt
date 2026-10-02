package com.arjunaayush.looply.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.theme.LinkerlyElevation
import com.arjunaayush.looply.core.designsystem.theme.LocalHighContrastEnabled
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

/**
 * Looply default card container adhering to Linkerly design standard.
 */
@Composable
fun LinkerlyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline
    ),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = LinkerlyElevation.Small,
    content: @Composable () -> Unit
) {
    val isHighContrast = LocalHighContrastEnabled.current
    val effectiveBorder = if (isHighContrast) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    } else {
        border
    }

    val cardModifier = modifier.defaultMinSize(minHeight = 44.dp)
    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val animatedElevation by animateDpAsState(
            targetValue = if (isPressed) (shadowElevation * 0.4f).coerceAtLeast(0.dp) else shadowElevation,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "cardShadowElevation"
        )

        Surface(
            onClick = onClick,
            modifier = cardModifier.expressivePressScale(
                targetScale = 0.975f,
                interactionSource = interactionSource,
                hapticFeedbackOnPress = true
            ),
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = effectiveBorder,
            tonalElevation = tonalElevation,
            shadowElevation = animatedElevation,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = effectiveBorder,
            tonalElevation = tonalElevation,
            shadowElevation = shadowElevation,
            content = content
        )
    }
}

fun Modifier.mergedCardBorder(
    index: Int,
    totalItems: Int,
    strokeWidth: Dp = 1.5.dp,
    color: Color,
    cornerRadius: Dp = 18.dp
): Modifier = this.drawBehind {
    val strokeWidthPx = strokeWidth.toPx()
    val radiusPx = cornerRadius.toPx()
    val width = size.width
    val height = size.height

    val path = Path().apply {
        if (totalItems == 1) {
            addRoundRect(
                RoundRect(
                    rect = Rect(strokeWidthPx / 2, strokeWidthPx / 2, width - strokeWidthPx / 2, height - strokeWidthPx / 2),
                    cornerRadius = CornerRadius(radiusPx - strokeWidthPx / 2, radiusPx - strokeWidthPx / 2)
                )
            )
        } else if (index == 0) {
            moveTo(strokeWidthPx / 2, height)
            lineTo(strokeWidthPx / 2, radiusPx)
            arcTo(
                rect = Rect(strokeWidthPx / 2, strokeWidthPx / 2, radiusPx * 2 - strokeWidthPx / 2, radiusPx * 2 - strokeWidthPx / 2),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(width - radiusPx, strokeWidthPx / 2)
            arcTo(
                rect = Rect(width - radiusPx * 2 + strokeWidthPx / 2, strokeWidthPx / 2, width - strokeWidthPx / 2, radiusPx * 2 - strokeWidthPx / 2),
                startAngleDegrees = 270f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )
            lineTo(width - strokeWidthPx / 2, height)
        } else if (index == totalItems - 1) {
            moveTo(strokeWidthPx / 2, 0f)
            lineTo(strokeWidthPx / 2, height - radiusPx)
            arcTo(
                rect = Rect(strokeWidthPx / 2, height - radiusPx * 2 + strokeWidthPx / 2, radiusPx * 2 - strokeWidthPx / 2, height - strokeWidthPx / 2),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(width - radiusPx, height - strokeWidthPx / 2)
            arcTo(
                rect = Rect(width - radiusPx * 2 + strokeWidthPx / 2, height - radiusPx * 2 + strokeWidthPx / 2, width - strokeWidthPx / 2, height - strokeWidthPx / 2),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(width - strokeWidthPx / 2, 0f)
        } else {
            moveTo(strokeWidthPx / 2, 0f)
            lineTo(strokeWidthPx / 2, height)
            moveTo(width - strokeWidthPx / 2, 0f)
            lineTo(width - strokeWidthPx / 2, height)
        }
    }

    drawPath(path, color, style = Stroke(width = strokeWidthPx))
}

@Preview(showBackground = true)
@Composable
private fun LinkerlyCardPreview() {
    LooplyTheme {
        LinkerlyCard(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Looply Card Content",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
