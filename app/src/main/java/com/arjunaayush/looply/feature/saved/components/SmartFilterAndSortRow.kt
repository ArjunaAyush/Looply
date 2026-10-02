package com.arjunaayush.looply.feature.saved.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.LinkerlyChip
import com.arjunaayush.looply.core.designsystem.theme.LooplyPink
import com.arjunaayush.looply.domain.model.ReelSmartFilter
import com.arjunaayush.looply.domain.model.ReelSortOrder

@Composable
fun SmartFilterAndSortRow(
    selectedFilter: ReelSmartFilter,
    onSelectFilter: (ReelSmartFilter) -> Unit,
    selectedSort: ReelSortOrder,
    onOpenSortSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Sort Option Trigger Chip
        LinkerlyChip(
            text = "Sort: ${selectedSort.displayName}",
            isSelected = true,
            containerColor = LooplyPink.copy(alpha = 0.15f),
            contentColor = LooplyPink,
            onClick = onOpenSortSheet
        )

        // Smart Filters (All, Liked, Unwatched, Watched)
        ReelSmartFilter.values().forEach { filter ->
            LinkerlyChip(
                text = filter.displayName,
                isSelected = filter == selectedFilter,
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}
