package com.protctus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_file_cache")
data class ScannedFileCache(
    @PrimaryKey val filePath: String,
    val lastModified: Long,
    val fileSize: Long,
    val mimeType: String = "",
    val sha256Hash: String? = null,
    val verdictDecision: String = "CLEAN",
    val verdictScore: Float = 0.0f,
    val isMalicious: Boolean,
    val scanTimestamp: Long
)
