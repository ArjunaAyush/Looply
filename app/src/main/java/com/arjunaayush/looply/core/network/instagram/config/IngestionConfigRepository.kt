package com.arjunaayush.looply.core.network.instagram.config

import android.content.Context
import com.arjunaayush.looply.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IngestionConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("ingestion_config", Context.MODE_PRIVATE)

    suspend fun current(): IngestionConfig = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastFetch = prefs.getLong(KEY_LAST_FETCH, 0L)
        if (now - lastFetch > REFRESH_INTERVAL_MS) {
            refreshRemote(now)
        }
        val cached = prefs.getString(KEY_CACHED_JSON, null)
        if (!cached.isNullOrBlank()) {
            runCatching {
                IngestionConfig.fromJson(cached, BuildConfig.VERSION_CODE)
            }.getOrNull() ?: IngestionConfig()
        } else {
            IngestionConfig()
        }
    }

    private fun refreshRemote(now: Long) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(REMOTE_CONFIG_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                // Validate json
                IngestionConfig.fromJson(text, BuildConfig.VERSION_CODE)
                prefs.edit()
                    .putString(KEY_CACHED_JSON, text)
                    .putLong(KEY_LAST_FETCH, now)
                    .apply()
            }
        } catch (_: Exception) {
            // Keep existing cache on failure
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val KEY_CACHED_JSON = "cached_json"
        private const val KEY_LAST_FETCH = "last_fetch_millis"
        private const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000 // 6 hours
        private const val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/arjunaayush/looply/main/config/ingestion.json"
    }
}
