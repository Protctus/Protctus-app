package com.protctus.app.engine.correlation

enum class EvidenceCategory {
    SIGNATURE_EXACT, SIGNATURE_HEURISTIC, BEHAVIORAL_DYNAMIC,
    STRUCTURAL_ANOMALY, PRIVACY_ABUSE, PACKER_OBFUSCATION, SYSTEM_INTEGRITY
}

enum class EvidenceCluster {
    UI_ABUSE, PRIVILEGE_ESCALATION, DATA_EXFILTRATION, DYNAMIC_STEALTH,
    STRUCTURAL_ANOMALY, THREAT_SIGNATURE, ORIGIN_CONTEXT
}

enum class SeverityBand { INFO, LOW, MEDIUM, HIGH, CRITICAL }
enum class CertaintyLevel { VERY_LOW, LOW, MEDIUM, HIGH, DEFINITIVE }
enum class VisibilityLevel { FULL, PARTIAL, OBFUSCATED, BLOCKED, FAILED }

data class EvidenceSignal(
    val engine: String,
    val category: EvidenceCategory,
    val cluster: EvidenceCluster,
    val confidence: Float,
    val severity: SeverityBand,
    val certainty: CertaintyLevel,
    val evidence: List<String>,
    val visibility: VisibilityLevel
)

enum class ThreatVerdict(val score: Int, val displayName: String) {
    CLEAN(0, "Clean"),
    LOW_RISK(25, "Low Risk"),
    INCONCLUSIVE(30, "Inconclusive"),
    PARTIAL_ANALYSIS(35, "Partial Analysis"),
    NEEDS_REVIEW(40, "Needs Review"),
    PACKED_APK(50, "Packed / Obfuscated"),
    SUSPICIOUS(60, "Suspicious"),
    HIGH_RISK(80, "High Risk"),
    HIGH_CONFIDENCE_MALWARE(100, "Malware"),
    LIMITED_VISIBILITY(-1, "Limited Visibility")
}

data class CorrelatedVerdict(
    val packageName: String,
    val appLabel: String,
    val finalVerdict: ThreatVerdict,
    val finalProbability: Float,
    val explanation: VerdictExplanation,
    val signals: List<EvidenceSignal>,
    val scanDurationMs: Long
) {
    val isActionable: Boolean get() = finalVerdict.score >= ThreatVerdict.SUSPICIOUS.score
    val displayRiskScore: Float get() = (finalProbability * 10f).coerceIn(0f, 10f)
    val shortSummary: String get() = explanation.topReasons.joinToString(". ")
}

data class VerdictExplanation(
    val topReasons: List<String>,
    val permissionRisks: List<String> = emptyList(),
    val apiRisks: List<String> = emptyList(),
    val signatureMatches: List<String> = emptyList(),
    val behavioralIndicators: List<String> = emptyList(),
    val structuralAnomalies: List<String> = emptyList(),
    val certificateIssues: List<String> = emptyList(),
    val mitigatingFactors: List<String> = emptyList()
)

data class EngineConsensus(
    val enginesRun: Int,
    val enginesTriggered: Int,
    val agreementRatio: Float,
    val triggeredEngines: List<String>
)

data class EngineResult(
    val engineName: String,
    val triggered: Boolean,
    val rawScore: Float,
    val normalizedScore: Float,
    val details: String,
    val weight: Float
)
