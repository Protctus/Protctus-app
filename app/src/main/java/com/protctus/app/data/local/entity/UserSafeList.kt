package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_safe_list")
data class UserSafeList(
    @PrimaryKey
    val domain: String,
    val addedAt: Long = System.currentTimeMillis()
)
