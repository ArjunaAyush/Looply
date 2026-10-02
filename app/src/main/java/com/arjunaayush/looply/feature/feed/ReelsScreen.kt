package com.arjunaayush.looply.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.ReelSortBottomSheet
import com.arjunaayush.looply.feature.feed.components.FeedEmptyState
import com.arjunaayush.looply.feature.feed.components.ReelPlayerItem
import kotlinx.coroutines.launch

@Composable
fun ReelsScreen(
    viewModel: ReelsViewModel,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTabActive: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showSortSheet by remember { mutableStateOf(false) }

    if (uiState.videos.isEmpty()) {
        FeedEmptyState(
            onImportClick = onImportClick,
            showImportButton = false,
            showFeedHeader = true,
            modifier = modifier
        )
    } else {
        val pagerState = rememberPagerState(
            initialPage = uiState.selectedIndex.coerceIn(0, (uiState.videos.size - 1).coerceAtLeast(0)),
            pageCount = { uiState.videos.size }
        )

        LaunchedEffect(uiState.selectedIndex) {
            if (uiState.selectedIndex in 0 until uiState.videos.size &&
                pagerState.currentPage != uiState.selectedIndex
            ) {
                pagerState.scrollToPage(uiState.selectedIndex)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (uiState.selectedIndex != pagerState.currentPage) {
                viewModel.setPage(pagerState.currentPage)
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> uiState.videos[page].id }
            ) { page ->
                val video = uiState.videos[page]
                ReelPlayerItem(
                    video = video,
                    isCurrentPage = page == pagerState.currentPage,
                    isLooping = uiState.isLooping,
                    isMuted = uiState.isMuted,
                    onToggleFavorite = { viewModel.toggleFavorite(video.id) },
                    onLike = { viewModel.likeVideo(video.id) },
                    onDelete = { viewModel.deleteVideo(video.id) },
                    onToggleMute = { viewModel.toggleMute() },
                    onOpenSort = { showSortSheet = true },
                    isTabActive = isTabActive
                )
            }

            // Sleek Feed text overlay at the top (tapping scrolls to topmost reel)
            Text(
                text = "Feed",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .clickable {
                        scope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )

            if (showSortSheet) {
                ReelSortBottomSheet(
                    selectedSort = uiState.sortOrder,
                    onSelectSort = { order ->
                        viewModel.setSortOrder(order)
                        scope.launch {
                            pagerState.scrollToPage(0)
                        }
                    },
                    onDismiss = { showSortSheet = false }
                )
            }
        }
    }
}
