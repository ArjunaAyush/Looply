package com.arjunaayush.looply.feature.saved.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.LinkerlyChip
import com.arjunaayush.looply.domain.model.CreatorGroup

@Composable
fun CreatorFilterRow(
    creatorGroups: List<CreatorGroup>,
    selectedCreator: String?,
    onSelectCreator: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinkerlyChip(
            text = "All Loops",
            isSelected = selectedCreator == null,
            onClick = { onSelectCreator(null) }
        )

        creatorGroups.forEach { group ->
            LinkerlyChip(
                text = "${group.creatorHandle} (${group.videoCount})",
                isSelected = selectedCreator == group.creatorHandle,
                onClick = { onSelectCreator(group.creatorHandle) }
            )
        }
    }
}
