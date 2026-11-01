package com.protctus.app.domain.usecase

import com.protctus.app.data.local.dao.NotificationDao
import com.protctus.app.domain.model.AppFeature
import com.protctus.app.domain.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class GlobalSearchUseCase(
    private val notificationDao: NotificationDao
) {
    // Local In-Memory Registry representing existing features
    private val staticRegistry = listOf(
        AppFeature("Leaderboard", "leaderboard_route", listOf("rank", "score", "top", "competition")),
        AppFeature("Daily Quests", "quests_route", listOf("xp", "missions", "rewards", "earn")),
        AppFeature("Smart Scan", "scan_route", listOf("virus", "malware", "security", "threat", "scan")),
        AppFeature("Settings", "settings_route", listOf("preferences", "account", "profile", "password"))
    )

    fun performSearch(query: String): Flow<List<SearchResultItem>> {
        val normalizedQuery = query.trim().lowercase()
        
        return notificationDao.searchNotifications(normalizedQuery).map { threatEntities ->
            val staticResults = if (normalizedQuery.isEmpty()) {
                emptyList()
            } else {
                staticRegistry.filter {
                    it.title.lowercase().contains(normalizedQuery) || it.tags.any { tag -> tag.contains(normalizedQuery) }
                }.map { SearchResultItem.Feature(it) }
            }

            val threatResults = threatEntities.map { SearchResultItem.ThreatLog(it) }

            // Merge both distinct domains
            staticResults + threatResults
        }.flowOn(Dispatchers.Default)
    }
}
