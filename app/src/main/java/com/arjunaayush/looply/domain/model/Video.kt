package com.arjunaayush.looply.domain.model

data class Video(
    val id: String,
    val reelUrl: String = "",
    val filePath: String,
    val thumbnailPath: String = "",
    val title: String = "",
    val author: String = "",
    val caption: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val aspectRatio: Float = 0.5625f,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false,
    val watchCount: Int = 0
) {
    val displayAuthor: String
        get() = if (author.isNotBlank()) {
            if (author.startsWith("@")) author else "@$author"
        } else {
            "@creator"
        }

    val file: java.io.File
        get() = java.io.File(filePath)

    val uri: android.net.Uri
        get() = android.net.Uri.fromFile(file)

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
