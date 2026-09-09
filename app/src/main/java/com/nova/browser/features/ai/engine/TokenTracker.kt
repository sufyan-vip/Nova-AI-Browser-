package com.nova.browser.features.ai.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.nova.browser.core.di.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent token/usage accounting with a soft daily budget. */
@Singleton
class TokenTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Usage(
        val todayTokens: Int = 0,
        val totalTokens: Int = 0,
        val requestCount: Int = 0,
        val budget: Int = DEFAULT_BUDGET
    ) {
        val budgetFraction: Float
            get() = if (budget <= 0) 0f else (todayTokens.toFloat() / budget).coerceIn(0f, 1f)
        val overBudget: Boolean get() = budget > 0 && todayTokens >= budget
    }

    companion object {
        const val DEFAULT_BUDGET = 200_000
        private val KEY_TODAY = intPreferencesKey("tokens_today")
        private val KEY_TOTAL = intPreferencesKey("tokens_total")
        private val KEY_REQUESTS = intPreferencesKey("ai_request_count")
        private val KEY_DAY = longPreferencesKey("tokens_day_stamp")
        private val KEY_BUDGET = intPreferencesKey("tokens_daily_budget")
    }

    val usage: Flow<Usage> = context.settingsDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs ->
            val storedDay = prefs[KEY_DAY] ?: 0L
            val sameDay = storedDay == todayStamp()
            Usage(
                todayTokens = if (sameDay) prefs[KEY_TODAY] ?: 0 else 0,
                totalTokens = prefs[KEY_TOTAL] ?: 0,
                requestCount = prefs[KEY_REQUESTS] ?: 0,
                budget = prefs[KEY_BUDGET] ?: DEFAULT_BUDGET
            )
        }

    suspend fun record(usage: AIUsage) {
        val amount = if (usage.totalTokens > 0) {
            usage.totalTokens
        } else {
            usage.promptTokens + usage.completionTokens
        }
        try {
            context.settingsDataStore.edit { prefs ->
                val today = todayStamp()
                val sameDay = (prefs[KEY_DAY] ?: 0L) == today
                prefs[KEY_DAY] = today
                prefs[KEY_TODAY] = (if (sameDay) prefs[KEY_TODAY] ?: 0 else 0) + amount
                prefs[KEY_TOTAL] = (prefs[KEY_TOTAL] ?: 0) + amount
                prefs[KEY_REQUESTS] = (prefs[KEY_REQUESTS] ?: 0) + 1
            }
        } catch (e: Exception) {
            // Usage stats are non-critical.
        }
    }

    suspend fun setBudget(budget: Int) {
        try {
            context.settingsDataStore.edit { it[KEY_BUDGET] = budget.coerceAtLeast(0) }
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun reset() {
        try {
            context.settingsDataStore.edit { prefs ->
                prefs[KEY_TODAY] = 0
                prefs[KEY_TOTAL] = 0
                prefs[KEY_REQUESTS] = 0
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /** Rough token estimate: words × 1.3 (spec 10_CONTEXT_ENGINE). */
    fun estimate(text: String): Int {
        if (text.isBlank()) return 0
        return (text.trim().split(Regex("\\s+")).size * 1.3).toInt()
    }

    private fun todayStamp(): Long = Calendar.getInstance().run {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}
