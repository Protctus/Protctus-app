package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.protctus.app.ui.screens.dashboard.RankData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class RewardItem(
    val title: String,
    val rankDescription: String,
    val iconRes: Int,
    val rank: Int,
    val xpRequired: Int,
    val unlocked: Boolean = false,
    val isClaimed: Boolean = false,
    val nextMissionText: String,
    val missionId: String
)

sealed class RewardsState {
    object Loading : RewardsState()
    data class Success(
        val currentXp: Int,
        val earnedBadges: List<RewardItem>,
        val nextBadges: List<RewardItem>,
        val currentRank: RankData
    ) : RewardsState()
    data class Error(val message: String) : RewardsState()
}

@HiltViewModel
class RewardsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private var snapshotListener: ListenerRegistration? = null

    private val _rewardsState = MutableStateFlow<RewardsState>(RewardsState.Loading)
    val rewardsState: StateFlow<RewardsState> = _rewardsState.asStateFlow()

    private var cachedDynamicBadges = listOf<RewardItem>()

    /** Public accessor for the full badge list — used by RankDefendScreen */
    val allBadges: List<RewardItem> get() = cachedDynamicBadges

    // Real security subscores from SecurityScanner
    private var _realDeviceIntegrity: Float = 0f
    private var _realAccountArmor: Float = 0f
    private var _realNetworkShield: Float = 0f
    private var _hasRealScanData = false

    init {
        performRealSecurityScan()
        startPollingCloudflare()
    }

    private fun performRealSecurityScan() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = com.protctus.app.utils.SecurityScanner.performScan(appContext)

                // Device Integrity: rooted, emulator, ADB, unknown sources
                var integrity = 1.0f
                if (result.isRooted) integrity -= 0.5f
                if (result.isEmulator) integrity -= 0.3f
                if (result.adbEnabled) integrity -= 0.1f
                if (result.unknownSourcesEnabled) integrity -= 0.1f

                // Network Shield: proxies, user CAs, WiFi, DNS
                var network = 1.0f
                if (result.hasHttpProxy) network -= 0.3f
                if (result.hasUserCaCerts) network -= 0.3f
                if (!result.isWifiSecure) network -= 0.2f
                if (result.isDnsIntercepted) network -= 0.2f

                // Account Armor: screen lock, accessibility spyware
                var account = 1.0f
                if (!result.isDeviceSecure) account -= 0.5f
                if (result.hasUnknownAccessibility) account -= 0.5f

                _realDeviceIntegrity = integrity.coerceIn(0f, 1f)
                _realAccountArmor = account.coerceIn(0f, 1f)
                _realNetworkShield = network.coerceIn(0f, 1f)
                _hasRealScanData = true
            } catch (e: Exception) {
                android.util.Log.e("RewardsViewModel", "Real scan failed: ${e.message}")
            }
        }
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    private fun startPollingCloudflare() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _rewardsState.value = RewardsState.Error("User not logged in")
            return
        }

        pollingJob?.cancel()
        pollingJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                try {
                    // Fetch user progression natively from Firestore
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    if (userDoc.exists()) {
                        val currentXp = userDoc.getLong("total_xp")?.toInt() ?: userDoc.getLong("xp")?.toInt() ?: 0
                        @Suppress("UNCHECKED_CAST")
                        val claimedList = userDoc.get("claimed_missions") as? List<String> ?: emptyList()
                        
                        // Dynamically resolve allBadges via GamificationEngine
                        val dynamicBadges = mutableListOf<RewardItem>()
                        for (i in com.protctus.app.utils.GamificationEngine.titles.indices) {
                            val titleStr = com.protctus.app.utils.GamificationEngine.titles[i]
                            dynamicBadges.add(RewardItem(
                                title = titleStr,
                                rankDescription = "Reach ${com.protctus.app.utils.GamificationEngine.xpThresholds[i]} XP",
                                iconRes = mapIconByKeywords(titleStr),
                                rank = i + 1,
                                xpRequired = com.protctus.app.utils.GamificationEngine.xpThresholds[i],
                                unlocked = false,
                                isClaimed = false,
                                nextMissionText = "",
                                missionId = ""
                            ))
                        }
                        cachedDynamicBadges = dynamicBadges.sortedBy { it.xpRequired }
                        
                        // Dynamically fetch actual Quests from Cloudflare D1 (since this part works)
                        val activeQuests = com.protctus.app.network.QuestsApiClient.fetchQuests()
                        var nextMissionTextStr = "All Missions Complete!"
                        var globalNextMissionId = "none"
                        var nextMissionXpNum = 0
                        
                        if (activeQuests != null) {
                            for (i in 0 until activeQuests.length()) {
                                val obj = activeQuests.getJSONObject(i)
                                val questId = obj.getString("id")
                                if (!claimedList.contains(questId)) {
                                    val actionTrigger = obj.optString("action_trigger", "")
                                    _globalMissionTriggerHolder = actionTrigger
                                    _globalMissionTitleHolder = obj.getString("title")
                                    nextMissionTextStr = obj.getString("title") + " (+${obj.getInt("xp_reward")} XP)"
                                    globalNextMissionId = questId 
                                    nextMissionXpNum = obj.getInt("xp_reward")
                                    break 
                                }
                            }
                        }
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            calculateRewards(currentXp, claimedList, nextMissionTextStr, globalNextMissionId, nextMissionXpNum)
                        }
                    } else {
                        // User not found in Firestore, default to 0
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            calculateRewards(0, emptyList(), "No Available Missions", "none", 0)
                        }
                    }
                } catch (e: Exception) {
                    // Fail silently to try again in 15s
                    android.util.Log.e("RewardsViewModel", "Polling failed: ${e.message}")
                }
                kotlinx.coroutines.delay(15000) // 15 Second Heartbeat Requested by User
            }
        }
    }

    private fun calculateRewards(
        currentXp: Int, 
        claimedMissions: List<String>, 
        nextMissionTextStr: String, 
        globalNextMissionId: String, 
        nextMissionXpNum: Int
    ) {
        val earned = mutableListOf<RewardItem>()
        val next = mutableListOf<RewardItem>()
        var highestUnlockedRank = 1

        if (cachedDynamicBadges.isEmpty()) {
            _rewardsState.value = RewardsState.Error("No dynamic badges loaded")
            return
        }

        for (badge in cachedDynamicBadges) {
            if (currentXp >= badge.xpRequired) {
                earned.add(badge.copy(unlocked = true, isClaimed = true)) // If unlocked via XP, it's virtually "claimed" visually
                highestUnlockedRank = badge.rank
            } else {
                next.add(badge.copy(isClaimed = false))
            }
        }

        // Compute RankData mapping the highest unlocked rank
        val currentBadge = cachedDynamicBadges.find { it.rank == highestUnlockedRank } ?: cachedDynamicBadges.firstOrNull() ?: fallbackBadge()
        val nextBadge = cachedDynamicBadges.find { it.rank == highestUnlockedRank + 1 }

        val rankData = RankData(
            levelNumber = currentBadge.rank,
            rankTitle = currentBadge.title,
            rankDescription = currentBadge.rankDescription,
            themeColor = getThemeColorForRank(currentBadge.rank),
            currentXp = currentXp,
            maxXp = nextBadge?.xpRequired ?: currentBadge.xpRequired, // Next milestone
            deviceIntegrity = if (_hasRealScanData) _realDeviceIntegrity else calculateSubscoreFallback(currentXp, "integrity"),
            accountArmor = if (_hasRealScanData) _realAccountArmor else calculateSubscoreFallback(currentXp, "armor"),
            networkShield = if (_hasRealScanData) _realNetworkShield else calculateSubscoreFallback(currentXp, "network"),
            nextMissionText = nextMissionTextStr, // Stolen cleanly from Active Quests
            nextXpReward = nextMissionXpNum, 
            iconRes = currentBadge.iconRes,
            isClaimed = globalNextMissionId == "none" // The Global Next Mission controls if they can act on the UI
        )
        
        _globalMissionIdHolder = globalNextMissionId
        _globalMissionXpHolder = nextMissionXpNum

        _rewardsState.value = RewardsState.Success(
            currentXp = currentXp,
            earnedBadges = earned,
            nextBadges = next,
            currentRank = rankData
        )
    }

    private var _globalMissionIdHolder = "none"
    private var _globalMissionXpHolder = 0
    private var _globalMissionTriggerHolder = ""
    private var _globalMissionTitleHolder = ""


    // Fallback subscores if real scan hasn't completed yet
    private fun calculateSubscoreFallback(xp: Int, type: String): Float {
        val baseMultiplier = xp.toFloat() / 1000f
        return when (type) {
            "integrity" -> (baseMultiplier * 1.2f).coerceIn(0f, 1f)
            "armor" -> (baseMultiplier * 1.0f).coerceIn(0f, 1f)
            "network" -> (baseMultiplier * 0.8f).coerceIn(0f, 1f)
            else -> baseMultiplier.coerceIn(0f, 1f)
        }
    }

    private fun getThemeColorForRank(rank: Int): androidx.compose.ui.graphics.Color {
        return when (rank) {
             1 -> androidx.compose.ui.graphics.Color(0xFFEF4444) // Red
             2 -> androidx.compose.ui.graphics.Color(0xFFF59E0B) // Amber
             3 -> androidx.compose.ui.graphics.Color(0xFF10B981) // Green
             4 -> androidx.compose.ui.graphics.Color(0xFF3B82F6) // Blue
             5 -> androidx.compose.ui.graphics.Color(0xFF8B5CF6) // Purple
             6 -> androidx.compose.ui.graphics.Color(0xFFEC4899) // Pink
             7 -> androidx.compose.ui.graphics.Color(0xFFD946EF) // Fuchsia
             8 -> androidx.compose.ui.graphics.Color(0xFFEAB308) // Yellow
             9 -> androidx.compose.ui.graphics.Color(0xFF06B6D4) // Cyan
             else -> androidx.compose.ui.graphics.Color(0xFF8C8D92) // Grey
        }
    }

    private fun mapIconByKeywords(title: String): Int {
        val lower = title.lowercase()
        return when {
            lower.contains("novice") -> com.protctus.app.R.drawable.encryption
            lower.contains("network") || lower.contains("connection") -> com.protctus.app.R.drawable.network
            lower.contains("lock") || lower.contains("shield") -> com.protctus.app.R.drawable.privacy_shield
            lower.contains("virus") || lower.contains("threat") -> com.protctus.app.R.drawable.virus_slayer
            lower.contains("data") || lower.contains("audit") -> com.protctus.app.R.drawable.data_guardian
            lower.contains("anonymity") || lower.contains("ghost") -> com.protctus.app.R.drawable.annoymity_ace
            else -> com.protctus.app.R.drawable.fortless_builder
        }
    }

    private fun fallbackBadge() = RewardItem("Ghost", "Unknown", com.protctus.app.R.drawable.encryption, 1, 0, true, true, "", "none")

    // --- REAL LOGIC: Verify device state and grant XP natively ---
    fun verifyGlobalNextMission(context: android.content.Context, onResult: (Boolean, String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(false, "Not authenticated")
            return
        }

        val missionId = _globalMissionIdHolder
        if (missionId == "none") {
            onResult(false, "All Active Missions Completed.")
            return
        }

        val isPassed = com.protctus.app.utils.QuestTracker.isActionCompleted(context, _globalMissionTriggerHolder)

        if (isPassed) {
            val xpReward = _globalMissionXpHolder
            viewModelScope.launch {
                val success = com.protctus.app.network.LeaderboardApiClient.claimMission(uid, missionId, xpReward)
                if (success) {
                    onResult(true, "Mission Accomplished! +$xpReward XP")
                    // Immediately trigger a fetch to update the UI instead of waiting for the 15s tick
                    startPollingCloudflare() 
                } else {
                    onResult(false, "Server Error: Could not sync with Cloudflare D1.")
                }
            }
        } else {
            onResult(false, "Mission Failed. Please complete the requirement first.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
