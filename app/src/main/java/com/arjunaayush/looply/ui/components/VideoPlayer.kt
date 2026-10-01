package com.arjunaayush.looply.ui.components

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class VideoPlayer(private val context: Context) {

    var exoPlayer: ExoPlayer? = null
        private set

    val playerView: PlayerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(Color.TRANSPARENT)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun play(uri: Uri, loop: Boolean = true) {
        release()

        val newPlayer = ExoPlayer.Builder(context).build().apply {
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }

        exoPlayer = newPlayer
        playerView.player = newPlayer
    }

    fun togglePlayPause(): Boolean {
        val player = exoPlayer ?: return false
        return if (player.isPlaying) {
            player.pause()
            false
        } else {
            player.play()
            true
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        if (exoPlayer?.playbackState == Player.STATE_READY) {
            exoPlayer?.play()
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        playerView.player = null
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying == true
    }

    fun setOnTapListener(onTap: (isPlaying: Boolean) -> Unit) {
        playerView.setOnClickListener {
            val playing = togglePlayPause()
            onTap(playing)
        }
    }
}
