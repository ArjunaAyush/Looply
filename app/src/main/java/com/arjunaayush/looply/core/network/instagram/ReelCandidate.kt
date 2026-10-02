package com.arjunaayush.looply.core.network.instagram

data class ReelCandidate(
    val mediaId: String,              // numeric pk; stable across all endpoints
    val shortcode: String?,           // "code"; used for dedupe with share-imported videos
    val ownerUsername: String?,
    val caption: String?,
    val durationSec: Double?,
    val progressiveUrl: String?,
    val dashManifest: String?,
    val thumbnailUrl: String?,
    val width: Int,
    val height: Int,
    val urlExpiresAtEpochSec: Long?,  // parsed from the CDN "oe" param (hex epoch)
)

data class NormalizedPage(
    val items: List<ReelCandidate>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val rawLength: Int,
)
