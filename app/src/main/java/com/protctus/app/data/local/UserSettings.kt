package com.protctus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val notificationsEnabled: Boolean = true,
    val biometricLoginEnabled: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    val dataSharingEnabled: Boolean = false,
    val adTrackingEnabled: Boolean = false
)
