package com.arjunaayush.looply.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.ReelSortBottomSheet
import com.arjunaayush.looply.domain.model.ReelSmartFilter
import com.arjunaayush.looply.feature.feed.components.FeedEmptyState
import com.arjunaayush.looply.feature.saved.components.CreatorFilterRow
import com.arjunaayush.looply.feature.saved.components.SmartFilterAndSortRow
import com.arjunaayush.looply.feature.saved.components.StorageUsageHeader
import com.arjunaayush.looply.feature.saved.components.VideoGridCard

@Composable
fun SavedVideosScreen(
    viewModel: SavedVideosViewModel,
    onVideoClick: (String) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.totalSavedCount > 0) {
            StorageUsageHeader(
                storageFormatted = uiState.storageUsageFormatted,
                onClearWatched = { viewModel.clearWatchedVideos() }
            )

            // Smart Filters (All, Liked, Unwatched, Watched) + Sort trigger
            SmartFilterAndSortRow(
                selectedFilter = uiState.selectedFilter,
                onSelectFilter = { viewModel.selectFilter(it) },
                selectedSort = uiState.selectedSort,
                onOpenSortSheet = { showSortSheet = true }
            )

            // Creator grouping chips
            if (uiState.creatorGroups.isNotEmpty()) {
                CreatorFilterRow(
                    creatorGroups = uiState.creatorGroups,
                    selectedCreator = uiState.selectedCreator,
                    onSelectCreator = { viewModel.selectCreator(it) }
                )
            }

            if (uiState.displayedVideos.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.displayedVideos,
                        key = { it.id }
                    ) { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onVideoClick(video.id) },
                            onDelete = { viewModel.deleteVideo(video.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (uiState.selectedFilter) {
                            ReelSmartFilter.LIKED -> "No liked reels yet.\nDouble-tap any reel in the Feed to like it."
                            ReelSmartFilter.UNWATCHED -> "All saved reels have been watched."
                            ReelSmartFilter.WATCHED -> "No watched reels yet."
                            else -> "No reels found matching this filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            FeedEmptyState(
                onImportClick = onImportClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showSortSheet) {
        ReelSortBottomSheet(
            selectedSort = uiState.selectedSort,
            onSelectSort = { viewModel.selectSort(it) },
            onDismiss = { showSortSheet = false }
        )
    }
}
