package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.protctus.app.data.local.entity.ThreatTelemetry

@Dao
interface ThreatTelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(telemetry: ThreatTelemetry)

    @Query("SELECT * FROM threat_telemetry ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingTelemetry(limit: Int): List<ThreatTelemetry>

    @Query("DELETE FROM threat_telemetry WHERE id IN (:ids)")
    suspend fun deleteTelemetryByIds(ids: List<Long>)
}
