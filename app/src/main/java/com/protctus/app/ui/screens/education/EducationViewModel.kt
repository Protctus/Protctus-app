package com.protctus.app.ui.screens.education

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.repository.EducationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Local data models for the gamified UI
data class StreakData(val currentStreak: Int, val maxStreak: Int, val lastActiveDate: String)
data class DailyGoalData(val currentProgress: Int, val maxTarget: Int)
data class Achievement(val id: String, val title: String, val description: String, val iconRes: Int?, val isUnlocked: Boolean)
data class ProfileStats(val totalXp: Int, val rank: String, val completedModules: Int, val accuracy: Float)

@HiltViewModel
class EducationViewModel @Inject constructor(
    private val repository: EducationRepository
) : ViewModel() {

    val streak: StateFlow<StreakData> = repository.streak
    val dailyGoal: StateFlow<DailyGoalData> = repository.dailyGoal
    val stats: StateFlow<ProfileStats> = repository.stats

    // Achievements are still mocked locally as they typically depend on logic running against local stats or server sync.
    // They can be extended to use the repository as well.
    private val _achievements = MutableStateFlow(
        listOf(
            Achievement("1", "First Defense", "Complete your first lesson.", null, true),
            Achievement("2", "Phishing Expert", "Perfect score in Phishing.", null, true),
            Achievement("3", "Malware Analyst", "Complete 10 Malware modules.", null, false),
            Achievement("4", "7-Day Streak", "Maintain a streak for a week.", null, true),
            Achievement("5", "30-Day Streak", "Maintain a streak for a month.", null, false)
        )
    )
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    init {
        viewModelScope.launch {
            repository.fetchStats()
        }
    }
    
    // Simulate updating goals and push to cloudflare
    fun completeLesson(accuracy: Float = 1.0f) {
        viewModelScope.launch {
            // Give 5 XP for completion
            repository.addXp(xpGained = 5, moduleCompleted = true, accuracy = accuracy)
        }
    }
}
