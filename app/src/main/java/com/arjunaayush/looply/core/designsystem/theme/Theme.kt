package com.arjunaayush.looply.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalHighContrastEnabled = staticCompositionLocalOf { false }

private val LooplyDarkColorScheme = darkColorScheme(
    primary = LooplyPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1020),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = LooplyRoseGlow,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B1622),
    onSecondaryContainer = Color(0xFFFFD8E4),
    tertiary = LooplyRoseBlush,
    onTertiary = Color(0xFF4A001C),
    tertiaryContainer = Color(0xFF38101E),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = LooplyBackground,
    onBackground = LooplyTextPrimary,
    surface = LooplySurface,
    onSurface = LooplyTextPrimary,
    surfaceVariant = LooplySurfaceElevated,
    onSurfaceVariant = LooplyTextSecondary,
    outline = LooplyDivider,
    outlineVariant = Color(0x33FF3366),
    error = LooplyError,
    onError = Color.White
)

private val LooplyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LooplyTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalIsDarkTheme provides true,
        LocalHighContrastEnabled provides false
    ) {
        MaterialTheme(
            colorScheme = LooplyDarkColorScheme,
            shapes = LooplyShapes,
            typography = Typography,
            content = content
        )
    }
}
