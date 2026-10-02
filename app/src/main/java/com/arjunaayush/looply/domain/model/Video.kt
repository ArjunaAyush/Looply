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
    val isWatched: Boolean = false
) {
    val displayAuthor: String
        get() = if (author.isNotBlank()) {
            if (author.startsWith("@")) author else "@$author"
        } else {
            "@creator"
        }
}
