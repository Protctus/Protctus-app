package com.protctus.app.neuroshield.engine

import android.util.Log

/**
 * Verdict Engine — Score Fusion and Threat Classification
 *
 * Combines the ONNX model's phishing probability with the existing heuristic
 * score to produce a final [ThreatVerdict]. Generates human-readable
 * explanations and identifies which lexical indicators triggered.
 *
 * Thresholds are calibrated from the training pipeline's MCC-optimized
 * threshold analysis across 5 cross-dataset splits.
 */
class VerdictEngine {

    companion object {
        private const val TAG = "VerdictEngine"

        // ── Classification Thresholds ──
        // Derived from training pipeline threshold optimization (maximizing MCC).
        // The ONNX model outputs P(phishing) — these are applied to the fused score.

        /** Fused score above this → PHISHING verdict. */
        const val THRESHOLD_PHISHING = 0.65f

        /** Fused score above this → SUSPICIOUS verdict. */
        const val THRESHOLD_SUSPICIOUS = 0.30f

        // ── Score Fusion Weights ──
        // ONNX model is the primary signal; heuristics provide supplemental context.

        /** Weight of the ONNX model score in the fused result. */
        private const val WEIGHT_ONNX = 0.70f

        /** Weight of the heuristic score in the fused result. */
        private const val WEIGHT_HEURISTIC = 0.30f

        /** When ONNX is unavailable (DEGRADED mode), heuristic score is scaled by this. */
        private const val DEGRADED_SCALE = 0.90f

        // ── Feature Index Constants (for indicator generation) ──
        private const val IDX_DOMAIN_LENGTH = 0
        private const val IDX_SUBDOMAIN_COUNT = 1
        private const val IDX_DOMAIN_ENTROPY = 2
        private const val IDX_DIGIT_RATIO = 3
        private const val IDX_NUM_HYPHENS = 8
        private const val IDX_TLD_LENGTH = 9
        private const val IDX_MAX_CONSONANT_RUN = 10
        private const val IDX_HAS_PUNYCODE = 11
        private const val IDX_IS_RAW_IP = 12
        private const val IDX_SLD_LENGTH = 18
        private const val IDX_SLD_ENTROPY = 19
        private const val IDX_IS_FREE_HOSTING = 23
        private const val IDX_HAS_DOUBLE_HYPHEN = 27
        private const val IDX_SLD_DGA_SCORE = 33
        private const val IDX_SLD_RARE_CHAR_COUNT = 35
        private const val IDX_SUBDOMAIN_ENTROPY = 41
        private const val IDX_CHAR_TRANSITION_VARIANCE = 44
        private const val IDX_DOMAIN_DEPTH = 45
    }

    private val featureExtractor = LexicalFeatureExtractor()

    /**
     * Produce a full threat verdict for a domain.
     *
     * @param domain The domain hostname to analyze.
     * @param onnxScore Phishing probability from [NeuroShieldEngine.infer] (0.0 if unavailable).
     * @param heuristicScore Score from the existing heuristic pipeline (0.0–0.80).
     * @param onnxAvailable Whether the ONNX engine is ready.
     * @return Complete [ThreatVerdict] with verdict, confidence, explanation, and indicators.
     */
    fun evaluate(
        domain: String,
        onnxScore: Float,
        heuristicScore: Float,
        onnxAvailable: Boolean = true
    ): ThreatVerdict {
        val features = featureExtractor.extract(domain)
        return evaluateWithFeatures(domain, onnxScore, heuristicScore, features, onnxAvailable)
    }

    /**
     * Produce a verdict from pre-extracted features (avoids double extraction).
     */
    fun evaluateWithFeatures(
        domain: String,
        onnxScore: Float,
        heuristicScore: Float,
        features: FloatArray,
        onnxAvailable: Boolean = true
    ): ThreatVerdict {
        // ── Score Fusion ──
        val fusedScore = if (onnxAvailable && onnxScore > 0.001f) {
            (onnxScore * WEIGHT_ONNX + heuristicScore * WEIGHT_HEURISTIC).coerceIn(0f, 1f)
        } else {
            // DEGRADED mode: heuristics only, slightly scaled down for conservatism
            (heuristicScore * DEGRADED_SCALE).coerceIn(0f, 1f)
        }

        // ── Verdict Classification ──
        val verdict = when {
            fusedScore >= THRESHOLD_PHISHING -> VerdictLevel.PHISHING
            fusedScore >= THRESHOLD_SUSPICIOUS -> VerdictLevel.SUSPICIOUS
            else -> VerdictLevel.SAFE
        }

        // ── Triggered Indicators ──
        val indicators = identifyIndicators(features, domain)

        // ── Explanation ──
        val explanation = buildExplanation(verdict, fusedScore, onnxScore, heuristicScore, indicators, onnxAvailable)

        // ── Recommended Action ──
        val action = when (verdict) {
            VerdictLevel.PHISHING -> "Domain blocked. Access denied to protect against phishing."
            VerdictLevel.SUSPICIOUS -> "Proceed with caution. Verify the site's legitimacy before entering any information."
            VerdictLevel.SAFE -> "No threats detected."
            VerdictLevel.REQUIRES_NETWORK_VERIFICATION -> "Additional verification required."
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
     * Identify which lexical indicators contributed to a suspicious score.
     * Only reports indicators that are outside normal ranges.
     */
    private fun identifyIndicators(features: FloatArray, domain: String): List<String> {
        if (features.size < LexicalFeatureExtractor.FEATURE_COUNT) return emptyList()

        val indicators = mutableListOf<String>()

        if (features[IDX_IS_RAW_IP] > 0.5f)
            indicators.add("Raw IP address used instead of domain name")

        if (features[IDX_HAS_PUNYCODE] > 0.5f)
            indicators.add("Punycode/IDN domain (potential homograph attack)")

        if (features[IDX_DOMAIN_LENGTH] > 50f)
            indicators.add("Unusually long domain (${features[IDX_DOMAIN_LENGTH].toInt()} chars)")

        if (features[IDX_DOMAIN_ENTROPY] > 4.2f)
            indicators.add("High domain entropy (${String.format("%.1f", features[IDX_DOMAIN_ENTROPY])}) — random-looking")

        if (features[IDX_SUBDOMAIN_COUNT] >= 3f)
            indicators.add("Deep subdomain nesting (${features[IDX_SUBDOMAIN_COUNT].toInt()} levels)")

        if (features[IDX_DOMAIN_DEPTH] >= 5f)
            indicators.add("Excessive domain depth (${features[IDX_DOMAIN_DEPTH].toInt()} labels)")

        if (features[IDX_NUM_HYPHENS] >= 3f)
            indicators.add("Multiple hyphens in domain (${features[IDX_NUM_HYPHENS].toInt()})")

        if (features[IDX_HAS_DOUBLE_HYPHEN] > 0.5f)
            indicators.add("Double-hyphen pattern detected")

        if (features[IDX_IS_FREE_HOSTING] > 0.5f)
            indicators.add("Hosted on free/shared hosting platform")

        if (features[IDX_SLD_DGA_SCORE] > 0.7f)
            indicators.add("SLD resembles DGA-generated domain")

        if (features[IDX_MAX_CONSONANT_RUN] >= 5f)
            indicators.add("Long consonant sequence (${features[IDX_MAX_CONSONANT_RUN].toInt()}) — keyboard-smash pattern")

        if (features[IDX_DIGIT_RATIO] > 0.3f)
            indicators.add("High digit ratio (${String.format("%.0f", features[IDX_DIGIT_RATIO] * 100)}%)")

        if (features[IDX_SLD_RARE_CHAR_COUNT] >= 2f)
            indicators.add("Rare characters in SLD (${features[IDX_SLD_RARE_CHAR_COUNT].toInt()})")

        if (features[IDX_CHAR_TRANSITION_VARIANCE] > 500f)
            indicators.add("High character transition variance — random structure")

        return indicators
    }

    private fun buildExplanation(
        verdict: VerdictLevel,
        fusedScore: Float,
        onnxScore: Float,
        heuristicScore: Float,
        indicators: List<String>,
        onnxAvailable: Boolean
    ): String {
        val sb = StringBuilder()

        when (verdict) {
            VerdictLevel.PHISHING -> sb.append("High-confidence phishing detection.")
            VerdictLevel.SUSPICIOUS -> sb.append("Suspicious domain characteristics detected.")
            VerdictLevel.SAFE -> sb.append("Domain appears legitimate.")
            VerdictLevel.REQUIRES_NETWORK_VERIFICATION -> sb.append("Inconclusive — further verification needed.")
        }

        sb.append(" Score: ${String.format("%.0f", fusedScore * 100)}%.")

        if (onnxAvailable) {
            sb.append(" ML: ${String.format("%.0f", onnxScore * 100)}%, Heuristic: ${String.format("%.0f", heuristicScore * 100)}%.")
        } else {
            sb.append(" (Heuristic-only mode)")
        }

        if (indicators.isNotEmpty()) {
            sb.append(" Indicators: ${indicators.size} triggered.")
        }

        return sb.toString()
    }
}
