package com.arjunaayush.looply.feature.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.feature.feed.components.FeedEmptyState
import com.arjunaayush.looply.feature.saved.components.CreatorFilterRow
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

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.displayedVideos.isNotEmpty()) {
            StorageUsageHeader(
                storageFormatted = uiState.storageUsageFormatted,
                onClearWatched = { viewModel.clearWatchedVideos() }
            )

            if (uiState.creatorGroups.isNotEmpty()) {
                CreatorFilterRow(
                    creatorGroups = uiState.creatorGroups,
                    selectedCreator = uiState.selectedCreator,
                    onSelectCreator = { viewModel.selectCreator(it) }
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
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
            FeedEmptyState(
                onImportClick = onImportClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
