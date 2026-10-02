package com.arjunaayush.looply.core.network.instagram

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object ReelJsonNormalizer {

    private const val MAX_DEPTH = 48
    private const val MAX_VIDEO_HEIGHT = 1920
    private val CURSOR_KEYS = listOf("next_max_id", "max_id", "end_cursor", "paging_token")
    private val HAS_MORE_KEYS = listOf("more_available", "has_next_page")
    private val AD_KEYS = listOf("ad_id", "ad_action", "injected", "ad_header_style")
    private val OE_REGEX = Regex("[?&]oe=([0-9A-Fa-f]+)")
    private const val ANTI_JSON_PREFIX = "for (;;);"

    fun parse(raw: String): NormalizedPage {
        val found = LinkedHashMap<String, ReelCandidate>()
        val state = WalkState()
        splitDocuments(raw).forEach { walk(it, 0, found, state) }
        return NormalizedPage(
            items = found.values.toList(),
            nextCursor = state.cursor,
            hasMore = state.hasMore ?: (state.cursor != null),
            rawLength = raw.length,
        )
    }

    /**
     * Paging keys can also appear deep inside media objects (e.g. comment previews carry their own
     * next_max_id). The feed-level cursor is the one closest to the root, so the shallowest match wins.
     */
    private class WalkState {
        var cursor: String? = null
        var cursorDepth = Int.MAX_VALUE
        var hasMore: Boolean? = null
        var hasMoreDepth = Int.MAX_VALUE
    }

    /**
     * Reads every top-level JSON value in sequence. Handles plain JSON, the "for (;;);" prefix,
     * and newline-delimited multi-document (@defer / streamed) responses. Note: org.json's
     * JSONObject(String) silently ignores trailing documents, so it must not be used here.
     */
    internal fun splitDocuments(raw: String): List<Any> {
        val text = raw.trim().removePrefix(ANTI_JSON_PREFIX)
        val tokener = JSONTokener(text)
        val docs = mutableListOf<Any>()
        runCatching {
            while (true) {
                if (tokener.nextClean() == '\u0000') break
                tokener.back()
                val value = tokener.nextValue()
                if (value is JSONObject || value is JSONArray) docs += value
            }
        }
        return docs
    }

    private fun walk(node: Any?, depth: Int, out: MutableMap<String, ReelCandidate>, state: WalkState) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1, out, state)
            is JSONObject -> {
                if (isAd(node)) return
                toCandidate(node)?.let { out.putIfAbsent(it.mediaId, it) }
                collectPaging(node, depth, state)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1, out, state)
            }
        }
    }

    private fun isAd(o: JSONObject): Boolean = AD_KEYS.any { o.has(it) && !o.isNull(it) }

    private fun collectPaging(o: JSONObject, depth: Int, state: WalkState) {
        if (depth < state.cursorDepth) {
            CURSOR_KEYS.firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() && it != "null" } }
                ?.let { state.cursor = it; state.cursorDepth = depth }
        }
        if (depth < state.hasMoreDepth) {
            HAS_MORE_KEYS.firstOrNull { o.has(it) }?.let { k ->
                state.hasMore = o.optBoolean(k, true)
                state.hasMoreDepth = depth
            }
        }
    }

    internal fun toCandidate(o: JSONObject): ReelCandidate? {
        val versions = o.optJSONArray("video_versions")
        val dash = o.optString("video_dash_manifest").takeIf { it.isNotBlank() && it != "null" }
        if ((versions == null || versions.length() == 0) && dash == null) return null

        val mediaId = o.optString("pk").takeIf { it.isNotBlank() }
            ?: o.optString("id").substringBefore('_').takeIf { it.isNotBlank() }
            ?: return null

        val best = versions?.let(::pickBestVersion)
        val url = best?.optString("url")?.takeIf { it.isNotBlank() }

        return ReelCandidate(
            mediaId = mediaId,
            shortcode = o.optString("code").takeIf { it.isNotBlank() },
            ownerUsername = (o.optJSONObject("user") ?: o.optJSONObject("owner"))
                ?.optString("username")?.takeIf { it.isNotBlank() },
            caption = o.optJSONObject("caption")?.optString("text")?.takeIf { it.isNotBlank() },
            durationSec = o.optDouble("video_duration", Double.NaN).takeUnless { it.isNaN() },
            progressiveUrl = url,
            dashManifest = dash,
            thumbnailUrl = o.optJSONObject("image_versions2")?.optJSONArray("candidates")
                ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() },
            width = best?.optInt("width") ?: o.optInt("original_width"),
            height = best?.optInt("height") ?: o.optInt("original_height"),
            urlExpiresAtEpochSec = url?.let { OE_REGEX.find(it)?.groupValues?.get(1)?.toLongOrNull(16) },
        )
    }

    private fun pickBestVersion(arr: JSONArray): JSONObject? {
        val all = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        return all.filter { it.optInt("height") in 1..MAX_VIDEO_HEIGHT }
            .maxByOrNull { it.optInt("width") * it.optInt("height") }
            ?: all.firstOrNull()
    }
}
