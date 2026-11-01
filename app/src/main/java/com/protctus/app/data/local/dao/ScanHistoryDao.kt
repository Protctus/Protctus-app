package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.protctus.app.data.local.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScans(limit: Int): Flow<List<ScanHistoryEntity>>

    @Query("SELECT COUNT(*) FROM scan_history")
    fun getTotalScanCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM scan_history WHERE verdictCode >= 2") // 2 = PHISHING
    fun getBlockedThreatCount(): Flow<Long>

    @Query("DELETE FROM scan_history WHERE timestamp < :olderThanMs")
    suspend fun deleteOlderThan(olderThanMs: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()
}
