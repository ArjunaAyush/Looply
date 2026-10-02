package com.arjunaayush.looply.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PendingStatus { QUEUED, DONE, EXPIRED, FAILED }

@Entity(tableName = "pending_reels", indices = [Index("status"), Index("shortcode")])
data class PendingReelEntity(
    @PrimaryKey val mediaId: String,
    val shortcode: String?,
    val ownerUsername: String?,
    val caption: String?,
    val progressiveUrl: String?,
    val dashManifest: String?,
    val thumbnailUrl: String?,
    val urlExpiresAtEpochSec: Long?,
    val capturedAtMillis: Long,
    val status: PendingStatus = PendingStatus.QUEUED,
    val attempts: Int = 0,
    val lastError: String? = null,
)
