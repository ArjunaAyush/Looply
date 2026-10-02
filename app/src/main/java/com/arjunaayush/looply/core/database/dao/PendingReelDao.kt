package com.arjunaayush.looply.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arjunaayush.looply.core.database.entity.PendingReelEntity
import com.arjunaayush.looply.core.database.entity.PendingStatus

@Dao
interface PendingReelDao {
    /** Returns rowId per item, or -1 where the mediaId already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PendingReelEntity>): List<Long>

    @Query("SELECT * FROM pending_reels WHERE status = 'QUEUED' ORDER BY capturedAtMillis LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<PendingReelEntity>

    @Query("UPDATE pending_reels SET status = :status, attempts = attempts + :attemptDelta, lastError = :error WHERE mediaId = :mediaId")
    suspend fun mark(mediaId: String, status: PendingStatus, attemptDelta: Int = 0, error: String? = null)

    @Query("SELECT COUNT(*) FROM pending_reels WHERE status = 'QUEUED'")
    suspend fun queuedCount(): Int

    @Query("DELETE FROM pending_reels WHERE status != 'QUEUED' AND capturedAtMillis < :before")
    suspend fun pruneFinished(before: Long)
}
