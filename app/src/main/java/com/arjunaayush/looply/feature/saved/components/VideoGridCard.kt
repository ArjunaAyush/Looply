package com.arjunaayush.looply.feature.saved.components

import androidx.compose.foundation.background
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

@Composable
fun VideoGridCard(
    video: Video,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    LinkerlyCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
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
