package com.protctus.app.engine

import com.protctus.app.engine.correlation.CorrelatedVerdict

/**
 * Data structures used across the ProcMalwareEngine V2 Pipeline.
 * These are purely data contracts and contain no reasoning logic.
 */

data class FileEvidence(
    val fileName: String,
    val absolutePath: String,
    val extension: String,
    val fileSize: Long,
    val yaraMatches: List<String> = emptyList(),
    val clamAvMatches: List<String> = emptyList(),
    val entropy: Double? = null,
    val isApkInWrongPlace: Boolean = false,
    val hasDoubleExtension: Boolean = false,
    val hasRTLO: Boolean = false,
    val hasEOCDAtEnd: Boolean = false,
    val archiveRatio: Double? = null,
    val archiveTraversal: Boolean = false,
    val hasEmbeddedExecutable: Boolean = false
)

data class AppEvidence(
    val packageName: String,
    val appLabel: String,
    val isSystemApp: Boolean,
    val isSideloaded: Boolean,
    val correlationVerdict: CorrelatedVerdict
)

data class SystemEvidence(
    val unknownSourcesEnabled: Boolean,
    val adbEnabled: Boolean,
    val isRooted: Boolean
)

enum class EngineDecision(val displayName: String, val isActionable: Boolean) {
    CLEAN("Clean", false),
    LOW_RISK("Low Risk", false),
    SUSPICIOUS("Suspicious", true),
    HIGH_RISK("High Risk", true),
    MALWARE("Malware", true)
}

data class EngineVerdict(
    val decision: EngineDecision,
    val finalScore: Float,
    val confidence: Float,
    val explanation: String
)
