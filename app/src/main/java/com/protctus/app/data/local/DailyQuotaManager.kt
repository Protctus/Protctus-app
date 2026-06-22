package com.protctus.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

val Context.dailyQuotaDataStore by preferencesDataStore(name = "daily_quota_prefs")

data class DailyQuotaState(
    val completedToday: Int,
    val isLimitReached: Boolean
)

@Singleton
class DailyQuotaManager @Inject constructor(private val context: Context) {

    private val LAST_DATE_KEY = stringPreferencesKey("last_completed_date")
    private val COMPLETED_COUNT_KEY = intPreferencesKey("completed_today_count")
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getCurrentDateStr(): String = dateFormat.format(Date())

    val quotaStateFlow: Flow<DailyQuotaState> = context.dailyQuotaDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val lastDate = prefs[LAST_DATE_KEY] ?: ""
            val today = getCurrentDateStr()
            
            if (lastDate != today) {
                // New Day - Reset count
                DailyQuotaState(completedToday = 0, isLimitReached = false)
            } else {
                val count = prefs[COMPLETED_COUNT_KEY] ?: 0
                DailyQuotaState(
                    completedToday = count,
                    isLimitReached = count >= 10
                )
            }
        }

    suspend fun incrementCompletedCount(): Boolean {
        val today = getCurrentDateStr()
        var success = false
        context.dailyQuotaDataStore.edit { prefs ->
            val lastDate = prefs[LAST_DATE_KEY] ?: ""
            val currentCount = if (lastDate != today) 0 else (prefs[COMPLETED_COUNT_KEY] ?: 0)
            
            if (currentCount < 10) {
                prefs[LAST_DATE_KEY] = today
                prefs[COMPLETED_COUNT_KEY] = currentCount + 1
                success = true
            } else {
                success = false
            }
        }
        return success
    }
}
