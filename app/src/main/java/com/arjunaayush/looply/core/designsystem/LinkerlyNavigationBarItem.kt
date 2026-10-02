package com.arjunaayush.looply.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

@Composable
fun RowScope.LinkerlyNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = label != null
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "IconScale"
    )

    val fontSize by animateFloatAsState(
        targetValue = if (selected) 13f else 11.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FontSize"
    )

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(
                modifier = Modifier.graphicsLayer(
                    scaleX = iconScale,
                    scaleY = iconScale
                )
            ) {
                icon()
            }
        },
        label = label?.let { labelComposable ->
            {
                val mergedStyle = LocalTextStyle.current.copy(
                    fontSize = fontSize.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    modifier = Modifier.offset(y = (-4).dp)
                ) {
                    ProvideTextStyle(value = mergedStyle) {
                        labelComposable()
                    }
                }
            }
        },
        alwaysShowLabel = alwaysShowLabel,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun LinkerlyNavigationBarItemPreview() {
    LooplyTheme {
        NavigationBar {
            LinkerlyNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { LinkerlyIcon(imageVector = LinkerlyIcons.NavBar.HomeSelected, contentDescription = null) },
                label = { Text("Feed") }
            )
        }
    }
}
