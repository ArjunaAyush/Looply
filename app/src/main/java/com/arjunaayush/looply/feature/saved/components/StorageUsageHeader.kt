package com.arjunaayush.looply.feature.saved.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.LinkerlyCard
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyOutlinedButton

@Composable
fun StorageUsageHeader(
    storageFormatted: String,
    onClearWatched: () -> Unit,
    modifier: Modifier = Modifier
) {
    LinkerlyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Storage Used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = storageFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinkerlyOutlinedButton(
                onClick = onClearWatched
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Delete,
                    contentDescription = null,
                    size = 16.dp
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Clear Watched")
            }
        }
    }
}
