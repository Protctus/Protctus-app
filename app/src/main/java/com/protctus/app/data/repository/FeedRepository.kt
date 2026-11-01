package com.protctus.app.data.repository

import com.protctus.app.data.local.dao.NotificationDao
import com.protctus.app.data.local.entity.NotificationEntity
import com.protctus.app.domain.model.NotificationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedRepository(
    private val notificationDao: NotificationDao
) {
    /**
     * Strictly Offline Room insertion masking SQL dependencies from Gamification / Scanners
     */
    suspend fun logEvent(title: String, description: String, type: NotificationType) = withContext(Dispatchers.IO) {
        val entry = NotificationEntity(
            title = title,
            description = description,
            type = type,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        notificationDao.insertNotification(entry)
    }

    /**
     * Passive hot Flow to track notifications ordered by strictly descending time.
     */
    fun getFullFeed() = notificationDao.getAllNotifications()
}
