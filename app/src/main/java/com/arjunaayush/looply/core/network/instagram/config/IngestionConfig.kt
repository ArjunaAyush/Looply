package com.arjunaayush.looply.core.network.instagram.config

import org.json.JSONArray
import org.json.JSONObject

data class IngestionConfig(
    val batchEnabled: Boolean = true,
    val minVersionCode: Int = 1,
    val startUrl: String = "https://www.instagram.com/reels/",
    val capturePatterns: List<String> = listOf("/api/v1/clips/", "/graphql", "/api/v1/feed/", "/api/v1/discover/"),
    val templatePatterns: List<String> = listOf("/api/v1/clips/", "/graphql"),
    val cursorKeys: List<String> = listOf("max_id", "after", "cursor", "end_cursor"),
    val minDelayMs: Long = 2_500,
    val maxDelayMs: Long = 6_000,
    val maxPagesPerRun: Int = 6,
    val dailyPageBudget: Int = 15,
    val dailyReelBudget: Int = 60,
    val initialWaitMs: Long = 25_000,
    val pageTimeoutMs: Long = 30_000,
) {
    fun toJsJson(): String = JSONObject()
        .put("capturePatterns", JSONArray(capturePatterns))
        .put("templatePatterns", JSONArray(templatePatterns))
        .put("cursorKeys", JSONArray(cursorKeys))
        .toString()

    companion object {
        private const val FLOOR_MIN_DELAY_MS = 2_000L
        private const val CEIL_PAGES_PER_RUN = 10
        private const val CEIL_DAILY_PAGES = 30
        private const val CEIL_DAILY_REELS = 150

        /** Remote values can tighten limits but never loosen them past compiled bounds. */
        fun fromJson(json: String, versionCode: Int): IngestionConfig {
            val o = JSONObject(json)
            val d = IngestionConfig()
            fun list(key: String, def: List<String>) = o.optJSONArray(key)
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: def
            val minDelay = o.optLong("minDelayMs", d.minDelayMs).coerceAtLeast(FLOOR_MIN_DELAY_MS)
            return IngestionConfig(
                batchEnabled = o.optBoolean("batchEnabled", true) && versionCode >= o.optInt("minVersionCode", 1),
                minVersionCode = o.optInt("minVersionCode", 1),
                startUrl = o.optString("startUrl", d.startUrl).takeIf { it.startsWith("https://www.instagram.com/") } ?: d.startUrl,
                capturePatterns = list("capturePatterns", d.capturePatterns),
                templatePatterns = list("templatePatterns", d.templatePatterns),
                cursorKeys = list("cursorKeys", d.cursorKeys),
                minDelayMs = minDelay,
                maxDelayMs = o.optLong("maxDelayMs", d.maxDelayMs).coerceAtLeast(minDelay + 500),
                maxPagesPerRun = o.optInt("maxPagesPerRun", d.maxPagesPerRun).coerceIn(1, CEIL_PAGES_PER_RUN),
                dailyPageBudget = o.optInt("dailyPageBudget", d.dailyPageBudget).coerceIn(1, CEIL_DAILY_PAGES),
                dailyReelBudget = o.optInt("dailyReelBudget", d.dailyReelBudget).coerceIn(1, CEIL_DAILY_REELS),
                initialWaitMs = o.optLong("initialWaitMs", d.initialWaitMs).coerceIn(5_000, 60_000),
                pageTimeoutMs = o.optLong("pageTimeoutMs", d.pageTimeoutMs).coerceIn(5_000, 60_000),
            )
        }
    }
}
