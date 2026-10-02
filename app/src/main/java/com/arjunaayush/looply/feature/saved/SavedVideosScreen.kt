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
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIconButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyTextButton
import com.arjunaayush.looply.core.designsystem.ReelSortBottomSheet
import com.arjunaayush.looply.core.designsystem.theme.LooplyPink
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
    val context = LocalContext.current
    var showSortSheet by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState.totalSavedCount > 0) {
            if (uiState.isSelectionMode) {
                // Bulk Selection Action Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinkerlyIconButton(onClick = { viewModel.clearSelection() }) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.Close,
                                    contentDescription = "Cancel selection",
                                    size = 20.dp
                                )
                            }
                            Text(
                                text = "${uiState.selectedVideoIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LinkerlyTextButton(
                                onClick = {
                                    if (uiState.selectedVideoIds.size == uiState.displayedVideos.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (uiState.selectedVideoIds.size == uiState.displayedVideos.size) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            LinkerlyIconButton(
                                onClick = {
                                    if (uiState.selectedVideoIds.isNotEmpty()) {
                                        val count = viewModel.exportSelectedVideos(context)
                                        Toast.makeText(context, "Saved $count reels to Gallery", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = uiState.selectedVideoIds.isNotEmpty()
                            ) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.Buttons.Download,
                                    contentDescription = "Export to Gallery",
                                    tint = if (uiState.selectedVideoIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    size = 20.dp
                                )
                            }

                            LinkerlyIconButton(
                                onClick = { showDeleteSelectedDialog = true },
                                enabled = uiState.selectedVideoIds.isNotEmpty()
                            ) {
                                LinkerlyIcon(
                                    imageVector = LinkerlyIcons.Buttons.Delete,
                                    contentDescription = "Delete selected",
                                    tint = if (uiState.selectedVideoIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    size = 20.dp
                                )
                            }
                        }
                    }
                }
            } else {
                StorageUsageHeader(
                    storageFormatted = uiState.storageUsageFormatted,
                    onClearWatched = { viewModel.clearWatchedVideos() },
                    onDeleteAll = { viewModel.deleteAllVideos() }
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
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleVideoSelection(video.id)
                                } else {
                                    onVideoClick(video.id)
                                }
                            },
                            onLongClick = {
                                viewModel.startSelection(video.id)
                            },
                            isSelected = video.id in uiState.selectedVideoIds,
                            isSelectionMode = uiState.isSelectionMode,
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

    if (showDeleteSelectedDialog) {
        Dialog(onDismissRequest = { showDeleteSelectedDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Delete ${uiState.selectedVideoIds.size} Selected Reels?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "This will permanently remove the selected videos from your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinkerlyTextButton(onClick = { showDeleteSelectedDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        LinkerlyButton(
                            onClick = {
                                showDeleteSelectedDialog = false
                                viewModel.deleteSelectedVideos()
                                Toast.makeText(context, "Deleted selected reels", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Buttons.Delete,
                                contentDescription = null,
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
