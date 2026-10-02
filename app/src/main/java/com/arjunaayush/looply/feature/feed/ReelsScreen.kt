package com.arjunaayush.looply.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arjunaayush.looply.feature.feed.components.FeedEmptyState
import com.arjunaayush.looply.feature.feed.components.ReelPlayerItem

@Composable
fun ReelsScreen(
    viewModel: ReelsViewModel,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.videos.isEmpty()) {
        FeedEmptyState(
            onImportClick = onImportClick,
            modifier = modifier
        )
    } else {
        val pagerState = rememberPagerState(
            initialPage = uiState.selectedIndex,
            pageCount = { uiState.videos.size }
        )

        LaunchedEffect(pagerState.currentPage) {
            viewModel.setPage(pagerState.currentPage)
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
                    onDelete = { viewModel.deleteVideo(video.id) }
                )
            }
        }
    }
}
