package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.protctus.app.domain.model.NotificationType

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val type: NotificationType,
    val timestamp: Long,
    val isRead: Boolean
)
