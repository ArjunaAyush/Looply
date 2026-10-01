package com.arjunaayush.looply.data.model

import android.net.Uri
import java.io.File

data class Video(
    val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val file: File
        get() = File(filePath)

    val uri: Uri
        get() = Uri.fromFile(file)

    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                "%.2f GB".format(mb / 1024.0)
            } else if (mb >= 1.0) {
                "%.1f MB".format(mb)
            } else {
                "${sizeBytes / 1024} KB"
            }
        }

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
