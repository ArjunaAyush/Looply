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
    val estimatedSizeBytes: Long = 0L
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

        // 1. Primary: Clips Home (Reels Feed)
        val clipsReels = fetchFromClipsHome(cookies, csrf)
        resultReels.addAll(clipsReels)
        Log.d(TAG, "Fetched ${clipsReels.size} reels from clips/home")

        // 2. Secondary: If more reels needed, query Timeline Feed
        if (resultReels.size < minCount) {
            val timelineReels = fetchFromTimeline(cookies, csrf)
            for (r in timelineReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
            Log.d(TAG, "Total reels after timeline fallback: ${resultReels.size}")
        }

        // 3. Tertiary: Clips Discover/Explore fallback
        if (resultReels.size < minCount) {
            val discoverReels = fetchFromDiscover(cookies, csrf)
            for (r in discoverReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
            Log.d(TAG, "Total reels after discover fallback: ${resultReels.size}")
        }

        Log.d(TAG, "Final algorithm fetch result: ${resultReels.size} unique reels ready for download")
        resultReels
    }

    private fun fetchFromClipsHome(cookies: String, csrf: String): List<FeedReel> {
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/clips/home/",
            "https://i.instagram.com/api/v1/clips/home/"
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                Log.d(TAG, "Attempting POST to clips/home: $endpoint")
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

                val postData = "container_module=clips_viewer_clips_tab".toByteArray(Charsets.UTF_8)
                conn.setRequestProperty("Content-Length", postData.size.toString())
                conn.outputStream.use { it.write(postData) }

                val responseCode = conn.responseCode
                Log.d(TAG, "clips/home ($endpoint) response code: $responseCode")

                if (responseCode in 200..299) {
                    val response = readStream(conn)
                    val reels = parseClipsHomeResponse(response)
                    Log.d(TAG, "clips/home parsed ${reels.size} reels from $endpoint")
                    if (reels.isNotEmpty()) return reels
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "clips/home failed ($endpoint) HTTP $responseCode: ${err.take(200)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed clips/home query on $endpoint: ${e.message}", e)
            } finally {
                conn?.disconnect()
            }
        }
        return emptyList()
    }

    private fun fetchFromTimeline(cookies: String, csrf: String): List<FeedReel> {
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/feed/timeline/",
            "https://i.instagram.com/api/v1/feed/timeline/"
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                Log.d(TAG, "Attempting POST to timeline: $endpoint")
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
                Log.d(TAG, "timeline ($endpoint) response code: $responseCode")

                if (responseCode in 200..299) {
                    val response = readStream(conn)
                    val reels = parseTimelineResponse(response)
                    Log.d(TAG, "timeline parsed ${reels.size} reels from $endpoint")
                    if (reels.isNotEmpty()) return reels
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "timeline failed ($endpoint) HTTP $responseCode: ${err.take(200)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed timeline query on $endpoint: ${e.message}", e)
            } finally {
                conn?.disconnect()
            }
        }
        return emptyList()
    }

    private fun fetchFromDiscover(cookies: String, csrf: String): List<FeedReel> {
        val endpoints = listOf(
            "https://www.instagram.com/api/v1/clips/discover/",
            "https://i.instagram.com/api/v1/clips/discover/"
        )

        for (endpoint in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                Log.d(TAG, "Attempting GET to discover: $endpoint")
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
                    setRequestProperty("Referer", "https://www.instagram.com/explore/")
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "discover ($endpoint) response code: $responseCode")

                if (responseCode in 200..299) {
                    val response = readStream(conn)
                    val reels = parseClipsHomeResponse(response)
                    Log.d(TAG, "discover parsed ${reels.size} reels from $endpoint")
                    if (reels.isNotEmpty()) return reels
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "discover failed ($endpoint) HTTP $responseCode: ${err.take(200)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed discover query on $endpoint: ${e.message}", e)
            } finally {
                conn?.disconnect()
            }
        }
        return emptyList()
    }

    private fun parseClipsHomeResponse(jsonString: String): List<FeedReel> {
        val list = mutableListOf<FeedReel>()
        try {
            val root = JSONObject(jsonString)
            val items = root.optJSONArray("items") ?: return emptyList()

            for (i in 0 until items.length()) {
                val itemObj = items.optJSONObject(i) ?: continue
                val media = itemObj.optJSONObject("media") ?: itemObj
                val parsed = parseMediaObject(media)
                if (parsed != null) {
                    list.add(parsed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing clips response: ${e.message}")
        }
        return list
    }

    private fun parseTimelineResponse(jsonString: String): List<FeedReel> {
        val list = mutableListOf<FeedReel>()
        try {
            val root = JSONObject(jsonString)
            val feedItems = root.optJSONArray("feed_items") ?: return emptyList()

            for (i in 0 until feedItems.length()) {
                val itemObj = feedItems.optJSONObject(i) ?: continue
                val media = itemObj.optJSONObject("media_or_ad") ?: continue
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
            val pk = media.optString("pk").ifBlank { media.optString("id") }
            if (pk.isNotBlank()) {
                shortcode = pkToShortcode(pk)
            }
        }
        if (shortcode.isBlank()) return null

        val id = media.optString("id", shortcode)

        var videoUrl = ""
        val videoVersions = media.optJSONArray("video_versions")
        if (videoVersions != null && videoVersions.length() > 0) {
            val bestVideo = videoVersions.optJSONObject(0)
            videoUrl = bestVideo?.optString("url") ?: ""
        }
        if (videoUrl.isBlank()) {
            videoUrl = media.optString("video_url")
        }
        if (videoUrl.isBlank()) return null

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
            videoUrl = videoUrl
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
