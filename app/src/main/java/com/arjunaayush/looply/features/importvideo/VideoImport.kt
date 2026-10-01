package com.arjunaayush.looply.features.importvideo

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.arjunaayush.looply.features.storage.VideoStorageManager
import com.arjunaayush.looply.utils.Constants
import java.io.File

data class ImportedVideoDetails(
    val file: File,
    val originalName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

class VideoImport(
    private val context: Context,
    private val storageManager: VideoStorageManager
) {

    /**
     * Creates the Intent to open the Android system document picker.
     */
    fun createPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Constants.MIME_TYPE_VIDEO_ALL
        }
    }

    /**
     * Imports a video from the selected Uri, saves it to internal storage,
     * and extracts its metadata (duration, resolution, original filename).
     */
    fun importVideo(uri: Uri, customName: String? = null): ImportedVideoDetails {
        val originalName = customName ?: queryFileName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
        val savedFile = storageManager.saveVideo(uri)

        var durationMs = 0L
        var width = 0
        var height = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.fromFile(savedFile))
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            // Graceful fallback if video metadata cannot be read
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return ImportedVideoDetails(
            file = savedFile,
            originalName = originalName,
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = savedFile.length()
        )
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: File(uri.path ?: "").name
        }
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }
}
