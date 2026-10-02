package com.arjunaayush.looply.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

/**
 * Standard icon sizes for Looply.
 */
object LinkerlyIconSize {
    val ExtraSmall = 14.dp
    val Small = 16.dp
    val MediumSmall = 20.dp
    val Medium = 24.dp
    val Large = 28.dp
    val ExtraLarge = 40.dp
}

@Composable
fun Modifier.autoMirror(): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return if (layoutDirection == LayoutDirection.Rtl) {
        this.graphicsLayer { scaleX = -1f }
    } else {
        this
    }
}

@Composable
fun LinkerlyIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LinkerlyIconSize.Medium,
    tint: Color = LocalContentColor.current,
    autoMirror: Boolean = false
) {
    val autoMirrorModifier = if (autoMirror) Modifier.autoMirror() else Modifier
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.then(autoMirrorModifier).size(size),
        tint = tint
    )
}

@Preview(showBackground = true)
@Composable
private fun LinkerlyIconPreview() {
    LooplyTheme {
        LinkerlyIcon(
            imageVector = LinkerlyIcons.Buttons.PlayVideo,
            contentDescription = null
        )
    }
}
