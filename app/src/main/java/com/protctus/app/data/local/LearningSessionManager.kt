package com.protctus.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

val Context.learningSessionDataStore by preferencesDataStore(name = "learning_session_prefs")

data class SessionData(
    val attemptedCategory: String?,
    val dailyQuota: Int
)

class LearningSessionManager(private val context: Context) {

    private val LAST_DATE_KEY = stringPreferencesKey("last_learning_date")
    private val ATTEMPTED_CATEGORY_KEY = stringPreferencesKey("attempted_category")
    private val DAILY_QUOTA_KEY = intPreferencesKey("daily_quota")
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun getCurrentDateStr(): String = dateFormat.format(Date())

    val sessionStateFlow: Flow<SessionData> = context.learningSessionDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val lastDate = prefs[LAST_DATE_KEY] ?: ""
            val today = getCurrentDateStr()
            
            if (lastDate != today) {
                // New Day - Reset session
                SessionData(null, 0)
            } else {
                SessionData(
                    attemptedCategory = prefs[ATTEMPTED_CATEGORY_KEY],
                    dailyQuota = prefs[DAILY_QUOTA_KEY] ?: 0
                )
            }
        }

    suspend fun setAttemptedCategory(category: String) {
        val today = getCurrentDateStr()
        context.learningSessionDataStore.edit { prefs ->
            val lastDate = prefs[LAST_DATE_KEY] ?: ""
            if (lastDate != today) {
                prefs[LAST_DATE_KEY] = today
                prefs[ATTEMPTED_CATEGORY_KEY] = category
                prefs[DAILY_QUOTA_KEY] = 0
            } else if (prefs[ATTEMPTED_CATEGORY_KEY] == null) {
                prefs[ATTEMPTED_CATEGORY_KEY] = category
            }
        }
    }

    suspend fun incrementDailyQuota() {
        val today = getCurrentDateStr()
        context.learningSessionDataStore.edit { prefs ->
            prefs[LAST_DATE_KEY] = today
            val currentQuota = prefs[DAILY_QUOTA_KEY] ?: 0
            prefs[DAILY_QUOTA_KEY] = currentQuota + 1
        }
    }
}
