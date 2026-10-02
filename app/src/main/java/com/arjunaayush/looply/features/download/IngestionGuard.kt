package com.arjunaayush.looply.features.download

import android.content.Context
import com.arjunaayush.looply.core.network.instagram.config.IngestionConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

sealed interface BlockReason {
    data class CoolingDown(val untilMillis: Long) : BlockReason
    data object NeedsUserVerification : BlockReason
    data object NeedsLogin : BlockReason
    data object DailyBudgetUsed : BlockReason
    data object DisabledRemotely : BlockReason
}

@Singleton
class IngestionGuard @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("ingestion_guard", Context.MODE_PRIVATE)

    fun blockReason(config: IngestionConfig, now: Long = System.currentTimeMillis()): BlockReason? {
        rollDay()
        return when {
            !config.batchEnabled -> BlockReason.DisabledRemotely
            prefs.getBoolean(KEY_NEEDS_LOGIN, false) -> BlockReason.NeedsLogin
            prefs.getBoolean(KEY_NEEDS_VERIFY, false) && now < cooldownUntil() -> BlockReason.NeedsUserVerification
            now < cooldownUntil() -> BlockReason.CoolingDown(cooldownUntil())
            pagesToday() >= config.dailyPageBudget || reelsToday() >= config.dailyReelBudget -> BlockReason.DailyBudgetUsed
            else -> null
        }
    }

    fun hasBudget(config: IngestionConfig): Boolean =
        pagesToday() < config.dailyPageBudget && reelsToday() < config.dailyReelBudget

    fun recordPage() = prefs.edit().putInt(KEY_PAGES, pagesToday() + 1).apply()
    fun recordReels(count: Int) = prefs.edit().putInt(KEY_REELS, reelsToday() + count).apply()

    fun onRateLimited(now: Long = System.currentTimeMillis()) {
        val strikes = prefs.getInt(KEY_STRIKES, 0)
        val backoff = min(BASE_BACKOFF_MS shl min(strikes, 6), MAX_BACKOFF_MS)
        prefs.edit().putInt(KEY_STRIKES, strikes + 1).putLong(KEY_COOLDOWN, now + backoff).apply()
    }

    fun onChallenge(now: Long = System.currentTimeMillis()) =
        prefs.edit().putBoolean(KEY_NEEDS_VERIFY, true).putLong(KEY_COOLDOWN, now + MAX_BACKOFF_MS).apply()

    fun onSessionExpired() = prefs.edit().putBoolean(KEY_NEEDS_LOGIN, true).apply()

    /** Call from the login flow after a successful Instagram login, and from a "I've verified my account" action. */
    fun clearUserActionFlags() = prefs.edit()
        .putBoolean(KEY_NEEDS_LOGIN, false)
        .putBoolean(KEY_NEEDS_VERIFY, false)
        .putLong(KEY_COOLDOWN, 0L)
        .putInt(KEY_STRIKES, 0)
        .apply()

    fun onCleanRun() = prefs.edit().putInt(KEY_STRIKES, 0).apply()

    private fun cooldownUntil() = prefs.getLong(KEY_COOLDOWN, 0L)
    private fun pagesToday() = prefs.getInt(KEY_PAGES, 0)
    private fun reelsToday() = prefs.getInt(KEY_REELS, 0)

    private fun rollDay() {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit().putString(KEY_DAY, today).putInt(KEY_PAGES, 0).putInt(KEY_REELS, 0).apply()
        }
    }

    private companion object {
        const val KEY_DAY = "day"
        const val KEY_PAGES = "pages_today"
        const val KEY_REELS = "reels_today"
        const val KEY_STRIKES = "strikes"
        const val KEY_COOLDOWN = "cooldown_until"
        const val KEY_NEEDS_LOGIN = "needs_login"
        const val KEY_NEEDS_VERIFY = "needs_verify"
        const val BASE_BACKOFF_MS = 30L * 60 * 1000
        const val MAX_BACKOFF_MS = 24L * 60 * 60 * 1000
    }
}
