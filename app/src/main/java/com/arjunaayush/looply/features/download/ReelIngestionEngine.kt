package com.arjunaayush.looply.features.download

import android.content.Context
import android.util.Log
import com.arjunaayush.looply.BuildConfig
import com.arjunaayush.looply.core.database.dao.PendingReelDao
import com.arjunaayush.looply.core.database.entity.PendingReelEntity
import com.arjunaayush.looply.core.database.entity.PendingStatus
import com.arjunaayush.looply.core.media.CorruptMediaException
import com.arjunaayush.looply.core.media.DashManifestParser
import com.arjunaayush.looply.core.media.DashMuxer
import com.arjunaayush.looply.core.media.ReelFileDownloader
import com.arjunaayush.looply.core.media.UrlExpiredException
import com.arjunaayush.looply.core.network.instagram.ReelCandidate
import com.arjunaayush.looply.core.network.instagram.capture.CaptureException
import com.arjunaayush.looply.core.network.instagram.capture.WebViewReelSource
import com.arjunaayush.looply.core.network.instagram.config.IngestionConfigRepository
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import com.arjunaayush.looply.features.importvideo.VideoImport
import com.arjunaayush.looply.features.instagram.InstagramDownloader
import com.arjunaayush.looply.features.storage.VideoStorageManager
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
    data object NoTemplate : CaptureResult
    data object SchemaDrift : CaptureResult
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
        var attempts = 0
        var consecutiveDuplicates = 0
        val seenShortcodes = mutableSetOf<String>()

        try {
            source.open(config)

            while (queued < target && guard.hasBudget(config) &&
                attempts < (target * 3).coerceIn(10, 50) &&
                consecutiveDuplicates < 3
            ) {
                attempts++
                val candidate = source.awaitNextReel(config.pageTimeoutMs)

                if (candidate != null) {
                    val code = candidate.shortcode ?: candidate.mediaId
                    val isDuplicate = seenShortcodes.contains(code) ||
                        (candidate.shortcode != null && videoRepository.isVideoAlreadyDownloaded(candidate.shortcode))

                    if (isDuplicate) {
                        consecutiveDuplicates++
                        Log.d("ReelIngestionEngine", "Encountered duplicate reel $code, skipping...")
                    } else {
                        seenShortcodes.add(code)
                        consecutiveDuplicates = 0
                        val added = enqueue(listOf(candidate))
                        if (added > 0) {
                            queued += added
                            onProgress(queued)
                            guard.recordPage()
                        }
                    }
                }

                if (queued >= target) break

                // Jitter delay between 1.5s and 3.0s before loading next reel
                delay(Random.nextLong(1500, 3000))
                source.loadReelsFeed()
            }

            guard.onCleanRun()
            return CaptureResult.Queued(queued, attempts)
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
                // 2. Download video file (progressive, DASH, or resolver fallback)
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
                } else if (!item.shortcode.isNullOrBlank()) {
                    // Safety net fallback: resolve stream via InstagramDownloader
                    val igDownloader = InstagramDownloader(
                        context = context,
                        repository = videoRepository,
                        videoImport = VideoImport(context, VideoStorageManager(context))
                    )
                    val saved = igDownloader.downloadReelSuspend("https://www.instagram.com/reels/${item.shortcode}/")
                    if (saved != null) {
                        pendingDao.mark(item.mediaId, PendingStatus.DONE)
                        downloaded++
                        onProgress(downloaded)
                        continue
                    } else {
                        pendingDao.mark(item.mediaId, PendingStatus.FAILED, attemptDelta = 1, error = "Could not resolve stream")
                        continue
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
}
