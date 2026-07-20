package com.protctus.app.neuroshield.engine

/**
 * NeuroShield Threat Verdict — the final output of the analysis pipeline.
 *
 * Every domain analysis produces exactly one [ThreatVerdict] containing
 * the verdict level, confidence score, human-readable explanation,
 * triggered lexical indicators, and a recommended action.
 */
data class ThreatVerdict(
    /** Final threat classification. */
    val verdict: VerdictLevel,

    /** Confidence score in [0.0, 1.0]. Higher = more confident in the verdict. */
    val confidence: Float,

    /** Human-readable explanation of why this verdict was reached. */
    val explanation: String,

    /** List of lexical/structural indicators that contributed to the score. */
    val triggeredIndicators: List<String>,

    /** Recommended action for the user or the VPN engine. */
    val recommendedAction: String,

    /** Unix timestamp (milliseconds) of when the verdict was produced. */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Threat classification levels.
 *
 * Discriminant values are stable for serialization and Room storage.
 */
enum class VerdictLevel(val code: Int) {
    /** No threat detected. Domain appears legitimate. */
    SAFE(0),

    /** Some suspicious indicators detected but insufficient confidence to block. */
    SUSPICIOUS(1),

    /** High-confidence phishing detection. Domain should be blocked. */
    PHISHING(2),

    /** Inconclusive — reserved for future Phase 3 cloud verification. */
    REQUIRES_NETWORK_VERIFICATION(3);

    companion object {
        /** Reconstruct from stored integer code. Defaults to SAFE for unknown values. */
        fun fromCode(code: Int): VerdictLevel =
            entries.firstOrNull { it.code == code } ?: SAFE
    }
}

/**
 * Snapshot of the NeuroShield engine's operational status.
 */
data class EngineStatus(
    /** Whether the ONNX model is loaded and ready for inference. */
    val onnxReady: Boolean,

    /** Whether the heuristic engines (BK-Tree, Markov, Bloom) are initialized. */
    val heuristicsReady: Boolean,

    /** Whether the VPN service is currently active. */
    val vpnActive: Boolean,

    /** ONNX model file size in bytes (0 if not loaded). */
    val modelSizeBytes: Long,

    /** Number of features the model expects. */
    val featureCount: Int,

    /** Current engine mode. */
    val mode: EngineMode
)

/**
 * Engine operating modes.
 */
enum class EngineMode {
    /** Full pipeline: ONNX + heuristics. */
    FULL,

    /** ONNX load failed; heuristics-only mode. */
    DEGRADED,

    /** Engine is initializing. */
    INITIALIZING,

    /** Engine is stopped. */
    STOPPED
}
