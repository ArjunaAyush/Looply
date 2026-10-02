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
import com.arjunaayush.looply.core.preferences.PreferencesManager
import com.arjunaayush.looply.core.util.IngestionLogger
import com.arjunaayush.looply.data.repository.VideoRepository
import com.arjunaayush.looply.features.importvideo.ImportedVideoDetails
import com.arjunaayush.looply.features.importvideo.VideoImport
import com.arjunaayush.looply.features.instagram.InstagramDownloader
import com.arjunaayush.looply.features.storage.VideoStorageManager
import com.arjunaayush.looply.utils.ThumbnailLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

sealed interface BatchIngestionResult {
    data class Success(val downloadedCount: Int, val downloadedBytes: Long, val message: String) : BatchIngestionResult
    data class Stopped(val downloadedCount: Int, val downloadedBytes: Long, val reason: String) : BatchIngestionResult
    data class Failed(val error: String) : BatchIngestionResult
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
    private val preferencesManager: PreferencesManager,
) {

    /**
     * High-speed parallel batch ingestion:
     * - Spawns 3 parallel WebView capture workers to discover reels simultaneously.
     * - Runs 2 concurrent download workers to stream and mux files directly to disk.
     * - Pipelined: download starts as soon as the first reel is found.
     * - Stops only when target MB is met, cache limit is reached, or user cancels.
     */
    suspend fun ingestBatch(
        targetMb: Int,
        isCancelled: () -> Boolean = { false },
        onProgress: (downloadedCount: Int, targetCount: Int, downloadedMb: Int, status: String) -> Unit = { _, _, _, _ -> }
    ): BatchIngestionResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.FEED_INGESTION) {
            return@withContext BatchIngestionResult.Failed("Disabled")
        }

        val savedCookies = preferencesManager.getInstagramCookies()
        if (savedCookies.isBlank()) {
            IngestionLogger.log("BatchWorker", "No Instagram session found. Please log in first.")
            return@withContext BatchIngestionResult.Failed("NOT_LOGGED_IN")
        }

        val config = configRepository.current()
        val cacheLimitBytes = preferencesManager.getCacheLimitBytes()
        val currentStorage = videoRepository.storageManager.getTotalStorageUsedBytes()

        if (currentStorage >= cacheLimitBytes) {
            val limitMb = (cacheLimitBytes / (1024 * 1024)).toInt()
            IngestionLogger.log("BatchWorker", "Cache limit reached ($limitMb MB). Aborting download.")
            return@withContext BatchIngestionResult.Stopped(0, 0L, "Cache limit reached")
        }

        val targetBytes = targetMb * 1024L * 1024L
        val targetCount = (targetMb / 7).coerceAtLeast(1)

        IngestionLogger.log("BatchWorker", "Starting parallel ingestion: target $targetMb MB (~$targetCount reels)")

        val seenShortcodes = ConcurrentHashMap.newKeySet<String>()
        val downloadedCount = AtomicInteger(0)
        val downloadedBytes = AtomicLong(0)
        val stopSignal = AtomicBoolean(false)
        val workChannel = Channel<PendingReelEntity>(capacity = 100)

        // Seed with any previously queued pending items
        val pendingExisting = pendingDao.nextQueued(targetCount)
        for (item in pendingExisting) {
            item.shortcode?.let { seenShortcodes.add(it) }
            seenShortcodes.add(item.mediaId)
            workChannel.trySend(item)
        }
        if (pendingExisting.isNotEmpty()) {
            IngestionLogger.log("BatchWorker", "Enqueued ${pendingExisting.size} previously pending reels for download")
        }

        val storageDir = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
        val thumbDir = File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }

        coroutineScope {
            val captureJobs = mutableListOf<Job>()
            val numCaptureWorkers = 3

            // 1. Launch 3 parallel capture workers
            for (workerId in 1..numCaptureWorkers) {
                val job = launch {
                    if (workerId > 1) {
                        delay((workerId - 1) * 750L) // Staggered startup
                    }
                    var source: WebViewReelSource? = null
                    try {
                        withContext(Dispatchers.Main) {
                            source = sourceProvider.get().apply { this.workerId = workerId }
                            source?.open(config)
                        }

                        var consecutiveTimeouts = 0
                        while (!stopSignal.get() && !isCancelled() && downloadedBytes.get() < targetBytes) {
                            if (videoRepository.storageManager.getTotalStorageUsedBytes() >= cacheLimitBytes) {
                                IngestionLogger.log("Worker-$workerId", "Cache limit reached. Halting.")
                                stopSignal.set(true)
                                break
                            }

                            val candidate = try {
                                source?.awaitNextReel(config.pageTimeoutMs)
                            } catch (e: CaptureException) {
                                IngestionLogger.log("Worker-$workerId", "Capture exception: ${e.code}")
                                if (e.code == "CHALLENGE" || e.code == "SESSION_EXPIRED") {
                                    stopSignal.set(true)
                                    break
                                }
                                null
                            }

                            if (candidate != null) {
                                consecutiveTimeouts = 0
                                val code = candidate.shortcode ?: candidate.mediaId
                                val alreadySaved = candidate.shortcode != null && videoRepository.isVideoAlreadyDownloaded(candidate.shortcode)

                                if (!seenShortcodes.add(code) || alreadySaved) {
                                    IngestionLogger.log("Worker-$workerId", "Skipping duplicate reel: $code")
                                } else {
                                    val entity = enqueueSingleCandidate(candidate)
                                    if (entity != null) {
                                        workChannel.send(entity)
                                        IngestionLogger.log("Worker-$workerId", "Found fresh reel $code -> sent to downloader")
                                    }
                                }
                            } else {
                                consecutiveTimeouts++
                                if (consecutiveTimeouts >= 4) {
                                    delay(2000L)
                                }
                            }

                            if (stopSignal.get() || isCancelled() || downloadedBytes.get() >= targetBytes) break

                            // Jitter between 1.0s and 2.2s before requesting next reel
                            delay(Random.nextLong(1000, 2200))
                            source?.loadReelsFeed()
                        }
                    } catch (e: Exception) {
                        IngestionLogger.log("Worker-$workerId", "Worker stopped: ${e.message}")
                    } finally {
                        withContext(Dispatchers.Main) {
                            try {
                                source?.close()
                            } catch (_: Exception) {}
                        }
                        IngestionLogger.log("Worker-$workerId", "Worker finished")
                    }
                }
                captureJobs.add(job)
            }

            // 2. Launch 2 concurrent download workers
            val numDownloaders = 2
            val downloadJobs = (1..numDownloaders).map { downloaderId ->
                launch(Dispatchers.IO) {
                    while (!stopSignal.get() && !isCancelled() && downloadedBytes.get() < targetBytes) {
                        if (videoRepository.storageManager.getTotalStorageUsedBytes() >= cacheLimitBytes) {
                            IngestionLogger.log("Downloader-$downloaderId", "Cache limit reached. Stopping.")
                            stopSignal.set(true)
                            break
                        }

                        val item = withTimeoutOrNull(2000L) {
                            workChannel.receiveCatching().getOrNull()
                        } ?: continue

                        try {
                            IngestionLogger.log("Downloader-$downloaderId", "Downloading reel ${item.shortcode ?: item.mediaId}...")
                            val (savedFile, thumbPath) = downloadItem(item, storageDir, thumbDir)
                            if (savedFile != null && savedFile.exists() && savedFile.length() > 0) {
                                val length = savedFile.length()
                                val total = downloadedBytes.addAndGet(length)
                                val count = downloadedCount.incrementAndGet()
                                val mb = (total / (1024 * 1024)).toInt()

                                pendingDao.mark(item.mediaId, PendingStatus.DONE)
                                IngestionLogger.log(
                                    "Downloader-$downloaderId",
                                    "Saved reel ${item.shortcode ?: item.mediaId} (${length / (1024 * 1024)} MB) [Total: $count reels, $mb MB]"
                                )
                                onProgress(count, targetCount, mb, "Downloaded $count reels ($mb MB)")

                                if (total >= targetBytes) {
                                    IngestionLogger.log("Downloader-$downloaderId", "Target $targetMb MB reached!")
                                    stopSignal.set(true)
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            IngestionLogger.log("Downloader-$downloaderId", "Download error for ${item.mediaId}: ${e.message}")
                            pendingDao.mark(item.mediaId, PendingStatus.FAILED, attemptDelta = 1, error = e.message)
                        }
                    }
                }
            }

            // 3. Monitor cancellation and target status
            while (!stopSignal.get() && !isCancelled() && downloadedBytes.get() < targetBytes) {
                if (videoRepository.storageManager.getTotalStorageUsedBytes() >= cacheLimitBytes) {
                    stopSignal.set(true)
                    break
                }
                delay(500L)
            }

            stopSignal.set(true)
            captureJobs.forEach { it.cancel() }
            workChannel.close()
            downloadJobs.forEach { it.join() }
        }

        val finalCount = downloadedCount.get()
        val finalBytes = downloadedBytes.get()
        val finalMb = (finalBytes / (1024 * 1024)).toInt()

        return@withContext if (isCancelled()) {
            IngestionLogger.log("BatchWorker", "Batch download cancelled by user. Saved $finalCount reels ($finalMb MB)")
            BatchIngestionResult.Stopped(finalCount, finalBytes, "Download stopped by user")
        } else if (videoRepository.storageManager.getTotalStorageUsedBytes() >= cacheLimitBytes) {
            IngestionLogger.log("BatchWorker", "Cache limit reached. Saved $finalCount reels ($finalMb MB)")
            BatchIngestionResult.Stopped(finalCount, finalBytes, "Cache limit reached")
        } else {
            IngestionLogger.log("BatchWorker", "Batch download complete: $finalCount reels saved ($finalMb MB)")
            BatchIngestionResult.Success(finalCount, finalBytes, "Successfully saved $finalCount reels ($finalMb MB)")
        }
    }

    private suspend fun downloadItem(
        item: PendingReelEntity,
        storageDir: File,
        thumbDir: File
    ): Pair<File?, String> {
        val targetFile = File(storageDir, "${item.mediaId}.mp4")

        // 1. Download video file
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
            val igDownloader = InstagramDownloader(
                context = context,
                repository = videoRepository,
                videoImport = VideoImport(context, VideoStorageManager(context))
            )
            val saved = igDownloader.downloadReelSuspend("https://www.instagram.com/reels/${item.shortcode}/")
            if (saved != null) {
                return Pair(File(saved.filePath), saved.thumbnailPath)
            } else {
                throw Exception("Could not resolve stream via shortcode fallback")
            }
        } else {
            throw Exception("No stream URL available")
        }

        // 2. Generate / Save thumbnail
        val thumbFile = File(thumbDir, "${item.mediaId}.jpg")
        var thumbPath = ""
        val generated = ThumbnailLoader.extractAndSaveThumbnail(targetFile, thumbFile)
        if (generated && thumbFile.exists() && thumbFile.length() > 0) {
            thumbPath = thumbFile.absolutePath
        } else if (!item.thumbnailUrl.isNullOrBlank()) {
            try {
                val conn = URL(item.thumbnailUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 10000
                if (conn.responseCode == 200) {
                    conn.inputStream.use { input ->
                        thumbFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    if (thumbFile.exists() && thumbFile.length() > 0) {
                        thumbPath = thumbFile.absolutePath
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Save to VideoRepository
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

        return Pair(targetFile, thumbPath)
    }

    private suspend fun enqueueSingleCandidate(c: ReelCandidate): PendingReelEntity? {
        val now = System.currentTimeMillis()
        val entity = PendingReelEntity(
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
        val ids = pendingDao.insertAll(listOf(entity))
        return if (ids.isNotEmpty() && ids[0] != -1L) entity else null
    }

    // Preserved for backwards compatibility with AutoDownloadWorker and tests
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
                consecutiveDuplicates < 5
            ) {
                attempts++
                val candidate = source.awaitNextReel(config.pageTimeoutMs)

                if (candidate != null) {
                    val code = candidate.shortcode ?: candidate.mediaId
                    val isDuplicate = seenShortcodes.contains(code) ||
                        (candidate.shortcode != null && videoRepository.isVideoAlreadyDownloaded(candidate.shortcode))

                    if (isDuplicate) {
                        consecutiveDuplicates++
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

    // Preserved for backwards compatibility with AutoDownloadWorker
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
            val expiresAt = item.urlExpiresAtEpochSec
            if (expiresAt != null && expiresAt < nowSec + 300) {
                pendingDao.mark(item.mediaId, PendingStatus.EXPIRED, error = "CDN url expired")
                continue
            }

            try {
                val (file, _) = downloadItem(item, storageDir, thumbDir)
                if (file != null) {
                    pendingDao.mark(item.mediaId, PendingStatus.DONE)
                    downloaded++
                    onProgress(downloaded)
                }
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
