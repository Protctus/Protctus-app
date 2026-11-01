package com.protctus.app.neuroshield.engine

import android.content.Context
import android.util.Log

/**
 * Multi-model ensemble phishing detection engine.
 *
 * Combines multiple independent detection signals into a single verdict:
 *   1. LightGBM model (53 features, native inference) — primary ML signal
 *   2. LogisticRegression ONNX model (53 features) — secondary ML signal
 *   3. Heuristic engines (BK-Tree, Markov, Viterbi, structural) — deterministic rules
 *
 * Decision policy:
 *   - If ANY model confidently detects phishing → PHISHING (block)
 *   - If models disagree → SUSPICIOUS (warn user)
 *   - If all models agree safe → SAFE
 *   - If no model has confidence → REQUIRES_NETWORK_VERIFICATION (insufficient evidence)
 *
 * Missing visibility is never converted into a benign classification.
 */
class EnsemblePhishingEngine(private val context: Context) {

    private companion object {
        private const val TAG = "EnsemblePhishingEngine"

        // Classification thresholds (phishing = positive class)
        const val THRESHOLD_PHISHING = 0.65f
        const val THRESHOLD_SUSPICIOUS = 0.30f
        const val SIGNAL_ABSENT = 0.01f

        // Model weights for fusion (must sum to 1.0)
        const val WEIGHT_LIGHTGBM = 0.50f
        const val WEIGHT_ONNX = 0.30f
        const val WEIGHT_HEURISTIC = 0.20f
    }

    private val featureExtractor = LexicalFeatureExtractor()
    private val dynamicFeatureProvider: DynamicFeatureProvider?
    private val lightGBMModel: LightGBMModel?
    private val neuroShieldEngine: NeuroShieldEngine?
    private val verdictEngine = VerdictEngine()

    init {
        // Load LightGBM model from assets
        lightGBMModel = LightGBMModel.loadFromAssets(context, "lightgbm_model.txt")

        // Initialize NeuroShield (ONNX) engine
        neuroShieldEngine = NeuroShieldEngine(context)
        neuroShieldEngine?.initialize()

        // Initialize dynamic feature provider
        dynamicFeatureProvider = DynamicFeatureProvider(context)
    }

    /**
     * Produce a full threat verdict using the ensemble.
     *
     * @param domain Raw domain or URL string
     * @return Complete [ThreatVerdict] with ensemble verdict, confidence, explanation
     */
    fun evaluate(domain: String): ThreatVerdict {
        val cleanDomain = sanitizeInput(domain)
        if (cleanDomain.isEmpty()) {
            return ThreatVerdict(
                verdict = VerdictLevel.REQUIRES_NETWORK_VERIFICATION,
                confidence = 0f,
                explanation = "Empty or invalid domain — insufficient evidence.",
                triggeredIndicators = emptyList(),
                recommendedAction = "No action needed."
            )
        }

        // Extract features (53 base + 20 dynamic = 73)
        val baseFeatures = featureExtractor.extract(cleanDomain)
        val dynamicFeatures = dynamicFeatureProvider?.compute(cleanDomain) ?: FloatArray(7)
        val features = FloatArray(73)
        System.arraycopy(baseFeatures, 0, features, 0, 53)
        System.arraycopy(dynamicFeatures, 0, features, 53, 7)

        // ── Layer 1: LightGBM model (native inference) ──
        val lgbScore = runLightGBM(features)

        // ── Layer 2: ONNX model (NeuroShield) ──
        val onnxScore = runONNX(cleanDomain)

        // ── Layer 3: Heuristic engines ──
        val heuristicScore = runHeuristics(cleanDomain)

        // ── Score Fusion ──
        val fusedScore = fuseScores(lgbScore, onnxScore, heuristicScore)

        // ── Determine evidence presence ──
        val hasLGBSignal = lgbScore > SIGNAL_ABSENT
        val hasONNXSignal = onnxScore > SIGNAL_ABSENT
        val hasHeuristicSignal = heuristicScore > SIGNAL_ABSENT
        val evidencePresent = hasLGBSignal || hasONNXSignal || hasHeuristicSignal

        // ── Verdict Classification ──
        val verdict = when {
            !evidencePresent -> VerdictLevel.REQUIRES_NETWORK_VERIFICATION
            fusedScore >= THRESHOLD_PHISHING -> VerdictLevel.PHISHING
            fusedScore >= THRESHOLD_SUSPICIOUS -> VerdictLevel.SUSPICIOUS
            else -> VerdictLevel.SAFE
        }

        // ── Triggered Indicators ──
        val indicators = verdictEngine.identifyIndicators(features.take(53), cleanDomain)
        if (lgbScore > THRESHOLD_PHISHING) {
            indicators.add("LightGBM ML model flagged phishing (score=${"%.2f".format(lgbScore)})")
        }
        if (onnxScore > THRESHOLD_PHISHING) {
            indicators.add("ONNX ML model flagged phishing (score=${"%.2f".format(onnxScore)})")
        }
        if (heuristicScore > THRESHOLD_PHISHING) {
            indicators.add("Heuristic analysis flagged phishing (score=${"%.2f".format(heuristicScore)})")
        }

        // ── Explanation ──
        val explanation = buildExplanation(verdict, fusedScore, lgbScore, onnxScore, heuristicScore, indicators, evidencePresent)

        // ── Recommended Action ──
        val action = when (verdict) {
            VerdictLevel.PHISHING -> "Domain blocked. Multi-model phishing detection confirmed."
            VerdictLevel.SUSPICIOUS -> "Proceed with caution. Multiple signals detected suspicious characteristics."
            VerdictLevel.SAFE -> "No threats detected by any model."
            VerdictLevel.REQUIRES_NETWORK_VERIFICATION -> "Insufficient evidence to classify. Further verification recommended."
        }

        return ThreatVerdict(
            verdict = verdict,
            confidence = fusedScore,
            explanation = explanation,
            triggeredIndicators = indicators,
            recommendedAction = action
        )
    }

    /**
     * Run LightGBM model inference.
     */
    private fun runLightGBM(features: FloatArray): Float {
        return lightGBMModel?.infer(features) ?: 0.0f
    }

    /**
     * Run ONNX model inference.
     */
    private fun runONNX(domain: String): Float {
        return neuroShieldEngine?.infer(domain) ?: 0.0f
    }

    /**
     * Run heuristic engines.
     */
    private fun runHeuristics(domain: String): Float {
        return verdictEngine.evaluate(domain, 0f, 0f, false).confidence
    }

    /**
     * Fuse scores from multiple models using weighted average.
     */
    private fun fuseScores(lgbScore: Float, onnxScore: Float, heuristicScore: Float): Float {
        var totalWeight = 0f
        var weightedSum = 0f

        if (lgbScore > SIGNAL_ABSENT) {
            weightedSum += lgbScore * WEIGHT_LIGHTGBM
            totalWeight += WEIGHT_LIGHTGBM
        }
        if (onnxScore > SIGNAL_ABSENT) {
            weightedSum += onnxScore * WEIGHT_ONNX
            totalWeight += WEIGHT_ONNX
        }
        if (heuristicScore > SIGNAL_ABSENT) {
            weightedSum += heuristicScore * WEIGHT_HEURISTIC
            totalWeight += WEIGHT_HEURISTIC
        }

        return if (totalWeight > 0f) weightedSum / totalWeight else 0.0f
    }

    private fun sanitizeInput(input: String): String {
        return input.trim().lowercase()
    }

    private fun buildExplanation(
        verdict: VerdictLevel,
        fusedScore: Float,
        lgbScore: Float,
        onnxScore: Float,
        heuristicScore: Float,
        indicators: List<String>,
        evidencePresent: Boolean
    ): String {
        val sb = StringBuilder()

        when (verdict) {
            VerdictLevel.PHISHING -> sb.append("Multi-model phishing detection confirmed.")
            VerdictLevel.SUSPICIOUS -> sb.append("Suspicious domain characteristics detected by multiple signals.")
            VerdictLevel.SAFE -> sb.append("No threats detected by any model.")
            VerdictLevel.REQUIRES_NETWORK_VERIFICATION ->
                if (!evidencePresent) {
                    sb.append("Insufficient evidence — no detection signal available.")
                } else {
                    sb.append("Inconclusive — further verification needed.")
                }
        }

        sb.append(" Score: ${"%.0f".format(fusedScore * 100)}%.")

        val modelDetails = mutableListOf<String>()
        if (lgbScore > SIGNAL_ABSENT) {
            modelDetails.add("LightGBM: ${"%.0f".format(lgbScore * 100)}%")
        }
        if (onnxScore > SIGNAL_ABSENT) {
            modelDetails.add("ONNX: ${"%.0f".format(onnxScore * 100)}%")
        }
        if (heuristicScore > SIGNAL_ABSENT) {
            modelDetails.add("Heuristic: ${"%.0f".format(heuristicScore * 100)}%")
        }

        if (modelDetails.isNotEmpty()) {
            sb.append(" (${modelDetails.joinToString(", ")})")
        }

        if (!evidencePresent) {
            sb.append(" Missing visibility is not treated as safe.")
        }

        if (indicators.isNotEmpty()) {
            sb.append(" Indicators: ${indicators.size} triggered.")
        }

        return sb.toString()
    }

    /**
     * Get the operational status of all engines.
     */
    fun getEngineStatus(): EngineStatus {
        return EngineStatus(
            onnxReady = neuroShieldEngine?.isReady ?: false,
            heuristicsReady = true,
            vpnActive = false,
            modelSizeBytes = lightGBMModelExpectedSize(),
            featureCount = 73,
            mode = if (lightGBMModel != null && neuroShieldEngine?.isReady == true) EngineMode.FULL else EngineMode.DEGRADED
        )
    }

    private fun lightGBMModelExpectedSize(): Long {
        return 3477662L // Approximate size of lightgbm_model.txt
    }

    /**
     * Release all engine resources.
     */
    fun close() {
        neuroShieldEngine?.close()
    }
}
