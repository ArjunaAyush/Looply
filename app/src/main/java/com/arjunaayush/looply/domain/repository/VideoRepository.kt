package com.arjunaayush.looply.domain.repository

import com.arjunaayush.looply.domain.model.CreatorGroup
import com.arjunaayush.looply.domain.model.Video
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getAllVideos(): Flow<List<Video>>
    fun getVideosByAuthor(author: String): Flow<List<Video>>
    fun getAllCreators(): Flow<List<String>>
    fun getCreatorGroups(): Flow<List<CreatorGroup>>
    suspend fun getVideoById(id: String): Video?
    suspend fun saveVideo(video: Video)
    suspend fun deleteVideo(id: String)
    suspend fun deleteWatchedVideos()
    suspend fun deleteAllVideos()
    suspend fun toggleFavorite(id: String)
    suspend fun markWatched(id: String)
    suspend fun recordVideoView(id: String)
    fun getStorageUsageBytes(): Flow<Long>
}
