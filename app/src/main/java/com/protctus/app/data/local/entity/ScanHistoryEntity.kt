package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.protctus.app.neuroshield.engine.VerdictLevel

/**
 * Room entity representing a single domain analysis result by the NeuroShield engine.
 * We store this to compute stats and optionally show a history of recent checks.
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val domain: String,
    
    /** Stored as the integer code from [VerdictLevel.code]. */
    val verdictCode: Int,
    
    val confidence: Float,
    
    val explanation: String,
    
    /** Comma-separated list of indicators, or empty. */
    val triggeredIndicators: String,
    
    val timestamp: Long
)
