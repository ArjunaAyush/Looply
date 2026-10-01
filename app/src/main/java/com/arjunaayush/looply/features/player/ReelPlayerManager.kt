package com.arjunaayush.looply.features.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class ReelPlayerManager(private val context: Context) {

    var player: ExoPlayer? = null
        private set

    private var activePlayerView: PlayerView? = null

    init {
        initPlayer()
    }

    private fun initPlayer() {
        if (player != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE // Seamless continuous looping
            setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        }
    }

    /**
     * Attaches the managed ExoPlayer instance to a specific PlayerView.
     */
    fun attachTo(playerView: PlayerView) {
        if (activePlayerView != playerView) {
            activePlayerView?.player = null
            activePlayerView = playerView
            playerView.player = player
        }
    }

    /**
     * Plays a video file from local storage.
     */
    fun playVideo(file: File) {
        if (!file.exists()) return

        if (player == null) {
            initPlayer()
            activePlayerView?.let { attachTo(it) }
        }

        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    /**
     * Toggles between play and pause. Returns the new playing state.
     */
    fun togglePlayPause(): Boolean {
        val p = player ?: return false
        return if (p.isPlaying) {
            p.pause()
            false
        } else {
            p.play()
            true
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        if (player?.playbackState == Player.STATE_READY) {
            player?.play()
        }
    }

    fun release() {
        activePlayerView?.player = null
        activePlayerView = null
        player?.release()
        player = null
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }

    fun addListener(listener: Player.Listener) {
        player?.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        player?.removeListener(listener)
    }
}
