package com.arjunaayush.looply.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

/**
 * Looply floating glassmorphic rounded pill bottom navigation bar.
 * Designed to float seamlessly above content with a translucent surface and frosted edge.
 */
@Composable
fun LinkerlyNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xD9181820),
            border = BorderStroke(
                width = 1.dp,
                color = Color(0x33FFFFFF)
            ),
            shadowElevation = 16.dp,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
private fun LinkerlyNavigationBarPreview() {
    LooplyTheme {
        LinkerlyNavigationBar {
            LinkerlyNavigationBarItem(
                selected = true,
                onClick = {},
                icon = { LinkerlyIcon(imageVector = LinkerlyIcons.NavBar.HomeSelected, contentDescription = null) }
            )
            LinkerlyNavigationBarItem(
                selected = false,
                onClick = {},
                icon = { LinkerlyIcon(imageVector = LinkerlyIcons.Buttons.PlayVideo, contentDescription = null) }
            )
        }
    }
}
