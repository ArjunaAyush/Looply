package com.arjunaayush.looply.features.storage

import android.content.Context
import android.net.Uri
import com.arjunaayush.looply.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val videosDir: File by lazy {
        File(context.filesDir, Constants.VIDEOS_DIRECTORY_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun getVideosDirectory(): File = videosDir

    /**
     * Copies a selected video from Uri into app-internal storage.
     * Uses a unique timestamp filename so previous videos are never overwritten.
     */
    fun saveVideo(uri: Uri): File {
        val fileName = "reel_${System.currentTimeMillis()}.mp4"
        val destinationFile = File(videosDir, fileName)

        val inputStream = if (uri.scheme == "file") {
            val filePath = uri.path
            val file = if (filePath != null) File(filePath) else null
            if (file != null && file.exists()) {
                java.io.FileInputStream(file)
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } else {
            context.contentResolver.openInputStream(uri)
        }

        inputStream?.use { input ->
            FileOutputStream(destinationFile).use { outputStream ->
                input.copyTo(outputStream)
            }
        } ?: throw Exception("Failed to open video stream from selected source")

        return destinationFile
    }

    /**
     * Returns all saved video files, sorted newest first.
     */
    fun getAllVideos(): List<File> {
        val files = videosDir.listFiles { file ->
            file.isFile && Constants.SUPPORTED_VIDEO_EXTENSIONS.any { ext ->
                file.extension.equals(ext, ignoreCase = true)
            }
        } ?: return emptyList()

        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * Calculates total bytes used by all saved videos.
     */
    fun getTotalStorageUsedBytes(): Long {
        return getAllVideos().sumOf { it.length() }
    }

    /**
     * Returns the latest saved video, or null if no videos exist.
     */
    fun getLatestVideo(): File? {
        return getAllVideos().firstOrNull()
    }

    /**
     * Deletes a specific video file from storage.
     */
    fun deleteVideo(file: File): Boolean {
        return if (file.exists()) file.delete() else false
    }

    /**
     * Migrates the legacy single video if it exists from earlier steps.
     */
    fun migrateLegacyVideoIfNeeded() {
        val legacyFile = File(context.filesDir, Constants.LEGACY_VIDEO_FILE_NAME)
        if (legacyFile.exists() && legacyFile.length() > 0) {
            val migrated = File(videosDir, "reel_legacy_${legacyFile.lastModified()}.mp4")
            if (!migrated.exists()) {
                legacyFile.renameTo(migrated)
            } else {
                legacyFile.delete()
            }
        }
    }
}
