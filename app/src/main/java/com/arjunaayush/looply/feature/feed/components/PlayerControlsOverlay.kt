package com.arjunaayush.looply.feature.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIconButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.theme.LooplyPink
import com.arjunaayush.looply.core.util.HapticEffectType
import com.arjunaayush.looply.core.util.HapticsManager
import com.arjunaayush.looply.domain.model.Video
import android.widget.Toast
import com.arjunaayush.looply.core.util.MediaExportUtils
import java.io.File

@Composable
fun PlayerControlsOverlay(
    video: Video,
    isLooping: Boolean,
    isMuted: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = HapticsManager(context)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.4f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.75f)
                    )
                )
            )
    ) {
        // Top Right Volume / Mute Button
        LinkerlyIconButton(
            onClick = {
                haptics.playHaptic(HapticEffectType.TICK)
                onToggleMute()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            LinkerlyIcon(
                imageVector = if (isMuted) LinkerlyIcons.Buttons.VolumeOff else LinkerlyIcons.Buttons.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) Color.White.copy(alpha = 0.7f) else LooplyPink,
                size = 22.dp
            )
        }

        // Right Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LinkerlyIconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = if (video.isFavorite) LinkerlyIcons.Buttons.FavoriteSelected else LinkerlyIcons.Buttons.Favorite,
                    contentDescription = null,
                    tint = if (video.isFavorite) LooplyPink else Color.White,
                    size = 24.dp
                )
            }

            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.CONFIRM)
                    val success = MediaExportUtils.exportVideoToDevice(
                        context = context,
                        sourceFile = File(video.filePath),
                        title = video.title.ifBlank { "Reel_${video.id}" }
                    )
                    if (success) {
                        Toast.makeText(context, "Saved reel to Movies/Looply", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not save reel to device", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Download,
                    contentDescription = "Save to device",
                    tint = Color.White,
                    size = 22.dp
                )
            }

            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.HEAVY_DELETE)
                    onDelete()
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    size = 22.dp
                )
            }
        }

        // Bottom Metadata
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 72.dp)
        ) {
            Text(
                text = video.displayAuthor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (video.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
