package com.arjunaayush.looply.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.arjunaayush.looply.core.database.VideoDatabase
import com.arjunaayush.looply.core.database.entity.VideoEntity
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import com.arjunaayush.looply.features.storage.VideoStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unified VideoRepository connecting background download workers to the Room database.
 * Directly updates Room database so Jetpack Compose UI (Reels and Saved screens) reacts automatically.
 */
class VideoRepository(
    private val context: Context,
    val storageManager: VideoStorageManager = VideoStorageManager(context),
    private val database: VideoDatabase = VideoDatabase.getInstance(context)
) {

    private val videoDao = database.videoDao()
    private val TAG = "VideoRepository"

    init {
        // Run disk-to-database synchronization in background to rescue existing reel files
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncDiskFilesWithDatabase()
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing disk with database: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if a video with the given shortcode is already downloaded in Room.
     */
    suspend fun isVideoAlreadyDownloaded(shortcode: String): Boolean = withContext(Dispatchers.IO) {
        val all = videoDao.getAllVideosSync()
        all.any { it.id.contains(shortcode) || it.reelUrl.contains(shortcode) || it.title.contains(shortcode) }
    }

    suspend fun isVideoAlreadyDownloadedAsync(shortcode: String): Boolean =
        isVideoAlreadyDownloaded(shortcode)

    /**
     * Retrieves all saved videos as domain models, sorted newest first.
     */
    suspend fun getAllVideos(): List<Video> = withContext(Dispatchers.IO) {
        videoDao.getAllVideosSync().map { it.toDomain() }
    }

    suspend fun getAllVideosAsync(): List<Video> = getAllVideos()

    /**
     * Saves imported video details into Room and returns the domain Video.
     */
    suspend fun saveVideo(
        details: ImportedVideoDetails,
        reelUrl: String = "",
        author: String = "",
        caption: String = ""
    ): Video = withContext(Dispatchers.IO) {
        val id = details.file.nameWithoutExtension
        val entity = VideoEntity(
            id = id,
            reelUrl = reelUrl,
            filePath = details.file.absolutePath,
            title = details.originalName,
            author = author,
            caption = caption,
            durationMs = details.durationMs,
            sizeBytes = details.sizeBytes,
            width = details.width,
            height = details.height,
            aspectRatio = if (details.height > 0) details.width.toFloat() / details.height else 0.5625f,
            createdAt = details.file.lastModified()
        )
        videoDao.insertVideo(entity)
        Log.d(TAG, "Successfully inserted video into Room: $id (${details.originalName})")
        entity.toDomain()
    }

    suspend fun saveVideoAsync(details: ImportedVideoDetails): Video = saveVideo(details)

    /**
     * Deletes a video from both Room and local disk.
     */
    suspend fun deleteVideo(video: Video): Boolean = withContext(Dispatchers.IO) {
        videoDao.deleteVideoById(video.id)
        storageManager.deleteVideo(video.file)
    }

    suspend fun deleteVideoAsync(video: Video): Boolean = deleteVideo(video)

    /**
     * Deletes all saved videos from Room and disk.
     */
    suspend fun deleteAllVideos() = withContext(Dispatchers.IO) {
        videoDao.deleteAllVideos()
        val files = storageManager.getAllVideos()
        for (f in files) {
            storageManager.deleteVideo(f)
        }
    }

    suspend fun deleteAllVideosAsync() = deleteAllVideos()

    /**
     * Ensures any physical reel files on disk are registered in the Room database.
     */
    private suspend fun syncDiskFilesWithDatabase() = withContext(Dispatchers.IO) {
        storageManager.migrateLegacyVideoIfNeeded()
        val diskFiles = storageManager.getAllVideos()
        val existingEntities = videoDao.getAllVideosSync()

        for (file in diskFiles) {
            val existing = existingEntities.find { it.filePath == file.absolutePath || it.id == file.nameWithoutExtension }
            if (existing == null || existing.durationMs == 0L) {
                var durationMs = existing?.durationMs ?: 0L
                var width = existing?.width ?: 0
                var height = existing?.height ?: 0

                if (durationMs == 0L) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        retriever.release()
                    } catch (_: Exception) {}
                }

                val title = if (existing != null && existing.title.isNotBlank() && !existing.title.startsWith("reel_")) {
                    existing.title
                } else {
                    file.nameWithoutExtension
                }

                val entity = VideoEntity(
                    id = file.nameWithoutExtension,
                    reelUrl = existing?.reelUrl ?: "",
                    filePath = file.absolutePath,
                    thumbnailPath = existing?.thumbnailPath ?: "",
                    title = title,
                    author = existing?.author ?: "",
                    caption = existing?.caption ?: "",
                    durationMs = durationMs,
                    sizeBytes = file.length(),
                    width = width,
                    height = height,
                    aspectRatio = if (height > 0) width.toFloat() / height else 0.5625f,
                    createdAt = existing?.createdAt ?: file.lastModified()
                )
                videoDao.insertVideo(entity)
                Log.d(TAG, "Synced physical file into Room: ${file.name}")
            }
        }
    }
}
