package com.protctus.app.data.repository

import com.protctus.app.data.local.RecentSearch
import com.protctus.app.data.local.RecentSearchDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import com.protctus.app.navigation.Route
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

data class SearchResultItemData(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: Route
)

class SearchRepository(private val recentSearchDao: RecentSearchDao) {

    val recentSearches: Flow<List<RecentSearch>> = recentSearchDao.getRecentSearches()

    // Cache for searchable items fetched dynamically from Firestore securely
    private var cachedSearchableItems: List<SearchResultItemData>? = null
    
    // Using lazy initialization to avoid Firebase initialization issues in environments like Compose Preview
    private val firestore by lazy { FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "protctus") }

    private val localFallbackSearchIndex = listOf(
        SearchResultItemData("Dashboard", "Your main security overview and rank up check.", Icons.Outlined.Dashboard, Route.Dashboard),
        SearchResultItemData("Rewards", "View your earned badges and upcoming rewards.", Icons.Outlined.WorkspacePremium, Route.Rewards),
        SearchResultItemData("Threat Feed", "Real-time updates on detected threats globally.", Icons.Outlined.BugReport, Route.ThreatFeed),
        SearchResultItemData("Daily Quests", "Complete missions to earn XP and defend your rank.", Icons.Outlined.EmojiEvents, Route.DailyQuests),
        SearchResultItemData("Leaderboard", "Compete against players globally.", Icons.Outlined.Leaderboard, Route.Leaderboard),
        SearchResultItemData("Smart Scan", "Quickly scan apps and device for threats.", Icons.Outlined.DocumentScanner, Route.SmartScan),
        SearchResultItemData("Settings", "Configure app preferences, security, and notifications.", Icons.Outlined.Settings, Route.Settings),
        SearchResultItemData("Account Security", "Manage your passwords, 2FA, and biometric settings.", Icons.Outlined.Security, Route.AccountSecurity),
        SearchResultItemData("Dark Web Scan", "Check if your email or passwords have been leaked.", Icons.Outlined.TravelExplore, Route.DarkWebScan),
        SearchResultItemData("Protctus Lab", "Test your knowledge on cybersecurity best practices.", Icons.Outlined.School, Route.LabChapters)
    )

    private suspend fun fetchDynamicallyIndexedItems(): List<SearchResultItemData> {
        if (cachedSearchableItems != null) return cachedSearchableItems!!

        return try {
            val snapshot = firestore.collection("search_index").get().await()
            val items = snapshot.documents.mapNotNull { doc ->
                val title = doc.getString("title") ?: return@mapNotNull null
                val description = doc.getString("description") ?: ""
                val routeName = doc.getString("routeName") ?: return@mapNotNull null
                
                // Route Mapping 
                val route = mapToRoute(routeName) ?: return@mapNotNull null
                
                // Optional icon override, defaulting to search
                val iconName = doc.getString("iconName") ?: "search"
                val icon = mapToIcon(iconName)

                SearchResultItemData(title, description, icon, route)
            }
            
            // If remote index is empty, gracefully fail-over to local static index for immediate functionality
            val finalItems = if (items.isNotEmpty()) items else localFallbackSearchIndex
            cachedSearchableItems = finalItems
            finalItems
        } catch (e: Exception) {
            e.printStackTrace()
            // Fail gracefully to local fallback index on network failure
            cachedSearchableItems = localFallbackSearchIndex 
            localFallbackSearchIndex
        }
    }

    private fun mapToRoute(routeName: String): Route? {
        return when (routeName) {
            "Dashboard" -> Route.Dashboard
            "Rewards" -> Route.Rewards
            "ThreatFeed" -> Route.ThreatFeed
            "DailyQuests" -> Route.DailyQuests
            "ThreatMap" -> Route.ThreatMap
            "FeaturesShortcut" -> Route.FeaturesShortcut
            "GetPremium" -> Route.GetPremium
            "Settings" -> Route.Settings
            "SmartScan" -> Route.SmartScan
            "DarkWebScan" -> Route.DarkWebScan
            "DeepScan" -> Route.DeepScan
            "SecurityQuiz" -> Route.LabChapters
            "EditProfile" -> Route.EditProfile
            "Leaderboard" -> Route.Leaderboard
            "HelpCenter", "ContactUs", "Faqs" -> Route.HelpCenter
            "TermsOfService" -> Route.TermsOfService
            "PrivacyPolicy" -> Route.PrivacyPolicy
            else -> null
        }
    }

    private fun mapToIcon(iconName: String): ImageVector {
        return when (iconName) {
            "smart_scan" -> Icons.Outlined.DocumentScanner
            "dashboard" -> Icons.Outlined.Dashboard
            "rewards" -> Icons.Outlined.WorkspacePremium
            "threat_feed" -> Icons.Outlined.BugReport
            "daily_quests" -> Icons.Outlined.EmojiEvents
            "leaderboard" -> Icons.Outlined.Leaderboard
            "settings" -> Icons.Outlined.Settings
            "account_security" -> Icons.Outlined.Security
            "dark_web_scan" -> Icons.Outlined.TravelExplore
            "security_quiz", "protctus_lab" -> Icons.Outlined.School
            else -> Icons.Outlined.Search
        }
    }

    suspend fun insertSearchQuery(query: String) {
        val recentSearch = RecentSearch(
            query = query,
            timestamp = System.currentTimeMillis()
        )
        recentSearchDao.insertSearchQuery(recentSearch)
    }

    suspend fun deleteSearchQuery(id: Int) {
        recentSearchDao.deleteSearchQuery(id)
    }

    suspend fun clearAllRecentSearches() {
        recentSearchDao.clearAllRecentSearches()
    }

    suspend fun searchItems(query: String): List<SearchResultItemData> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase()
        
        val dynamicItems = fetchDynamicallyIndexedItems()
        
        return dynamicItems.filter {
            it.title.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
    }
}
