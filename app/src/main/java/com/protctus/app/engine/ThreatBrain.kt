package com.protctus.app.engine

/**
 * ThreatBrain has been REMOVED as an active analysis engine.
 *
 * All threat verdicts now originate exclusively from the multi-engine
 * consensus pipeline in ThreatCorrelationEngine, which aggregates:
 *   - Quark (behavioral analysis)
 *   - ClamAV (signature matching)
 *   - YARA (pattern matching)
 *   - Androguard (structural/manifest/DEX analysis)
 *
 * The data classes below are retained because they are used across
 * multiple modules (ScannerViewModel, DeepScannerService, FileScanner,
 * DeepScanViewModel, SentinelService).
 */

data class EliteThreat(
    val packageName: String,
    val appName: String,
    val riskScore: Int,
    val detectedAnomalies: List<String>
)

data class ScanThreat(
    val appName: String,
    val packageName: String,
    val threatType: String,
    val riskScore: String,
    val detailedReason: String
)
