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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
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
    onOpenSort: () -> Unit = {},
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
        // Top Right Actions: Sort icon above, Mute icon below
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.TICK)
                    onOpenSort()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Sort,
                    contentDescription = "Sort feed",
                    tint = Color.White,
                    size = 22.dp
                )
            }

            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.TICK)
                    onToggleMute()
                },
                modifier = Modifier
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
        }

        // Right Action Rail (Heart, Open, Share, Download, Delete)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Heart: Like the reel
            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.FAVORITE_POP)
                    onToggleFavorite()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = if (video.isFavorite) LinkerlyIcons.Buttons.FavoriteSelected else LinkerlyIcons.Buttons.Favorite,
                    contentDescription = if (video.isFavorite) "Liked" else "Like",
                    tint = if (video.isFavorite) LooplyPink else Color.White,
                    size = 22.dp
                )
            }

            // 2. Open: Opens original reel in Instagram app
            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.TICK)
                    val targetUrl = video.reelUrl.ifBlank {
                        if (video.id.isNotBlank()) "https://www.instagram.com/reel/${video.id}/" else "https://www.instagram.com/"
                    }
                    val igIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        setPackage("com.instagram.android")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(igIntent)
                    } catch (_: Exception) {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(fallbackIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open reel in Instagram", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.OpenInBrowser,
                    contentDescription = "Open in Instagram",
                    tint = Color.White,
                    size = 22.dp
                )
            }

            // 3. Share: Shares the reel (.mp4) to the sharesheet
            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.TICK)
                    val file = File(video.filePath)
                    if (file.exists()) {
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Reel").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            Toast.makeText(context, "Failed to share reel", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Video file not found", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Share,
                    contentDescription = "Share reel",
                    tint = Color.White,
                    size = 22.dp
                )
            }

            // 4. Download: Saves the reel to Gallery
            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.CONFIRM)
                    val success = MediaExportUtils.exportVideoToDevice(
                        context = context,
                        sourceFile = File(video.filePath),
                        title = video.title.ifBlank { "Reel_${video.id}" }
                    )
                    if (success) {
                        Toast.makeText(context, "Saved reel to Gallery", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not save reel to Gallery", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Download,
                    contentDescription = "Save to Gallery",
                    tint = Color.White,
                    size = 22.dp
                )
            }

            // 5. Delete: Deletes the reel
            LinkerlyIconButton(
                onClick = {
                    haptics.playHaptic(HapticEffectType.HEAVY_DELETE)
                    onDelete()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                LinkerlyIcon(
                    imageVector = LinkerlyIcons.Buttons.Delete,
                    contentDescription = "Delete reel",
                    tint = Color.White,
                    size = 22.dp
                )
            }
        }

        // Bottom Metadata (moved up by 10px to 82dp)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.74f)
                .padding(start = 16.dp, bottom = 82.dp)
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
