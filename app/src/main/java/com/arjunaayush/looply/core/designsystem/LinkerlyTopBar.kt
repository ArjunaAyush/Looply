package com.arjunaayush.looply.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

/**
 * Looply default app top bar matching Linkerly visual hierarchy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkerlyTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    Column(modifier = modifier) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground
            )
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            thickness = 1.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun LinkerlyTopBarPreview() {
    LooplyTheme {
        LinkerlyTopBar(
            title = {
                Text(
                    text = "Looply",
                    fontWeight = FontWeight.Bold
                )
            }
        )
    }
}
