package com.protctus.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreat(threat: ThreatAlert)
    
    @Query("SELECT * FROM threat_alerts ORDER BY timestamp DESC")
    fun getAllThreats(): Flow<List<ThreatAlert>>
    
    @Query("DELETE FROM threat_alerts WHERE id = :id")
    suspend fun deleteThreat(id: Int)
    
    @Query("DELETE FROM threat_alerts")
    suspend fun clearAllThreats()

    @Query("SELECT COUNT(*) FROM threat_alerts WHERE threatType = :threatType")
    suspend fun countByThreatType(threatType: String): Int
}
