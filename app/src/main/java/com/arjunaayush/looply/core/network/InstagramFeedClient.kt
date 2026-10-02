package com.arjunaayush.looply.core.network

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.arjunaayush.looply.core.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class FeedReel(
    val id: String,
    val shortcode: String,
    val title: String,
    val creatorHandle: String,
    val creatorName: String,
    val videoUrl: String,
    val estimatedSizeBytes: Long = 0L,
    val thumbnailUrl: String = ""
)

@Singleton
class InstagramFeedClient @Inject constructor(
    @ApplicationContext private val context: Context? = null
) {

    companion object {
        private const val TAG = "InstagramFeedClient"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Instagram 319.0.0.36.108 Android (33/13; 480dpi; 1080x2400; samsung; SM-G998B; p3s; exynos2100; en_US; 570123456)"
        private const val IG_APP_ID = "936619743392459"
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun pkToShortcode(pkStr: String): String {
            val cleanPk = pkStr.split("_")[0]
            var temp = cleanPk.toBigIntegerOrNull() ?: return ""
            val base = BigInteger.valueOf(64)
            val sb = StringBuilder()
            while (temp > BigInteger.ZERO) {
                val rem = temp.mod(base).toInt()
                sb.append(ALPHABET[rem])
                temp = temp.divide(base)
            }
            return sb.reverse().toString()
        }

        fun shortcodeToMediaId(shortcode: String): String {
            val cleanCode = if (shortcode.length > 28) shortcode.substring(0, shortcode.length - 28) else shortcode
            var id = BigInteger.ZERO
            val base = BigInteger.valueOf(64)
            for (ch in cleanCode) {
                val index = ALPHABET.indexOf(ch)
                if (index >= 0) {
                    id = id.multiply(base).add(BigInteger.valueOf(index.toLong()))
                }
            }
            return id.toString()
        }
    }

    /**
     * Extracts active cookies for Instagram from both PreferencesManager and CookieManager.
     */
    fun getInstagramCookies(): String {
        val prefCookies = if (context != null) {
            try {
                PreferencesManager(context).getInstagramCookies()
            } catch (e: Exception) {
                Log.w(TAG, "Could not read cookies from PreferencesManager: ${e.message}")
                ""
            }
        } else ""

        val cm = try {
            CookieManager.getInstance()
        } catch (_: Exception) {
            null
        }

        val c1 = cm?.getCookie("https://www.instagram.com") ?: ""
        val c2 = cm?.getCookie("https://instagram.com") ?: ""
        val c3 = cm?.getCookie("https://m.instagram.com") ?: ""
        val c4 = cm?.getCookie(".instagram.com") ?: ""

        val combined = "$prefCookies; $c1; $c2; $c3; $c4"
        val parts = combined.split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val finalCookies = parts.joinToString("; ")
        Log.d(TAG, "Aggregated Instagram cookies (has sessionid: ${finalCookies.contains("sessionid")})")
        return finalCookies
    }

    /**
     * Extracts CSRF token from cookie string.
     */
    fun extractCsrfToken(cookies: String): String {
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("csrftoken=") }
            ?.substringAfter("csrftoken=") ?: ""
    }

    /**
     * Fetches reels from the user's Instagram feed algorithm.
     * Queries multiple Instagram feed endpoints with robust fallback and logging.
     */
    data class ClipsPageResult(
        val reels: List<FeedReel>,
        val maxId: String = "",
        val pagingToken: String = ""
    )

    private var persistentClipsMaxId: String = ""
    private var persistentClipsPagingToken: String = ""

    /**
     * Fetches reels from the user's Instagram feed algorithm.
     * Queries multiple Instagram feed endpoints with robust multi-page pagination and fallback.
     */
    suspend fun fetchAlgorithmReels(
        minCount: Int = 10,
        cookies: String = getInstagramCookies()
    ): List<FeedReel> = withContext(Dispatchers.IO) {
        if (!cookies.contains("sessionid")) {
            Log.w(TAG, "Cannot fetch algorithm reels: sessionid cookie not found in cookies")
            return@withContext emptyList()
        }

        val resultReels = mutableListOf<FeedReel>()
        val csrf = extractCsrfToken(cookies)
        Log.d(TAG, "Starting algorithm reels fetch (target minCount: $minCount, csrf: ${csrf.take(8)}...)")

        // 1. Primary: Clips Home (Reels Feed) with multi-page pagination
        val clipsReels = fetchFromClipsHome(cookies, csrf, targetCount = minCount)
        resultReels.addAll(clipsReels)
        Log.d(TAG, "Fetched ${clipsReels.size} reels from clips/home")

        // 2. Secondary: If more reels needed, query Explore Clips algorithm
        if (resultReels.size < minCount) {
            val exploreReels = fetchFromClipsExplore(cookies, csrf, targetCount = minCount - resultReels.size)
            for (r in exploreReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
            Log.d(TAG, "Total reels after explore clips: ${resultReels.size}")
        }

        // 3. Tertiary: Explore Popular feed
        if (resultReels.size < minCount) {
            val popularReels = fetchFromExplorePopular(cookies, csrf)
            for (r in popularReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
            Log.d(TAG, "Total reels after explore popular: ${resultReels.size}")
        }

        // 4. Quaternary: Timeline Feed (Reels in user follow stream)
        if (resultReels.size < minCount) {
            val timelineReels = fetchFromTimeline(cookies, csrf)
            for (r in timelineReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
            Log.d(TAG, "Total reels after timeline fallback: ${resultReels.size}")
        }

        Log.d(TAG, "Final algorithm fetch result: ${resultReels.size} unique reels ready for download")
        resultReels
    }

    private fun fetchFromClipsHome(
        cookies: String,
        csrf: String,
        targetCount: Int = 15
    ): List<FeedReel> {
        val collectedReels = mutableListOf<FeedReel>()
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/clips/home/",
            "https://i.instagram.com/api/v1/clips/home/"
        )

        for (endpoint in endpoints) {
            var maxId = persistentClipsMaxId
            var pagingToken = persistentClipsPagingToken
            var page = 0
            val maxPages = 8

            while (collectedReels.size < targetCount && page < maxPages) {
                page++
                var conn: HttpURLConnection? = null
                try {
                    val url = URL(endpoint)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", USER_AGENT)
                        setRequestProperty("Cookie", cookies)
                        setRequestProperty("X-IG-App-ID", IG_APP_ID)
                        setRequestProperty("X-ASBD-ID", "359341")
                        setRequestProperty("X-IG-WWW-Claim", "0")
                        if (csrf.isNotBlank()) setRequestProperty("X-CSRFToken", csrf)
                        setRequestProperty("X-Requested-With", "XMLHttpRequest")
                        setRequestProperty("Origin", "https://www.instagram.com")
                        setRequestProperty("Referer", "https://www.instagram.com/reels/")
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        doOutput = true
                    }

                    val postParams = mutableListOf("container_module=clips_viewer_clips_tab")
                    if (maxId.isNotBlank()) postParams.add("max_id=$maxId")
                    if (pagingToken.isNotBlank()) postParams.add("paging_token=$pagingToken")
                    val postData = postParams.joinToString("&").toByteArray(Charsets.UTF_8)

                    conn.setRequestProperty("Content-Length", postData.size.toString())
                    conn.outputStream.use { it.write(postData) }

                    val responseCode = conn.responseCode
                    Log.d(TAG, "clips/home page $page ($endpoint) response code: $responseCode")

                    if (responseCode in 200..299) {
                        val response = readStream(conn)
                        val pageResult = parseClipsHomeResponse(response)
                        val newReels = pageResult.reels.filter { r -> collectedReels.none { it.shortcode == r.shortcode } }
                        collectedReels.addAll(newReels)
                        Log.d(TAG, "clips/home page $page fetched ${newReels.size} new reels (total: ${collectedReels.size})")

                        maxId = pageResult.maxId
                        pagingToken = pageResult.pagingToken
                        if (newReels.isEmpty() && maxId.isBlank() && pagingToken.isBlank()) {
                            break
                        }
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed clips/home page $page query on $endpoint: ${e.message}", e)
                    break
                } finally {
                    conn?.disconnect()
                }
            }

            if (collectedReels.isNotEmpty()) {
                persistentClipsMaxId = maxId
                persistentClipsPagingToken = pagingToken
                return collectedReels
            }
        }
        return collectedReels
    }

    private fun fetchFromClipsExplore(
        cookies: String,
        csrf: String,
        targetCount: Int = 10
    ): List<FeedReel> {
        val collectedReels = mutableListOf<FeedReel>()
        val endpoint = "https://www.instagram.com/api/v1/clips/home/"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cookie", cookies)
                setRequestProperty("X-IG-App-ID", IG_APP_ID)
                setRequestProperty("X-ASBD-ID", "359341")
                setRequestProperty("X-IG-WWW-Claim", "0")
                if (csrf.isNotBlank()) setRequestProperty("X-CSRFToken", csrf)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Origin", "https://www.instagram.com")
                setRequestProperty("Referer", "https://www.instagram.com/explore/")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                doOutput = true
            }

            val postData = "container_module=clips_viewer_explore".toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", postData.size.toString())
            conn.outputStream.use { it.write(postData) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = readStream(conn)
                val pageResult = parseClipsHomeResponse(response)
                collectedReels.addAll(pageResult.reels)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed explore clips query: ${e.message}", e)
        } finally {
            conn?.disconnect()
        }
        return collectedReels
    }



    private fun fetchFromExplorePopular(
        cookies: String,
        csrf: String
    ): List<FeedReel> {
        val collectedReels = mutableListOf<FeedReel>()
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/explore/popular/?is_prefetch=false",
            "https://i.instagram.com/api/v1/explore/popular/?is_prefetch=false"
        )
        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Cookie", cookies)
                    setRequestProperty("X-IG-App-ID", IG_APP_ID)
                    setRequestProperty("X-ASBD-ID", "359341")
                    setRequestProperty("X-IG-WWW-Claim", "0")
                    if (csrf.isNotBlank()) setRequestProperty("X-CSRFToken", csrf)
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Origin", "https://www.instagram.com")
                    setRequestProperty("Referer", "https://www.instagram.com/explore/")
                }
                if (conn.responseCode in 200..299) {
                    val response = readStream(conn)
                    val reels = parseExplorePopularResponse(response)
                    collectedReels.addAll(reels)
                    if (collectedReels.isNotEmpty()) return collectedReels
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed explore/popular on $endpoint: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return collectedReels
    }

    private fun parseExplorePopularResponse(jsonString: String): List<FeedReel> {
        val list = mutableListOf<FeedReel>()
        try {
            val root = JSONObject(jsonString)
            val items = root.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val itemObj = items.optJSONObject(i) ?: continue
                    val media = itemObj.optJSONObject("media")
                        ?: itemObj.optJSONObject("media_or_ad")
                        ?: itemObj
                    val parsed = parseMediaObject(media)
                    if (parsed != null) list.add(parsed)
                }
            }
            val sections = root.optJSONArray("sectional_items")
            if (sections != null) {
                for (s in 0 until sections.length()) {
                    val sec = sections.optJSONObject(s) ?: continue
                    val layout = sec.optJSONObject("layout_content") ?: continue
                    val medias = layout.optJSONArray("medias")
                        ?: layout.optJSONArray("fill_items")
                    if (medias != null) {
                        for (m in 0 until medias.length()) {
                            val medObj = medias.optJSONObject(m) ?: continue
                            val media = medObj.optJSONObject("media")
                                ?: medObj.optJSONObject("media_or_ad")
                                ?: medObj
                            val parsed = parseMediaObject(media)
                            if (parsed != null) list.add(parsed)
                        }
                    }
                    val twoByTwo = layout.optJSONObject("two_by_two_item")
                    if (twoByTwo != null) {
                        val media = twoByTwo.optJSONObject("media")
                            ?: twoByTwo.optJSONObject("media_or_ad")
                            ?: twoByTwo
                        val parsed = parseMediaObject(media)
                        if (parsed != null) list.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing explore popular response: ${e.message}")
        }
        return list
    }

    private fun fetchFromTimeline(cookies: String, csrf: String): List<FeedReel> {
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/feed/timeline/",
            "https://i.instagram.com/api/v1/feed/timeline/"
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Cookie", cookies)
                    setRequestProperty("X-IG-App-ID", IG_APP_ID)
                    setRequestProperty("X-ASBD-ID", "359341")
                    setRequestProperty("X-IG-WWW-Claim", "0")
                    if (csrf.isNotBlank()) setRequestProperty("X-CSRFToken", csrf)
                    setRequestProperty("X-Requested-With", "XMLHttpRequest")
                    setRequestProperty("Origin", "https://www.instagram.com")
                    setRequestProperty("Referer", "https://www.instagram.com/")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                }

                val postData = "feed_view_mode=reels".toByteArray(Charsets.UTF_8)
                conn.setRequestProperty("Content-Length", postData.size.toString())
                conn.outputStream.use { it.write(postData) }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val response = readStream(conn)
                    val reels = parseTimelineResponse(response)
                    if (reels.isNotEmpty()) return reels
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed timeline query on $endpoint: ${e.message}", e)
            } finally {
                conn?.disconnect()
            }
        }
        return emptyList()
    }

    private fun parseClipsHomeResponse(jsonString: String): ClipsPageResult {
        val list = mutableListOf<FeedReel>()
        var maxId = ""
        var pagingToken = ""
        try {
            val root = JSONObject(jsonString)
            maxId = root.optString("max_id")
                .ifBlank { root.optString("next_max_id") }
                .ifBlank { root.optJSONObject("paging_info")?.optString("max_id") ?: "" }
                .ifBlank { root.optJSONObject("paging_info")?.optString("next_max_id") ?: "" }
                .ifBlank { root.optJSONObject("pagination")?.optString("next_max_id") ?: "" }
                .ifBlank { root.optJSONObject("pagination")?.optString("max_id") ?: "" }

            pagingToken = root.optString("paging_token")
                .ifBlank { root.optJSONObject("paging_info")?.optString("paging_token") ?: "" }
                .ifBlank { root.optJSONObject("pagination")?.optString("paging_token") ?: "" }

            val items = root.optJSONArray("items")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("tray")

            if (items != null) {
                for (i in 0 until items.length()) {
                    val itemObj = items.optJSONObject(i) ?: continue
                    val media = itemObj.optJSONObject("media")
                        ?: itemObj.optJSONObject("media_or_ad")
                        ?: itemObj.optJSONObject("clip")?.optJSONObject("media")
                        ?: itemObj.optJSONObject("clip")
                        ?: itemObj.optJSONObject("clips")
                        ?: itemObj.optJSONObject("item")
                        ?: itemObj
                    val parsed = parseMediaObject(media)
                    if (parsed != null) {
                        list.add(parsed)
                    }
                }

                if (maxId.isBlank() && items.length() > 0) {
                    for (k in items.length() - 1 downTo 0) {
                        val itm = items.optJSONObject(k) ?: continue
                        val m = itm.optJSONObject("media")
                            ?: itm.optJSONObject("media_or_ad")
                            ?: itm.optJSONObject("clip")
                            ?: itm
                        val candidatePk = m.opt("pk")?.toString()?.ifBlank { null }
                            ?: m.opt("id")?.toString()?.ifBlank { null }
                        if (!candidatePk.isNullOrBlank()) {
                            maxId = candidatePk
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing clips response: ${e.message}")
        }
        return ClipsPageResult(list, maxId, pagingToken)
    }

    private fun parseTimelineResponse(jsonString: String): List<FeedReel> {
        val list = mutableListOf<FeedReel>()
        try {
            val root = JSONObject(jsonString)
            val feedItems = root.optJSONArray("feed_items") ?: return emptyList()

            for (i in 0 until feedItems.length()) {
                val itemObj = feedItems.optJSONObject(i) ?: continue
                val media = itemObj.optJSONObject("media_or_ad")
                    ?: itemObj.optJSONObject("media")
                    ?: continue
                val parsed = parseMediaObject(media)
                if (parsed != null) {
                    list.add(parsed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing timeline response: ${e.message}")
        }
        return list
    }

    fun parseMediaObject(media: JSONObject): FeedReel? {
        var shortcode = media.optString("code")
        if (shortcode.isBlank()) {
            shortcode = media.optString("shortcode")
        }
        if (shortcode.isBlank()) {
            val pk = media.opt("pk")?.toString()?.ifBlank { null }
                ?: media.opt("id")?.toString()
                ?: ""
            if (pk.isNotBlank()) {
                shortcode = pkToShortcode(pk)
            }
        }
        if (shortcode.isBlank()) return null

        val id = media.optString("id", shortcode)

        var videoUrl = ""
        val videoVersions = media.optJSONArray("video_versions")
            ?: media.optJSONObject("clips_metadata")?.optJSONArray("video_versions")
            ?: media.optJSONObject("video_metadata")?.optJSONArray("video_versions")
        if (videoVersions != null && videoVersions.length() > 0) {
            val bestVideo = videoVersions.optJSONObject(0)
            videoUrl = bestVideo?.optString("url") ?: ""
        }
        if (videoUrl.isBlank()) {
            videoUrl = media.optString("video_url")
        }
        if (videoUrl.isBlank()) {
            videoUrl = media.optString("progressive_download_url")
        }
        if (videoUrl.isBlank()) {
            val carousel = media.optJSONArray("carousel_media")
            if (carousel != null && carousel.length() > 0) {
                for (j in 0 until carousel.length()) {
                    val carItem = carousel.optJSONObject(j) ?: continue
                    val carVersions = carItem.optJSONArray("video_versions")
                    if (carVersions != null && carVersions.length() > 0) {
                        videoUrl = carVersions.optJSONObject(0)?.optString("url") ?: ""
                        if (videoUrl.isNotBlank()) break
                    }
                }
            }
        }
        if (videoUrl.isBlank()) return null
        videoUrl = videoUrl.replace("\\u0026", "&").replace("&amp;", "&")

        var thumbnailUrl = ""
        val imageVersions = media.optJSONObject("image_versions2")
        val candidates = imageVersions?.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            thumbnailUrl = candidates.optJSONObject(0)?.optString("url") ?: ""
        }
        if (thumbnailUrl.isBlank()) {
            thumbnailUrl = media.optString("display_url")
        }
        thumbnailUrl = thumbnailUrl.replace("\\u0026", "&").replace("&amp;", "&")

        val userObj = media.optJSONObject("user")
        val creatorHandle = userObj?.optString("username", "creator") ?: "creator"
        val creatorName = userObj?.optString("full_name", creatorHandle) ?: creatorHandle

        val captionObj = media.optJSONObject("caption")
        val title = captionObj?.optString("text", "Reel by @$creatorHandle") ?: "Reel by @$creatorHandle"

        return FeedReel(
            id = id,
            shortcode = shortcode,
            title = title.take(120),
            creatorHandle = creatorHandle,
            creatorName = creatorName,
            videoUrl = videoUrl,
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun readStream(conn: HttpURLConnection): String {
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val sb = StringBuilder()
        var line: String? = reader.readLine()
        while (line != null) {
            sb.append(line)
            line = reader.readLine()
        }
        reader.close()
        return sb.toString()
    }
}
