package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_telemetry")
data class ThreatTelemetry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domainHashSha256: String,
    val confidenceScore: Float,
    val threatType: String, // e.g., "PHISHING", "MALWARE"
    val timestamp: Long,
    val isFalsePositive: Boolean = false
)
