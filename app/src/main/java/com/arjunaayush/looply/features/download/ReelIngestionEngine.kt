package com.arjunaayush.looply.features.download

import android.content.Context
import com.arjunaayush.looply.BuildConfig
import com.arjunaayush.looply.core.database.dao.PendingReelDao
import com.arjunaayush.looply.core.database.entity.PendingReelEntity
import com.arjunaayush.looply.core.database.entity.PendingStatus
import com.arjunaayush.looply.core.media.CorruptMediaException
import com.arjunaayush.looply.core.media.DashManifestParser
import com.arjunaayush.looply.core.media.DashMuxer
import com.arjunaayush.looply.core.media.ReelFileDownloader
import com.arjunaayush.looply.core.media.UrlExpiredException
import com.arjunaayush.looply.core.network.instagram.NormalizedPage
import com.arjunaayush.looply.core.network.instagram.ReelCandidate
import com.arjunaayush.looply.core.network.instagram.ReelJsonNormalizer
import com.arjunaayush.looply.core.network.instagram.ResponseClassifier
import com.arjunaayush.looply.core.network.instagram.ResponseVerdict
import com.arjunaayush.looply.core.network.instagram.capture.CaptureException
import com.arjunaayush.looply.core.network.instagram.capture.WebViewReelSource
import com.arjunaayush.looply.core.network.instagram.config.IngestionConfigRepository
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import com.arjunaayush.looply.utils.ThumbnailLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed interface CaptureResult {
    data class Queued(val newItems: Int, val pages: Int) : CaptureResult
    data class Blocked(val reason: BlockReason) : CaptureResult
    data object NotLoggedIn : CaptureResult
    data object NeedsVerification : CaptureResult
    data object RateLimited : CaptureResult
    data object NoTemplate : CaptureResult          // first page worked, pagination template not found
    data object SchemaDrift : CaptureResult         // feed JSON arrived but zero reels parsed
    data class Failed(val code: String) : CaptureResult
}

@Singleton
class ReelIngestionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceProvider: Provider<WebViewReelSource>,
    private val configRepository: IngestionConfigRepository,
    private val guard: IngestionGuard,
    private val pendingDao: PendingReelDao,
    private val videoRepository: VideoRepository,
    private val downloader: ReelFileDownloader,
) {

    suspend fun capture(target: Int, onProgress: (queued: Int) -> Unit = {}): CaptureResult {
        if (!BuildConfig.FEED_INGESTION) return CaptureResult.Blocked(BlockReason.DisabledRemotely)
        val config = configRepository.current()
        guard.blockReason(config)?.let { return CaptureResult.Blocked(it) }

        val source = sourceProvider.get()
        var queued = 0
        var pages = 0
        try {
            source.open(config)
            val initial = source.awaitInitial(config.initialWaitMs)
            var cursor: String? = null
            var hasMore = true
            var sawFeedJson = false
            var parsed = 0
            for (p in initial) {
                val page = ReelJsonNormalizer.parse(p.body)
                sawFeedJson = sawFeedJson || page.rawLength > SCHEMA_DRIFT_MIN_BYTES
                parsed += page.items.size
                queued += enqueue(page.items)
                if (page.nextCursor != null) { cursor = page.nextCursor; hasMore = page.hasMore }
            }
            onProgress(queued)
            if (initial.isNotEmpty() && parsed == 0 && sawFeedJson) {
                return CaptureResult.SchemaDrift
            }
            if (!source.hasTemplate) return if (queued > 0) CaptureResult.Queued(queued, 0) else CaptureResult.NoTemplate

            var dryPages = 0
            while (queued < target && hasMore && cursor != null &&
                pages < config.maxPagesPerRun && guard.hasBudget(config)
            ) {
                delay(Random.nextLong(config.minDelayMs, config.maxDelayMs))
                val resp = source.requestPage(cursor, config.pageTimeoutMs)
                pages++
                guard.recordPage()

                when (ResponseClassifier.classify(resp.status, resp.body)) {
                    ResponseVerdict.RATE_LIMITED -> { guard.onRateLimited(); return CaptureResult.RateLimited }
                    ResponseVerdict.CHALLENGE -> { guard.onChallenge(); return CaptureResult.NeedsVerification }
                    ResponseVerdict.SESSION_EXPIRED -> { guard.onSessionExpired(); return CaptureResult.NotLoggedIn }
                    ResponseVerdict.TRANSIENT -> break
                    ResponseVerdict.OK -> Unit
                }

                val page: NormalizedPage = ReelJsonNormalizer.parse(resp.body)
                if (page.items.isEmpty() && page.rawLength > SCHEMA_DRIFT_MIN_BYTES) return CaptureResult.SchemaDrift

                val added = enqueue(page.items + source.drainBacklogPayloads().flatMap { ReelJsonNormalizer.parse(it.body).items })
                queued += added
                onProgress(queued)
                dryPages = if (added == 0) dryPages + 1 else 0
                if (dryPages >= 2) break                       // feed is repeating itself; stop politely
                cursor = page.nextCursor
                hasMore = page.hasMore
            }
            guard.onCleanRun()
            return CaptureResult.Queued(queued, pages)
        } catch (e: CaptureException) {
            return when (e.code) {
                "SESSION_EXPIRED" -> { guard.onSessionExpired(); CaptureResult.NotLoggedIn }
                "CHALLENGE" -> { guard.onChallenge(); CaptureResult.NeedsVerification }
                else -> CaptureResult.Failed(e.code)
            }
        } finally {
            source.close()
        }
    }

    suspend fun downloadQueued(
        limit: Int,
        onProgress: (downloaded: Int) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        val storageDir = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val queuedItems = pendingDao.nextQueued(limit)
        var downloaded = 0
        val nowSec = System.currentTimeMillis() / 1000

        for (item in queuedItems) {
            // 1. Check expiration (skip and mark EXPIRED if within 5 min)
            val expiresAt = item.urlExpiresAtEpochSec
            if (expiresAt != null && expiresAt < nowSec + 300) {
                pendingDao.mark(item.mediaId, PendingStatus.EXPIRED, error = "CDN url expired")
                continue
            }

            val targetFile = File(storageDir, "${item.mediaId}.mp4")

            try {
                // 2. Download video file (progressive or DASH)
                if (!item.progressiveUrl.isNullOrBlank()) {
                    downloader.download(item.progressiveUrl, targetFile)
                } else if (!item.dashManifest.isNullOrBlank()) {
                    val tracks = DashManifestParser.parse(item.dashManifest)
                        ?: throw CorruptMediaException("Failed to parse DASH manifest")
                    val vFile = File(storageDir, "${item.mediaId}.v.part")
                    val aFile = if (tracks.audioUrl != null) File(storageDir, "${item.mediaId}.a.part") else null
                    try {
                        downloader.download(tracks.videoUrl, vFile)
                        if (tracks.audioUrl != null && aFile != null) {
                            downloader.download(tracks.audioUrl, aFile)
                        }
                        DashMuxer.mux(vFile, aFile, targetFile)
                    } finally {
                        vFile.delete()
                        aFile?.delete()
                    }
                } else {
                    pendingDao.mark(item.mediaId, PendingStatus.FAILED, attemptDelta = 1, error = "No stream url available")
                    continue
                }

                // 3. Generate thumbnail
                val thumbFile = File(thumbDir, "${item.mediaId}.jpg")
                var thumbPath = ""
                if (ThumbnailLoader.extractAndSaveThumbnail(targetFile, thumbFile)) {
                    thumbPath = thumbFile.absolutePath
                }

                // 4. Save video details into Room
                val details = ImportedVideoDetails(
                    file = targetFile,
                    originalName = targetFile.name,
                    durationMs = 0L,
                    width = 720,
                    height = 1280,
                    sizeBytes = targetFile.length(),
                )
                val reelUrl = if (!item.shortcode.isNullOrBlank()) "https://www.instagram.com/reel/${item.shortcode}/" else ""
                videoRepository.saveVideo(
                    details = details,
                    reelUrl = reelUrl,
                    author = item.ownerUsername ?: "Instagram Creator",
                    caption = item.caption ?: "",
                    thumbnailPath = thumbPath,
                )

                // 5. Mark as DONE
                pendingDao.mark(item.mediaId, PendingStatus.DONE)
                downloaded++
                onProgress(downloaded)

            } catch (e: UrlExpiredException) {
                pendingDao.mark(item.mediaId, PendingStatus.EXPIRED, error = e.message)
            } catch (e: Exception) {
                val attempts = item.attempts + 1
                val newStatus = if (attempts >= 3) PendingStatus.FAILED else PendingStatus.QUEUED
                pendingDao.mark(item.mediaId, newStatus, attemptDelta = 1, error = e.message)
            }
        }

        downloaded
    }

    private suspend fun enqueue(items: List<ReelCandidate>): Int {
        val now = System.currentTimeMillis()
        val fresh = items
            .distinctBy { it.mediaId }
            .filter { c -> c.shortcode == null || !videoRepository.isVideoAlreadyDownloaded(c.shortcode) }
            .map { c ->
                PendingReelEntity(
                    mediaId = c.mediaId,
                    shortcode = c.shortcode,
                    ownerUsername = c.ownerUsername,
                    caption = c.caption,
                    progressiveUrl = c.progressiveUrl,
                    dashManifest = c.dashManifest,
                    thumbnailUrl = c.thumbnailUrl,
                    urlExpiresAtEpochSec = c.urlExpiresAtEpochSec,
                    capturedAtMillis = now,
                )
            }
        if (fresh.isEmpty()) return 0
        val inserted = pendingDao.insertAll(fresh).count { it != -1L }
        guard.recordReels(inserted)
        return inserted
    }

    private companion object {
        const val SCHEMA_DRIFT_MIN_BYTES = 5_000
    }
}
