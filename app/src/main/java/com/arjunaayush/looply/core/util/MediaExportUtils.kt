package com.arjunaayush.looply.core.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object MediaExportUtils {
    private const val TAG = "MediaExportUtils"

    /**
     * Exports an offline video loop to the user's public device storage (Movies/Looply).
     * Scoped-storage compliant across Android 9 through Android 15+.
     */
    fun exportVideoToDevice(context: Context, sourceFile: File, title: String): Boolean {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            Log.e(TAG, "Source video file does not exist: ${sourceFile.absolutePath}")
            return false
        }

        val cleanTitle = title
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .take(50)
            .ifBlank { "Looply_Reel" }
        val filename = "${cleanTitle}_${System.currentTimeMillis()}.mp4"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Looply")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return false

                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                Log.d(TAG, "Successfully exported video to MediaStore: $uri")
                true
            } else {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "Looply"
                )
                if (!moviesDir.exists()) moviesDir.mkdirs()
                val destFile = File(moviesDir, filename)

                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                Log.d(TAG, "Successfully exported video to legacy storage: ${destFile.absolutePath}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export video to device: ${e.message}", e)
            false
        }
    }
}
