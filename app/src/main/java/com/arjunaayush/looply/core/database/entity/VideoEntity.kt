package com.arjunaayush.looply.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arjunaayush.looply.domain.model.Video

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "reel_url")
    val reelUrl: String = "",
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String = "",
    val title: String = "",
    val author: String = "",
    val caption: String = "",
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    @ColumnInfo(name = "aspect_ratio")
    val aspectRatio: Float = 0.5625f,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_watched")
    val isWatched: Boolean = false,
    @ColumnInfo(name = "watch_count")
    val watchCount: Int = 0
) {
    fun toDomain(): Video = Video(
        id = id,
        reelUrl = reelUrl,
        filePath = filePath,
        thumbnailPath = thumbnailPath,
        title = title,
        author = author,
        caption = caption,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        createdAt = createdAt,
        isFavorite = isFavorite,
        isWatched = isWatched,
        watchCount = watchCount
    )

    companion object {
        fun fromDomain(video: Video): VideoEntity = VideoEntity(
            id = video.id,
            reelUrl = video.reelUrl,
            filePath = video.filePath,
            thumbnailPath = video.thumbnailPath,
            title = video.title,
            author = video.author,
            caption = video.caption,
            durationMs = video.durationMs,
            sizeBytes = video.sizeBytes,
            width = video.width,
            height = video.height,
            aspectRatio = video.aspectRatio,
            createdAt = video.createdAt,
            isFavorite = video.isFavorite,
            isWatched = video.isWatched,
            watchCount = video.watchCount
        )
    }
}
