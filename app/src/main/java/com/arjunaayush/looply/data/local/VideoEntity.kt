package com.arjunaayush.looply.data.local

import com.arjunaayush.looply.data.model.Video

data class VideoEntity(
    val id: String,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long
) {
    fun toDomain(): Video {
        return Video(
            id = id,
            title = title,
            filePath = filePath,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(video: Video): VideoEntity {
            return VideoEntity(
                id = video.id,
                title = video.title,
                filePath = video.filePath,
                durationMs = video.durationMs,
                sizeBytes = video.sizeBytes,
                width = video.width,
                height = video.height,
                createdAt = video.createdAt
            )
        }
    }
}
