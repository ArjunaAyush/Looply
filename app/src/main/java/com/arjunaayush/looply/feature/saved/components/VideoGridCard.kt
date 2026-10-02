package com.arjunaayush.looply.feature.saved.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arjunaayush.looply.core.designsystem.LinkerlyCard
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.theme.LooplyPink
import com.arjunaayush.looply.domain.model.Video
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(
    video: Video,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LinkerlyCard(
        border = if (isSelected) BorderStroke(2.dp, LooplyPink) else null,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumbModel = when {
                video.thumbnailPath.isNotBlank() && File(video.thumbnailPath).exists() -> File(video.thumbnailPath)
                video.filePath.isNotBlank() && File(video.filePath).exists() -> File(video.filePath)
                else -> null
            }

            AsyncImage(
                model = thumbModel,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top-left Selection Checkbox
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(26.dp)
                        .background(
                            if (isSelected) LooplyPink else Color.Black.copy(alpha = 0.65f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LinkerlyIcon(
                        imageVector = if (isSelected) LinkerlyIcons.Buttons.CheckCircleFilled else LinkerlyIcons.Buttons.CheckCircle,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        size = 20.dp
                    )
                }
            }

            // Top-right Favorite indicator
            if (video.isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    LinkerlyIcon(
                        imageVector = LinkerlyIcons.Buttons.FavoriteSelected,
                        contentDescription = null,
                        tint = LooplyPink,
                        size = 16.dp
                    )
                }
            }

            // Bottom metadata gradient bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(8.dp)
            ) {
                Text(
                    text = video.displayAuthor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
