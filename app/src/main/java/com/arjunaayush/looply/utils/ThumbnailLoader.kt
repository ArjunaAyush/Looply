package com.arjunaayush.looply.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/**
 * High-performance, memory-efficient video thumbnail loader.
 * Uses an in-memory LRU cache and background thread pool with native MediaMetadataRetriever.
 */
object ThumbnailLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024) // 1/8th of heap in KB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Asynchronously loads a video frame thumbnail into [targetView].
     * Recycles previous tags safely and caches resulting scaled bitmaps.
     */
    fun loadThumbnail(file: File, targetView: ImageView, width: Int = 180, height: Int = 180) {
        val path = file.absolutePath
        val cached = memoryCache.get(path)

        if (cached != null) {
            targetView.setImageBitmap(cached)
            return
        }

        // Reset image and tag to prevent image flashing when views are recycled
        targetView.setImageBitmap(null)
        targetView.tag = path

        executor.execute {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(path)

                // Try fetching frame at 0.5s for a representative frame
                val frame = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime

                if (frame != null) {
                    val scaled = Bitmap.createScaledBitmap(frame, width, height, true)
                    memoryCache.put(path, scaled)

                    mainHandler.post {
                        if (targetView.tag == path) {
                            targetView.setImageBitmap(scaled)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore extraction failures for corrupt/unsupported files
            } finally {
                try {
                    retriever?.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
