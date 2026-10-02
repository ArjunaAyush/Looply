package com.arjunaayush.looply.data.repository

import com.arjunaayush.looply.core.database.dao.VideoDao
import com.arjunaayush.looply.core.database.entity.VideoEntity
import com.arjunaayush.looply.domain.model.CreatorGroup
import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val videoDao: VideoDao
) : VideoRepository {

    override fun getAllVideos(): Flow<List<Video>> {
        return videoDao.getAllVideos().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getVideosByAuthor(author: String): Flow<List<Video>> {
        return videoDao.getVideosByAuthor(author).map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getAllCreators(): Flow<List<String>> {
        return videoDao.getAllCreators().flowOn(Dispatchers.IO)
    }

    override fun getCreatorGroups(): Flow<List<CreatorGroup>> {
        return videoDao.getAllVideos().map { entities ->
            entities
                .map { it.toDomain() }
                .groupBy { it.displayAuthor }
                .map { (creator, videos) ->
                    CreatorGroup(creatorHandle = creator, videos = videos)
                }
                .sortedByDescending { it.videoCount }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getVideoById(id: String): Video? = withContext(Dispatchers.IO) {
        videoDao.getVideoById(id)?.toDomain()
    }

    override suspend fun saveVideo(video: Video) = withContext(Dispatchers.IO) {
        videoDao.insertVideo(VideoEntity.fromDomain(video))
    }

    override suspend fun deleteVideo(id: String) = withContext(Dispatchers.IO) {
        val existing = videoDao.getVideoById(id)
        if (existing != null) {
            try {
                val file = File(existing.filePath)
                if (file.exists()) {
                    file.delete()
                }
                if (existing.thumbnailPath.isNotBlank()) {
                    val thumbFile = File(existing.thumbnailPath)
                    if (thumbFile.exists()) {
                        thumbFile.delete()
                    }
                }
            } catch (_: Exception) {
                // Ignore file system errors
            }
            videoDao.deleteVideoById(id)
        }
    }

    override suspend fun deleteWatchedVideos() = withContext(Dispatchers.IO) {
        val videos = videoDao.getAllVideos().firstOrNull() ?: emptyList()
        val watched = videos.filter { it.isWatched }
        watched.forEach { entity ->
            try {
                val file = File(entity.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // Continue with DB deletion
            }
        }
        videoDao.deleteWatchedVideos()
    }

    override suspend fun deleteAllVideos() = withContext(Dispatchers.IO) {
        val videos = videoDao.getAllVideos().firstOrNull() ?: emptyList()
        videos.forEach { entity ->
            try {
                val file = File(entity.filePath)
                if (file.exists()) {
                    file.delete()
                }
                if (entity.thumbnailPath.isNotBlank()) {
                    val thumb = File(entity.thumbnailPath)
                    if (thumb.exists()) {
                        thumb.delete()
                    }
                }
            } catch (_: Exception) {
                // Continue with DB deletion
            }
        }
        videoDao.deleteAllVideos()
    }

    override suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        videoDao.toggleFavorite(id)
    }

    override suspend fun markWatched(id: String) = withContext(Dispatchers.IO) {
        videoDao.markWatched(id)
    }

    override suspend fun recordVideoView(id: String) = withContext(Dispatchers.IO) {
        videoDao.recordView(id)
    }

    override fun getStorageUsageBytes(): Flow<Long> {
        return videoDao.getAllVideos().map { entities ->
            entities.sumOf { it.sizeBytes }
        }.flowOn(Dispatchers.IO)
    }
}
