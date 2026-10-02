package com.arjunaayush.looply.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

/**
 * Looply Filled Button: consistent rounding, premium elevated drop shadow, and smooth micro-animations.
 */
@Composable
fun LinkerlyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .expressivePressScale(
                targetScale = 0.95f,
                interactionSource = interactionSource,
                hapticFeedbackOnPress = true
            ),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        border = border,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp,
            focusedElevation = 2.dp,
            hoveredElevation = 3.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { content() }
    )
}

/**
 * Looply Outlined Button: elegant border width (1.5.dp), crisp surface matching, and scale-on-press animation.
 */
@Composable
fun LinkerlyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    ),
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedBorder = border ?: BorderStroke(
        width = 1.5.dp,
        color = if (enabled) {
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        }
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .expressivePressScale(
                targetScale = 0.95f,
                interactionSource = interactionSource,
                hapticFeedbackOnPress = true
            ),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = resolvedBorder,
        contentPadding = contentPadding,
        colors = colors,
        interactionSource = interactionSource,
        content = { content() }
    )
}

/**
 * Looply Text Button: flat, clean text actions with scale-on-press behavior.
 */
@Composable
fun LinkerlyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    colors: ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    ),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.expressivePressScale(
            targetScale = 0.96f,
            interactionSource = interactionSource,
            hapticFeedbackOnPress = true
        ),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = { content() }
    )
}

/**
 * Looply Icon Button: standard rounding with scale feedback.
 */
@Composable
fun LinkerlyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.expressivePressScale(
            targetScale = 0.93f,
            interactionSource = interactionSource,
            hapticFeedbackOnPress = true
        ),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = { content() }
    )
}

@Preview(showBackground = true, backgroundColor = 0L)
@Composable
private fun LinkerlyButtonsPreview() {
    LooplyTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinkerlyButton(onClick = {}) {
                Text("Looply Button")
            }
            LinkerlyOutlinedButton(onClick = {}) {
                Text("Outlined Button")
            }
            LinkerlyTextButton(onClick = {}) {
                Text("Text Button")
            }
            LinkerlyIconButton(onClick = {}) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.PlayVideo,
                    contentDescription = null
                )
            }
        }
    }
}
