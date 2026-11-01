package com.protctus.app.engine.quark

/**
 * Result of a single Quark rule evaluation against an APK.
 */
data class QuarkRuleResult(
    val rule: QuarkRule,
    val confidence: Int,
    val confidencePercent: Int,
    val weightedScore: Double,
    val matchedPermissions: List<String>,
    val matchedApis: List<ApiTarget>,
    val stageResults: List<Boolean>
) {
    val threatLevel: ThreatLevel
        get() = when {
            confidencePercent >= 80 -> ThreatLevel.CRITICAL
            confidencePercent >= 60 -> ThreatLevel.HIGH
            confidencePercent >= 40 -> ThreatLevel.MEDIUM
            confidencePercent >= 20 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }
}

/**
 * Overall result of Quark behavioral analysis on a single APK.
 */
data class QuarkScanResult(
    val packageName: String,
    val appLabel: String,
    val totalRulesEvaluated: Int,
    val triggeredRules: List<QuarkRuleResult>,
    val overallThreatScore: Double,
    val overallThreatLevel: ThreatLevel,
    val scanDurationMs: Long,
    val engineVersion: String = ENGINE_VERSION
) {
    /** Number of rules that triggered (confidence >= 40%) */
    val triggeredRuleCount: Int get() = triggeredRules.size

    /** Top 3 most critical detections */
    val topThreats: List<QuarkRuleResult>
        get() = triggeredRules
            .sortedByDescending { it.weightedScore }
            .take(3)

    /** All unique labels from triggered rules */
    val detectedBehaviorLabels: Set<String>
        get() = triggeredRules.flatMap { it.rule.labels }.toSet()

    /** Summary string for UI display */
    val summary: String
        get() = when (overallThreatLevel) {
            ThreatLevel.CRITICAL -> "Critical behavioral threats detected. ${triggeredRuleCount} suspicious patterns found."
            ThreatLevel.HIGH -> "High-risk behaviors identified. ${triggeredRuleCount} rules triggered."
            ThreatLevel.MEDIUM -> "Moderate behavioral risk. ${triggeredRuleCount} patterns matched."
            ThreatLevel.LOW -> "Minor behavioral anomalies. ${triggeredRuleCount} low-confidence matches."
            ThreatLevel.NONE -> "No behavioral threats detected."
        }

    companion object {
        const val ENGINE_VERSION = "quark-protctus-1.0"

        /** Create an empty result for a clean APK */
        fun clean(packageName: String, appLabel: String, rulesEvaluated: Int, durationMs: Long) =
            QuarkScanResult(
                packageName = packageName,
                appLabel = appLabel,
                totalRulesEvaluated = rulesEvaluated,
                triggeredRules = emptyList(),
                overallThreatScore = 0.0,
                overallThreatLevel = ThreatLevel.NONE,
                scanDurationMs = durationMs
            )
    }
}

/**
 * Threat level classification — matches Quark's LEVEL_INFO enum
 * from quark/utils/weight.py
 */
enum class ThreatLevel(val displayName: String, val numericScore: Int) {
    NONE("Clean", 0),
    LOW("Low Risk", 25),
    MEDIUM("Moderate Risk", 50),
    HIGH("High Risk", 75),
    CRITICAL("Critical", 100);

    companion object {
        /**
         * Calculate threat level from weight ratio.
         * Adapted from Quark's Weight.calculate() in weight.py:
         *   - level1 threshold = score_sum / 16
         *   - level2 threshold = score_sum / 8
         *   - level3 threshold = score_sum / 4
         *   - level4 threshold = score_sum / 2
         *   - above level4 = High Risk
         */
        fun fromWeightRatio(weightSum: Double, scoreSum: Double): ThreatLevel {
            if (scoreSum <= 0.0 || weightSum <= 0.0) return NONE
            val ratio = weightSum / scoreSum
            return when {
                ratio > 0.5 -> CRITICAL
                ratio > 0.25 -> HIGH
                ratio > 0.125 -> MEDIUM
                ratio > 0.0625 -> LOW
                else -> NONE
            }
        }
    }
}
