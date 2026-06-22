package com.protctus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val profileImageUri: String? = null,
    val rank: String = "ROOKIE"
)
