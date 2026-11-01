package com.protctus.app.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.protctus.app.network.CloudflareEducationApi
import com.protctus.app.network.EducationStatsResponse
import com.protctus.app.network.XpAddRequest
import com.protctus.app.ui.screens.education.DailyGoalData
import com.protctus.app.ui.screens.education.ProfileStats
import com.protctus.app.ui.screens.education.StreakData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EducationRepository @Inject constructor(
    private val prefs: SharedPreferences
) {
    private val api: CloudflareEducationApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://protctus-education-api.jatinsharmashivpuri02122008.workers.dev/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudflareEducationApi::class.java)
    }

    private val _streak = MutableStateFlow(StreakData(currentStreak = 0, maxStreak = 0, lastActiveDate = "Unknown"))
    val streak: StateFlow<StreakData> = _streak.asStateFlow()

    private val _dailyGoal = MutableStateFlow(DailyGoalData(currentProgress = 0, maxTarget = 5))
    val dailyGoal: StateFlow<DailyGoalData> = _dailyGoal.asStateFlow()

    private val _stats = MutableStateFlow(ProfileStats(totalXp = 0, rank = "Novice", completedModules = 0, accuracy = 0f))
    val stats: StateFlow<ProfileStats> = _stats.asStateFlow()

    private val authHeader: String
        get() = prefs.getString("auth_token", "")?.let { "Bearer $it" } ?: ""

    suspend fun fetchStats() = withContext(Dispatchers.IO) {
        try {
            val response = api.getStats(authHeader)
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    _streak.value = StreakData(body.currentStreak, body.maxStreak, "Today")
                    _dailyGoal.value = DailyGoalData(body.dailyProgress, body.dailyTarget)
                    _stats.value = ProfileStats(body.totalXp, body.rank, body.completedModules, body.accuracy)
                    saveToCache(body)
                }
            } else {
                loadFromCache()
            }
        } catch (e: Exception) {
            Log.e("EducationRepository", "Error fetching stats", e)
            loadFromCache()
        }
    }

    suspend fun incrementStreak() = withContext(Dispatchers.IO) {
        try {
            val response = api.incrementStreak(authHeader)
            if (response.isSuccessful) {
                fetchStats() // Re-fetch to get updated values
            }
        } catch (e: Exception) {
            Log.e("EducationRepository", "Error incrementing streak", e)
        }
    }

    suspend fun addXp(xpGained: Int, moduleCompleted: Boolean, accuracy: Float) = withContext(Dispatchers.IO) {
        // Optimistic UI update
        val currStats = _stats.value
        _stats.value = currStats.copy(
            totalXp = currStats.totalXp + xpGained,
            completedModules = if (moduleCompleted) currStats.completedModules + 1 else currStats.completedModules
        )
        val currGoal = _dailyGoal.value
        if (moduleCompleted) {
            _dailyGoal.value = currGoal.copy(currentProgress = currGoal.currentProgress + 1)
        }

        try {
            api.addXp(authHeader, XpAddRequest(xpGained, moduleCompleted, accuracy))
        } catch (e: Exception) {
            Log.e("EducationRepository", "Error adding XP", e)
        }
    }

    private fun saveToCache(data: EducationStatsResponse) {
        prefs.edit().apply {
            putInt("edu_currentStreak", data.currentStreak)
            putInt("edu_maxStreak", data.maxStreak)
            putInt("edu_totalXp", data.totalXp)
            putString("edu_rank", data.rank)
            putInt("edu_completedModules", data.completedModules)
            putFloat("edu_accuracy", data.accuracy)
            putInt("edu_dailyProgress", data.dailyProgress)
            putInt("edu_dailyTarget", data.dailyTarget)
        }.apply()
    }

    private fun loadFromCache() {
        _streak.value = StreakData(
            currentStreak = prefs.getInt("edu_currentStreak", 0),
            maxStreak = prefs.getInt("edu_maxStreak", 0),
            lastActiveDate = "Cached"
        )
        _stats.value = ProfileStats(
            totalXp = prefs.getInt("edu_totalXp", 0),
            rank = prefs.getString("edu_rank", "Novice") ?: "Novice",
            completedModules = prefs.getInt("edu_completedModules", 0),
            accuracy = prefs.getFloat("edu_accuracy", 0f)
        )
        _dailyGoal.value = DailyGoalData(
            currentProgress = prefs.getInt("edu_dailyProgress", 0),
            maxTarget = prefs.getInt("edu_dailyTarget", 5)
        )
    }
}
