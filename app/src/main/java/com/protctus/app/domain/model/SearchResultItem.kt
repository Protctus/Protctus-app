package com.protctus.app.domain.model

import com.protctus.app.data.local.entity.NotificationEntity

data class AppFeature(
    val title: String, 
    val route: String, 
    val tags: List<String>
)

sealed class SearchResultItem {
    data class Feature(val appFeature: AppFeature) : SearchResultItem()
    data class ThreatLog(val log: NotificationEntity) : SearchResultItem()
}
