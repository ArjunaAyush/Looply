package com.arjunaayush.looply.core.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object DashMuxer {

    fun mux(video: File, audio: File?, out: File) {
        val extractors = listOfNotNull(video, audio).map { f -> MediaExtractor().apply { setDataSource(f.path) } }
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val tracks = extractors.map { ex ->
                val idx = (0 until ex.trackCount).first { i ->
                    val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                    mime.startsWith("video/") || mime.startsWith("audio/")
                }
                ex.selectTrack(idx)
                ex to muxer.addTrack(ex.getTrackFormat(idx))
            }
            muxer.start()
            started = true

            val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            for ((ex, track) in tracks) {
                while (true) {
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.set(
                        0, size, ex.sampleTime,
                        if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                    )
                    muxer.writeSampleData(track, buffer, info)
                    ex.advance()
                }
            }
        } finally {
            if (started) runCatching { muxer.stop() }
            muxer.release()
            extractors.forEach { it.release() }
        }
    }
}
