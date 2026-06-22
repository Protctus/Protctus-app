package com.protctus.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(recentSearch: RecentSearch)
    
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<RecentSearch>>
    
    @Query("DELETE FROM recent_searches WHERE id = :id")
    suspend fun deleteSearchQuery(id: Int)
    
    @Query("DELETE FROM recent_searches")
    suspend fun clearAllRecentSearches()
}
