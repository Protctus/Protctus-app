package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.compose.ui.graphics.Color
import com.protctus.app.ui.screens.dashboard.RankData
import com.protctus.app.utils.SecurityScanResult
import com.protctus.app.utils.SecurityScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class HomeState(
    val isScanning: Boolean = true,
    val result: SecurityScanResult = SecurityScanResult(
        score = 0,
        domainFindings = emptyMap(),
        adbEnabled = false,
        unknownSourcesEnabled = false,
        isDeviceSecure = true,
        hasUnknownAccessibility = false,
        hasHttpProxy = false,
        hasUserCaCerts = false,
        isRooted = false,
        isEmulator = false,
        hasSideloadedApps = false,
        hasPermissionMismatchApps = false,
        isVpnActive = false,
        isWifiSecure = true,
        isDnsIntercepted = false
    ),
    val rankData: RankData? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _homeState = MutableStateFlow(HomeState(isScanning = true))
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _username = MutableStateFlow<String>("User")
    val username: StateFlow<String> = _username.asStateFlow()

    init {
        fetchUsername()
    }

    private fun fetchUsername() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val document = firestore.collection("users").document(uid).get().await()
                if (document.exists()) {
                    val foundUsername = document.getString("username")
                    if (!foundUsername.isNullOrBlank()) {
                        _username.value = foundUsername
                    } else {
                        _username.value = "User"
                    }
                } else {
                    _username.value = "User"
                }
            } catch (e: Exception) {
                // Keep default "User"
            }
        }
    }

    fun triggerNewScan(context: Context) {
        _homeState.value = _homeState.value.copy(
            isScanning = true,
            result = _homeState.value.result.copy(score = 0)
        )
        performDeepScan(context)
    }

    fun performDeepScan(context: Context) {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isScanning = true)
            try {
                val result = withContext(Dispatchers.IO) {
                    SecurityScanner.performScan(context)
                }
                
                val rankData = calculateRealRank(result)

                // --- REAL LOGIC: Cloud Sync XP ---
                syncToGlobalLeaderboard(rankData)

                _homeState.value = HomeState(
                    isScanning = false,
                    result = result,
                    rankData = rankData,
                    error = null
                )
                
            } catch (e: Exception) {
                _homeState.value = _homeState.value.copy(
                    isScanning = false,
                    error = e.message ?: "Unknown error occurred during scan"
                )
            }
        }
    }

    private suspend fun syncToGlobalLeaderboard(rankData: RankData) {
        val currentUser = auth.currentUser ?: return // Skip if not logged in
        
        // PUSH NATIVELY TO CLOUDFLARE D1
        com.protctus.app.network.LeaderboardApiClient.syncUserTelemetry(
            uid = currentUser.uid,
            username = _username.value,
            securityScore = rankData.currentXp,
            rankTitle = rankData.rankTitle,
            level = rankData.levelNumber
        )
    }

    private fun calculateRealRank(result: SecurityScanResult): RankData {
        // 1. Device Integrity (Rooted, Emulator, ADB, Unknown Sources)
        var integrityScore = 1.0f
        if (result.isRooted) integrityScore -= 0.5f
        if (result.isEmulator) integrityScore -= 0.3f
        if (result.adbEnabled) integrityScore -= 0.1f
        if (result.unknownSourcesEnabled) integrityScore -= 0.1f
        
        // 2. Network Shield (Proxies, User CAs, WiFi, DNS)
        var networkScore = 1.0f
        if (result.hasHttpProxy) networkScore -= 0.3f
        if (result.hasUserCaCerts) networkScore -= 0.3f
        if (!result.isWifiSecure) networkScore -= 0.2f
        if (result.isDnsIntercepted) networkScore -= 0.2f
        
        // 3. Account Armor (Screen Lock, Accessibility Spyware)
        var accountScore = 1.0f
        if (!result.isDeviceSecure) accountScore -= 0.5f
        if (result.hasUnknownAccessibility) accountScore -= 0.5f

        val totalScore = ((integrityScore + networkScore + accountScore) / 3f).coerceIn(0f, 1f)
        val currentXp = (totalScore * 1000).toInt() // Max XP 1000

        // Determine Level and Rank based on real XP
        return when {
            currentXp >= 900 -> RankData(
                levelNumber = 5,
                rankTitle = "Privacy Shield",
                rankDescription = "Elite Device Hardening Achieved",
                currentXp = currentXp,
                maxXp = 1000,
                deviceIntegrity = integrityScore,
                accountArmor = accountScore,
                networkShield = networkScore,
                nextMissionText = "Maintain perfect score for 7 days.",
                nextXpReward = 50,
                themeColor = androidx.compose.ui.graphics.Color(0xFF8B5CF6) // Purple
            )
            currentXp >= 750 -> RankData(
                levelNumber = 4,
                rankTitle = "Ghost Mode",
                rankDescription = "Advanced Network & Account Security",
                currentXp = currentXp,
                maxXp = 1000,
                deviceIntegrity = integrityScore,
                accountArmor = accountScore,
                networkShield = networkScore,
                nextMissionText = "Disable any active proxies/CAs.",
                nextXpReward = 150,
                themeColor = androidx.compose.ui.graphics.Color(0xFF3B82F6) // Blue
            )
            currentXp >= 500 -> RankData(
                levelNumber = 3,
                rankTitle = "White Hat Hero",
                rankDescription = "Solid Baseline Defense",
                currentXp = currentXp,
                maxXp = 1000,
                deviceIntegrity = integrityScore,
                accountArmor = accountScore,
                networkShield = networkScore,
                nextMissionText = "Ensure Screen Lock is enabled.",
                nextXpReward = 250,
                themeColor = androidx.compose.ui.graphics.Color(0xFF10B981) // Green
            )
            currentXp >= 250 -> RankData(
                levelNumber = 2,
                rankTitle = "Fortress Builder",
                rankDescription = "Basic Security Established",
                currentXp = currentXp,
                maxXp = 1000,
                deviceIntegrity = integrityScore,
                accountArmor = accountScore,
                networkShield = networkScore,
                nextMissionText = "Turn off Developer Options/ADB.",
                nextXpReward = 250,
                themeColor = androidx.compose.ui.graphics.Color(0xFFF59E0B) // Amber
            )
            else -> RankData(
                levelNumber = 1,
                rankTitle = "Cyber Novice",
                rankDescription = "Device highly vulnerable to attacks.",
                currentXp = currentXp,
                maxXp = 1000,
                deviceIntegrity = integrityScore,
                accountArmor = accountScore,
                networkShield = networkScore,
                nextMissionText = "Resolve critical system warnings immediately.",
                nextXpReward = 250,
                themeColor = androidx.compose.ui.graphics.Color(0xFFEF4444) // Red
            )
        }
    }
}
