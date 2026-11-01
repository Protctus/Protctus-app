package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.protctus.app.data.local.entity.ScannedFileCache

@Dao
interface ScannedFileCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: ScannedFileCache)

    @Query("SELECT * FROM scanned_file_cache WHERE filePath = :filePath LIMIT 1")
    suspend fun getCachedFile(filePath: String): ScannedFileCache?

    @Query("SELECT * FROM scanned_file_cache WHERE sha256Hash = :hash LIMIT 1")
    suspend fun getCachedBySha256(hash: String): ScannedFileCache?

    @Query("SELECT * FROM scanned_file_cache WHERE isMalicious = 1 AND scanTimestamp >= :since ORDER BY scanTimestamp DESC")
    suspend fun getRecentThreats(since: Long): List<ScannedFileCache>

    @Query("DELETE FROM scanned_file_cache WHERE scanTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
