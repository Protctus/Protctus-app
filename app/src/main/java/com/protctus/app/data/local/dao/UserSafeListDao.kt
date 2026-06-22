package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.protctus.app.data.local.entity.UserSafeList

@Dao
interface UserSafeListDao {
    @Query("SELECT EXISTS(SELECT 1 FROM user_safe_list WHERE domain = :domain LIMIT 1)")
    suspend fun isDomainSafe(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsSafe(userSafeList: UserSafeList)
}
