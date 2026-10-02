package com.arjunaayush.looply.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("looply_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_INSTAGRAM_LOGGED_IN = "ig_logged_in"
        private const val KEY_INSTAGRAM_USERNAME = "ig_username"
        private const val KEY_INSTAGRAM_COOKIES = "ig_cookies"
        private const val KEY_AUTO_DOWNLOAD_WIFI = "auto_download_wifi"
        private const val KEY_CACHE_LIMIT_INDEX = "cache_limit_index"
        private const val KEY_AUTO_DELETE_24H = "auto_delete_24h"
        private const val KEY_INSTANT_BATCH_MB = "instant_batch_mb"
        private const val KEY_INFINITE_LOOP = "infinite_loop_default"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_AMBIENT_MODE = "ambient_mode_enabled"
        private const val KEY_SHAKE_TO_SHUFFLE = "shake_to_shuffle_enabled"

        val CACHE_LIMIT_OPTIONS = listOf(
            "300 MB",
            "500 MB",
            "1 GB",
            "2 GB",
            "3 GB",
            "5 GB"
        )
    }

    private val _isInstagramLoggedIn = MutableStateFlow(prefs.getBoolean(KEY_INSTAGRAM_LOGGED_IN, false))
    val isInstagramLoggedIn: StateFlow<Boolean> = _isInstagramLoggedIn.asStateFlow()

    private val _instagramUsername = MutableStateFlow(prefs.getString(KEY_INSTAGRAM_USERNAME, "") ?: "")
    val instagramUsername: StateFlow<String> = _instagramUsername.asStateFlow()

    private val _autoDownloadOnWifi = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, true))
    val autoDownloadOnWifi: StateFlow<Boolean> = _autoDownloadOnWifi.asStateFlow()

    private val _cacheLimitIndex = MutableStateFlow(prefs.getInt(KEY_CACHE_LIMIT_INDEX, 1)) // default 500 MB
    val cacheLimitIndex: StateFlow<Int> = _cacheLimitIndex.asStateFlow()

    private val _autoDeleteWatchedAfter24h = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DELETE_24H, true))
    val autoDeleteWatchedAfter24h: StateFlow<Boolean> = _autoDeleteWatchedAfter24h.asStateFlow()

    private val _instantBatchSizeMb = MutableStateFlow(prefs.getInt(KEY_INSTANT_BATCH_MB, 200)) // default 200 MB
    val instantBatchSizeMb: StateFlow<Int> = _instantBatchSizeMb.asStateFlow()

    private val _infiniteLoopDefault = MutableStateFlow(prefs.getBoolean(KEY_INFINITE_LOOP, true))
    val infiniteLoopDefault: StateFlow<Boolean> = _infiniteLoopDefault.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _ambientModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_AMBIENT_MODE, true))
    val ambientModeEnabled: StateFlow<Boolean> = _ambientModeEnabled.asStateFlow()

    private val _shakeToShuffleEnabled = MutableStateFlow(prefs.getBoolean(KEY_SHAKE_TO_SHUFFLE, true))
    val shakeToShuffleEnabled: StateFlow<Boolean> = _shakeToShuffleEnabled.asStateFlow()

    fun setInstagramLogin(loggedIn: Boolean, username: String, cookies: String = "") {
        val editor = prefs.edit()
            .putBoolean(KEY_INSTAGRAM_LOGGED_IN, loggedIn)
            .putString(KEY_INSTAGRAM_USERNAME, username)
        if (cookies.isNotBlank()) {
            editor.putString(KEY_INSTAGRAM_COOKIES, cookies)
        } else if (!loggedIn) {
            editor.remove(KEY_INSTAGRAM_COOKIES)
        }
        editor.apply()
        _isInstagramLoggedIn.value = loggedIn
        _instagramUsername.value = username
    }

    fun setInstagramUsername(username: String) {
        prefs.edit().putString(KEY_INSTAGRAM_USERNAME, username).apply()
        _instagramUsername.value = username
    }

    fun getInstagramCookies(): String {
        return prefs.getString(KEY_INSTAGRAM_COOKIES, "") ?: ""
    }

    fun setAutoDownloadOnWifi(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_WIFI, enabled).apply()
        _autoDownloadOnWifi.value = enabled
    }

    fun setCacheLimitIndex(index: Int) {
        val clamped = index.coerceIn(0, CACHE_LIMIT_OPTIONS.size - 1)
        prefs.edit().putInt(KEY_CACHE_LIMIT_INDEX, clamped).apply()
        _cacheLimitIndex.value = clamped
    }

    fun getCacheLimitBytes(): Long {
        return when (_cacheLimitIndex.value) {
            0 -> 300L * 1024 * 1024
            1 -> 500L * 1024 * 1024
            2 -> 1024L * 1024 * 1024
            3 -> 2L * 1024 * 1024 * 1024
            4 -> 3L * 1024 * 1024 * 1024
            5 -> 5L * 1024 * 1024 * 1024
            else -> 500L * 1024 * 1024
        }
    }

    fun setAutoDeleteWatchedAfter24h(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DELETE_24H, enabled).apply()
        _autoDeleteWatchedAfter24h.value = enabled
    }

    fun setInstantBatchSizeMb(sizeMb: Int) {
        val clamped = sizeMb.coerceIn(100, 500)
        prefs.edit().putInt(KEY_INSTANT_BATCH_MB, clamped).apply()
        _instantBatchSizeMb.value = clamped
    }

    fun setInfiniteLoopDefault(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INFINITE_LOOP, enabled).apply()
        _infiniteLoopDefault.value = enabled
    }

    fun setHapticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _hapticsEnabled.value = enabled
    }

    fun setAmbientModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AMBIENT_MODE, enabled).apply()
        _ambientModeEnabled.value = enabled
    }

    fun setShakeToShuffleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHAKE_TO_SHUFFLE, enabled).apply()
        _shakeToShuffleEnabled.value = enabled
    }
}
