package com.arjunaayush.looply.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.arjunaayush.looply.core.database.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY created_at DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE author = :author ORDER BY created_at DESC")
    fun getVideosByAuthor(author: String): Flow<List<VideoEntity>>

    @Query("SELECT DISTINCT author FROM videos WHERE author != '' ORDER BY author ASC")
    fun getAllCreators(): Flow<List<String>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Update
    suspend fun updateVideo(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteVideoById(id: String)

    @Query("DELETE FROM videos WHERE is_watched = 1")
    suspend fun deleteWatchedVideos()

    @Query("SELECT * FROM videos ORDER BY created_at DESC")
    suspend fun getAllVideosSync(): List<VideoEntity>

    @Query("DELETE FROM videos")
    suspend fun deleteAllVideos()

    @Query("UPDATE videos SET is_favorite = NOT is_favorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE videos SET is_watched = 1 WHERE id = :id")
    suspend fun markWatched(id: String)
}
