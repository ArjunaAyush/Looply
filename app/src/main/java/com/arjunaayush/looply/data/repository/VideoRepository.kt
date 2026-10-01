package com.arjunaayush.looply.data.repository

import android.content.Context
import android.util.Log
import com.arjunaayush.looply.data.local.VideoDatabase
import com.arjunaayush.looply.data.local.VideoEntity
import com.arjunaayush.looply.data.model.Video
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import com.arjunaayush.looply.features.storage.VideoStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val context: Context,
    val storageManager: VideoStorageManager = VideoStorageManager(context),
    private val database: VideoDatabase = VideoDatabase.getInstance(context)
) {

    private val videoDao = database.videoDao()

    init {
        // Run disk-to-database synchronization in background to prevent blocking Main/UI thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncDiskFilesWithDatabase()
            } catch (e: Exception) {
                Log.e("VideoRepository", "Error syncing disk with database: ${e.message}", e)
            }
        }
    }

    /**
     * Checks if a video with the given shortcode is already downloaded.
     */
    fun isVideoAlreadyDownloaded(shortcode: String): Boolean {
        val entities = videoDao.getAll()
        return entities.any { it.title.contains(shortcode) || it.id.contains(shortcode) }
    }

    suspend fun isVideoAlreadyDownloadedAsync(shortcode: String): Boolean = withContext(Dispatchers.IO) {
        isVideoAlreadyDownloaded(shortcode)
    }

    /**
     * Retrieves all saved videos as domain models, sorted newest first.
     */
    fun getAllVideos(): List<Video> {
        val entities = videoDao.getAll()
        return entities.map { it.toDomain() }
    }

    suspend fun getAllVideosAsync(): List<Video> = withContext(Dispatchers.IO) {
        getAllVideos()
    }

    /**
     * Gets the newest/latest video, or null if empty.
     */
    fun getLatestVideo(): Video? {
        return getAllVideos().firstOrNull()
    }

    suspend fun getLatestVideoAsync(): Video? = withContext(Dispatchers.IO) {
        getLatestVideo()
    }

    /**
     * Saves imported video details into both local storage and database.
     */
    fun saveVideo(details: ImportedVideoDetails): Video {
        val entity = VideoEntity(
            id = details.file.nameWithoutExtension,
            title = details.originalName,
            filePath = details.file.absolutePath,
            durationMs = details.durationMs,
            sizeBytes = details.sizeBytes,
            width = details.width,
            height = details.height,
            createdAt = details.file.lastModified()
        )

        videoDao.insert(entity)
        return entity.toDomain()
    }

    suspend fun saveVideoAsync(details: ImportedVideoDetails): Video = withContext(Dispatchers.IO) {
        saveVideo(details)
    }

    /**
     * Deletes a video from both database and local disk.
     */
    fun deleteVideo(video: Video): Boolean {
        videoDao.deleteById(video.id)
        return storageManager.deleteVideo(video.file)
    }

    suspend fun deleteVideoAsync(video: Video): Boolean = withContext(Dispatchers.IO) {
        deleteVideo(video)
    }

    /**
     * Deletes all saved videos from database and disk.
     */
    fun deleteAllVideos() {
        videoDao.deleteAll()
        val files = storageManager.getAllVideos()
        for (f in files) {
            storageManager.deleteVideo(f)
        }
    }

    suspend fun deleteAllVideosAsync() = withContext(Dispatchers.IO) {
        deleteAllVideos()
    }

    /**
     * Ensures any physical reel files on disk are registered in the database.
     */
    private fun syncDiskFilesWithDatabase() {
        storageManager.migrateLegacyVideoIfNeeded()
        val diskFiles = storageManager.getAllVideos()
        val existingEntities = videoDao.getAll()

        for (file in diskFiles) {
            val existing = existingEntities.find { it.filePath == file.absolutePath }
            if (existing == null || existing.durationMs == 0L) {
                var durationMs = existing?.durationMs ?: 0L
                var width = existing?.width ?: 0
                var height = existing?.height ?: 0

                if (durationMs == 0L) {
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
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
                    title = title,
                    filePath = file.absolutePath,
                    durationMs = durationMs,
                    sizeBytes = file.length(),
                    width = width,
                    height = height,
                    createdAt = existing?.createdAt ?: file.lastModified()
                )
                videoDao.insert(entity)
            }
        }
    }
}
