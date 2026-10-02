package com.arjunaayush.looply.feature.feed.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

@Composable
fun FeedEmptyState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            LinkerlyIcon(
                imageVector = LinkerlyIcons.Buttons.PlayVideo,
                contentDescription = null,
                size = 64.dp,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Loops Yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Share reels directly from Instagram or paste a video link to watch and loop offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            LinkerlyButton(
                onClick = onImportClick
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Paste,
                    contentDescription = null,
                    size = 18.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Paste Reel Link")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedEmptyStatePreview() {
    LooplyTheme {
        FeedEmptyState(onImportClick = {})
    }
}
