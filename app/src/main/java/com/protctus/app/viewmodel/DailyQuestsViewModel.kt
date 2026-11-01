package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class DailyQuest(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val points: Int = 0,
    val actionTrigger: String = "",
    val isReadyToClaim: Boolean = false,
    val isCompleted: Boolean = false,
    val isClaiming: Boolean = false
)

sealed class QuestsState {
    data object Loading : QuestsState()
    data class Success(val quests: List<DailyQuest>) : QuestsState()
    data class Error(val message: String) : QuestsState()
}

/**
 * Emitted when XP is earned — the UI listens to this for animations.
 */
data class XpGainEvent(
    val questTitle: String,
    val xpAmount: Int
)

@HiltViewModel
class DailyQuestsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val globalQuestTracker: com.protctus.app.utils.GlobalQuestTracker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _questsState = MutableStateFlow<QuestsState>(QuestsState.Loading)
    val questsState: StateFlow<QuestsState> = _questsState.asStateFlow()

    // One-shot event channel for XP gain animations
    private val _xpGainEvent = MutableSharedFlow<XpGainEvent>()
    val xpGainEvent: SharedFlow<XpGainEvent> = _xpGainEvent.asSharedFlow()

    init {
        fetchQuests()
        collectQuestEvents()
    }

    /**
     * Listens to GlobalQuestTracker and auto-marks matching quests as ready-to-claim.
     */
    private fun collectQuestEvents() {
        viewModelScope.launch {
            globalQuestTracker.questEvents.collect { event ->
                val current = _questsState.value
                if (current is QuestsState.Success) {
                    _questsState.value = QuestsState.Success(
                        quests = current.quests.map { quest ->
                            if (quest.actionTrigger.lowercase().trim() == event && !quest.isCompleted) {
                                quest.copy(isReadyToClaim = true)
                            } else quest
                        }
                    )
                }
            }
        }
    }

    fun fetchQuests() {
        viewModelScope.launch {
            _questsState.value = QuestsState.Loading
            try {
                // Fetch all quest types (not just DAILY)
                val questsArray = com.protctus.app.network.QuestsApiClient.fetchQuests(null)
                val uid = auth.currentUser?.uid

                // Load user's claimed missions to mark completed ones
                val claimedMissions = mutableSetOf<String>()
                if (uid != null) {
                    try {
                        val userDoc = firestore.collection("users").document(uid).get().await()
                        if (userDoc.exists()) {
                            @Suppress("UNCHECKED_CAST")
                            val claimedList = userDoc.get("claimed_missions") as? List<String> ?: emptyList()
                            claimedMissions.addAll(claimedList)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DailyQuestsViewModel", "Failed to fetch claimed missions: ${e.message}")
                    }
                }

                val allAvailableQuests = mutableListOf<DailyQuest>()
                if (questsArray != null) {
                    for (i in 0 until questsArray.length()) {
                        val obj = questsArray.getJSONObject(i)
                        val questId = obj.getString("id")
                        val actionTrigger = obj.optString("action_trigger", "")
                        val isClaimed = claimedMissions.contains(questId)
                        val isReady = !isClaimed && com.protctus.app.utils.QuestTracker.isActionCompleted(context, actionTrigger)
                        
                        allAvailableQuests.add(DailyQuest(
                            id = questId,
                            title = obj.getString("title"),
                            subtitle = obj.getString("description"),
                            points = obj.getInt("xp_reward"),
                            actionTrigger = actionTrigger,
                            isReadyToClaim = isReady,
                            isCompleted = isClaimed
                        ))
                    }
                }

                // Group by strict actionTrigger to ensure no two identical quests are shown
                val groupedByRoute = allAvailableQuests.groupBy { 
                    com.protctus.app.utils.QuestActionRegistry.getRouteForTrigger(it.actionTrigger)::class.simpleName 
                }

                // Pick exactly 1 random quest per distinct route
                val distinctQuests = groupedByRoute.values.map { quests ->
                    quests.first() // Simplified since order is inherently stable, but let's just take the first of each distinct group
                }.sortedBy { it.id } // Sort purely for deterministic seed input

                // Create a deterministic daily seed using the user's local timezone
                val cal = java.util.Calendar.getInstance()
                val todaySeed = (cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)).toLong()
                val randomizer = java.util.Random(todaySeed)

                // Shuffle the distinctly grouped quests deterministically and pick the top 5
                val dailyRandomQuests = distinctQuests.shuffled(randomizer).take(5)

                _questsState.value = QuestsState.Success(dailyRandomQuests)
            } catch (e: Exception) {
                _questsState.value = QuestsState.Error(e.message ?: "Failed to fetch quests")
            }
        }
    }

    /**
     * Claims a quest — sends XP to the Cloudflare D1 backend.
     * On success: marks quest completed, emits XpGainEvent for animation.
     */
    fun completeQuest(quest: DailyQuest, onResult: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(false, "Not logged in")
            return
        }
        if (quest.isCompleted) {
            onResult(false, "Already completed")
            return
        }

        // Mark as claiming (for loading spinner)
        updateQuestState(quest.id, isClaiming = true)

        viewModelScope.launch {
            try {
                val success = com.protctus.app.network.LeaderboardApiClient.claimMission(
                    uid = uid,
                    missionId = quest.id,
                    xpReward = quest.points
                )

                if (success) {
                    // Mark completed in local state
                    updateQuestState(quest.id, isCompleted = true, isClaiming = false)
                    // Emit XP gain event for animation
                    _xpGainEvent.emit(XpGainEvent(questTitle = quest.title, xpAmount = quest.points))
                    onResult(true, "+${quest.points} XP earned!")
                } else {
                    updateQuestState(quest.id, isClaiming = false)
                    onResult(false, "Server error. Try again.")
                }
            } catch (e: Exception) {
                updateQuestState(quest.id, isClaiming = false)
                onResult(false, "Network error: ${e.message}")
            }
        }
    }

    private fun updateQuestState(questId: String, isCompleted: Boolean? = null, isClaiming: Boolean? = null) {
        val current = _questsState.value
        if (current is QuestsState.Success) {
            _questsState.value = QuestsState.Success(
                quests = current.quests.map { q ->
                    if (q.id == questId) {
                        q.copy(
                            isCompleted = isCompleted ?: q.isCompleted,
                            isClaiming = isClaiming ?: q.isClaiming,
                            isReadyToClaim = q.isReadyToClaim
                        )
                    } else q
                }
            )
        }
    }
}
