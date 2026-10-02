package com.arjunaayush.looply.core.network

import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
class InstagramFeedClient @Inject constructor() {

    companion object {
        private const val TAG = "InstagramFeedClient"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val IG_APP_ID = "936619743392459"
    }

    /**
     * Extracts active cookies for Instagram from system CookieManager.
     */
    fun getInstagramCookies(): String {
        val cm = CookieManager.getInstance()
        val c1 = cm.getCookie("https://www.instagram.com") ?: ""
        val c2 = cm.getCookie("https://instagram.com") ?: ""
        val c3 = cm.getCookie(".instagram.com") ?: ""
        val parts = "$c1; $c2; $c3".split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return parts.joinToString("; ")
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
     * Queries the Reels Home endpoint with fallback to Timeline and Discover.
     */
    suspend fun fetchAlgorithmReels(
        minCount: Int = 10,
        cookies: String = getInstagramCookies()
    ): List<FeedReel> = withContext(Dispatchers.IO) {
        if (!cookies.contains("sessionid")) {
            Log.w(TAG, "Cannot fetch reels: sessionid cookie not found")
            return@withContext emptyList()
        }

        val resultReels = mutableListOf<FeedReel>()
        val csrf = extractCsrfToken(cookies)

        // 1. Primary: Clips Home (Reels Feed)
        val clipsReels = fetchFromClipsHome(cookies, csrf)
        resultReels.addAll(clipsReels)

        // 2. Secondary: If more reels needed, query Timeline Feed
        if (resultReels.size < minCount) {
            val timelineReels = fetchFromTimeline(cookies, csrf)
            for (r in timelineReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
        }

        // 3. Tertiary: Clips Discover/Explore fallback
        if (resultReels.size < minCount) {
            val discoverReels = fetchFromDiscover(cookies, csrf)
            for (r in discoverReels) {
                if (resultReels.none { it.shortcode == r.shortcode }) {
                    resultReels.add(r)
                }
            }
        }

        Log.d(TAG, "Successfully fetched ${resultReels.size} algorithm reels from Instagram feed")
        resultReels
    }

    private fun fetchFromClipsHome(cookies: String, csrf: String): List<FeedReel> {
        return try {
            val url = URL("https://www.instagram.com/api/v1/clips/home/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cookie", cookies)
                setRequestProperty("X-IG-App-ID", IG_APP_ID)
                setRequestProperty("X-CSRFToken", csrf)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Referer", "https://www.instagram.com/reels/")
                doOutput = true
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = readStream(conn)
                parseClipsHomeResponse(response)
            } else {
                Log.w(TAG, "clips/home responded with HTTP $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query clips/home: ${e.message}")
            emptyList()
        }
    }

    private fun fetchFromTimeline(cookies: String, csrf: String): List<FeedReel> {
        return try {
            val url = URL("https://www.instagram.com/api/v1/feed/timeline/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cookie", cookies)
                setRequestProperty("X-IG-App-ID", IG_APP_ID)
                setRequestProperty("X-CSRFToken", csrf)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Referer", "https://www.instagram.com/")
                doOutput = true
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = readStream(conn)
                parseTimelineResponse(response)
            } else {
                Log.w(TAG, "feed/timeline responded with HTTP $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query feed/timeline: ${e.message}")
            emptyList()
        }
    }

    private fun fetchFromDiscover(cookies: String, csrf: String): List<FeedReel> {
        return try {
            val url = URL("https://www.instagram.com/api/v1/clips/discover/")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cookie", cookies)
                setRequestProperty("X-IG-App-ID", IG_APP_ID)
                setRequestProperty("X-CSRFToken", csrf)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("Referer", "https://www.instagram.com/explore/")
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = readStream(conn)
                parseClipsHomeResponse(response)
            } else {
                Log.w(TAG, "clips/discover responded with HTTP $responseCode")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query clips/discover: ${e.message}")
            emptyList()
        }
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

    private fun parseMediaObject(media: JSONObject): FeedReel? {
        val shortcode = media.optString("code").takeIf { it.isNotBlank() } ?: return null
        val id = media.optString("id", shortcode)

        val videoVersions = media.optJSONArray("video_versions") ?: return null
        if (videoVersions.length() == 0) return null

        // Select the primary high-quality video URL
        val bestVideo = videoVersions.optJSONObject(0) ?: return null
        val videoUrl = bestVideo.optString("url").takeIf { it.isNotBlank() } ?: return null

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
