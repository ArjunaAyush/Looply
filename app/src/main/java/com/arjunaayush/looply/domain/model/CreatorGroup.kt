package com.arjunaayush.looply.domain.model

data class CreatorGroup(
    val creatorHandle: String,
    val videos: List<Video>,
    val videoCount: Int = videos.size
)
