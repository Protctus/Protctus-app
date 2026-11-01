package com.protctus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_alerts")
data class ThreatAlert(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    val priority: String, // High, Medium, Low
    val threatType: String, // Malware, Spyware, Phishing, Environment, etc.
    val priorityColorHex: String, // e.g., #EF4444
    val timestamp: Long
)
