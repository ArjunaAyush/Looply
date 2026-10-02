package com.arjunaayush.looply.feature.feed.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.theme.LooplyPink
import com.arjunaayush.looply.core.util.HapticEffectType
import com.arjunaayush.looply.core.util.HapticsManager
import com.arjunaayush.looply.domain.model.Video
import kotlinx.coroutines.delay
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun ReelPlayerItem(
    video: Video,
    isCurrentPage: Boolean,
    isLooping: Boolean,
    isMuted: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = remember { HapticsManager(context) }
    var showControls by remember { mutableStateOf(true) }
    var showHeartAnimation by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
            val file = File(video.filePath)
            val uri = if (file.exists()) Uri.fromFile(file) else Uri.parse(video.reelUrl)
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(2800)
            showControls = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        haptics.playHaptic(HapticEffectType.FAVORITE_POP)
                        showHeartAnimation = true
                        onToggleFavorite()
                    },
                    onTap = {
                        showControls = !showControls
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Auto-hiding HUD controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                video = video,
                isLooping = isLooping,
                isMuted = isMuted,
                onToggleFavorite = onToggleFavorite,
                onDelete = onDelete
            )
        }

        // Heart burst micro-interaction on double-tap
        if (showHeartAnimation) {
            HeartBurst(
                onAnimationEnd = { showHeartAnimation = false },
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun HeartBurst(
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var animState by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (animState) 1.4f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heartScale"
    )

    LaunchedEffect(Unit) {
        animState = true
        delay(400)
        onAnimationEnd()
    }

    LinkerlyIcon(
        imageVector = LinkerlyIcons.Buttons.FavoriteSelected,
        contentDescription = null,
        tint = LooplyPink,
        size = 80.dp,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale)
    )
}
