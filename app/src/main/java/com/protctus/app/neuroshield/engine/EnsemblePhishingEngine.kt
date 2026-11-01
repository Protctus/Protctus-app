package com.protctus.app.neuroshield.engine

import android.content.Context
import android.util.Log

/**
 * Multi-model ensemble phishing detection engine — 4-model ensemble.
 *
 * Combines multiple independent detection signals into a single verdict:
 *   1. Signature layer (exact known-phishing domains) — near-zero cost, highest confidence
 *   2. Calibrated ONNX model (LogisticRegression, 53 features) — primary ML signal, runs on every query
 *   3. LightGBM model (1000 trees, 53 features) — secondary ML, cascade band
 *   4. HistGradientBoosting model (ONNX, 53 features) — tertiary ML, cascade band
 *   5. Heuristic engines (BK-Tree, Markov, Viterbi, structural) — deterministic rules
 *
 * Decision policy (phishing = positive class):
 *   - Signature match → PHISHING (block) immediately
 *   - If ONNX is confident (safe <0.15 or phishing >0.85), skip LightGBM + HGB to save battery
 *   - Otherwise fuse calibrated ONNX + LightGBM + HGB + heuristics
 *   - Fused score >= THRESHOLD_PHISHING → PHISHING (block)
 *   - Fused score >= THRESHOLD_SUSPICIOUS → SUSPICIOUS (warn)
 *   - All models agree safe → SAFE
 *   - No signal → REQUIRES_NETWORK_VERIFICATION (insufficient evidence)
 *
 * Missing visibility is never converted into a benign classification.
 *
 * Battery/thermal design:
 *   - Calibrated ONNX (LogisticRegression) is tiny and fast — runs on every query.
 *   - LightGBM and HGB (larger) are only run in the uncertainty band (cascade), saving CPU.
 *   - All inference is on-demand (per URL/DNS query), never continuous.
 */
class EnsemblePhishingEngine(private val context: Context) {

    private companion object {
        private const val TAG = "EnsemblePhishingEngine"

        // Classification thresholds (phishing = positive class)
        const val THRESHOLD_PHISHING = 0.65f
        const val THRESHOLD_SUSPICIOUS = 0.30f
        const val SIGNAL_ABSENT = 0.01f

        // Cascade band: if calibrated ONNX score falls in this range, run LightGBM + HGB too.
        // Outside this band, ONNX is confident enough to decide alone (saves battery).
        const val CASCADE_LOW = 0.15f
        const val CASCADE_HIGH = 0.85f

        // Model weights for fusion (sum to 1.0 across active signals only).
        const val WEIGHT_ONNX = 0.40f
        const val WEIGHT_LIGHTGBM = 0.25f
        const val WEIGHT_HGB = 0.25f
        const val WEIGHT_HEURISTIC = 0.08f
        const val WEIGHT_SIGNATURE = 0.02f

        // Signature match contributes near-certain phishing signal.
        const val SIGNATURE_SCORE = 0.95f
    }

    private val featureExtractor = LexicalFeatureExtractor()
    private val dynamicFeatureProvider: DynamicFeatureProvider?
    private val lightGBMModel: LightGBMModel?
    private val hgbModel: HistGradientBoostingModel?
    private val neuroShieldEngine: NeuroShieldEngine?
    private val verdictEngine = VerdictEngine()

    /** Known high-confidence phishing domain signatures (exact host match). */
    private val phishingSignatures: Set<String>

    init {
        // Load LightGBM model from assets
        lightGBMModel = LightGBMModel.loadFromAssets(context, "lightgbm_model.txt")

        // Initialize NeuroShield (ONNX) engine — calibrated LogisticRegression
        neuroShieldEngine = NeuroShieldEngine(context)
        neuroShieldEngine?.initialize()

        // Initialize HistGradientBoosting (ONNX) engine — tertiary ML
        hgbModel = HistGradientBoostingModel(context)
        hgbModel?.initialize()

        // Initialize dynamic feature provider
        dynamicFeatureProvider = DynamicFeatureProvider(context)

        // Load bundled phishing signatures
        phishingSignatures = loadPhishingSignatures()
        Log.i(TAG, "Ensemble ready: ${phishingSignatures.size} signatures, ONNX=${neuroShieldEngine?.isReady == true}, LightGBM=${lightGBMModel != null}, HGB=${hgbModel?.isReady == true}")
    }

    /**
     * Produce a full threat verdict using the ensemble with battery-efficient cascade.
     *
     * @param domain Raw domain or URL string
     * @param heuristicScore Score from the detector's heuristic pipeline (0.0–0.80)
     * @return Complete [ThreatVerdict] with ensemble verdict, confidence, explanation
     */
    fun evaluate(domain: String, heuristicScore: Float = 0f): ThreatVerdict {
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

        // Build 53-feature vector (dynamic features 46-52 populated, matching training).
        val features = featureExtractor.extract(cleanDomain)
        val dynamicFeatures = dynamicFeatureProvider?.compute(cleanDomain) ?: FloatArray(7)
        System.arraycopy(dynamicFeatures, 0, features, 46, dynamicFeatures.size)

        // ── Layer 0: Signature layer (near-zero cost) ──
        val signatureMatch = phishingSignatures.contains(cleanDomain)
        val signatureScore = if (signatureMatch) SIGNATURE_SCORE else 0.0f

        // Immediate block on high-confidence signature.
        if (signatureMatch) {
            return buildVerdict(
                cleanDomain, features, signatureScore, 0.0f, 0.0f, boundedHeuristic(heuristicScore),
                true, listOf("Signature match: known phishing domain")
            )
        }

        // ── Layer 1: Calibrated ONNX model (LogisticRegression) ──
        val onnxScore = runCalibratedONNX(cleanDomain)

        // ── Cascade: skip LightGBM + HGB when ONNX is confident (saves battery/heat) ──
        val runLightGBM = onnxScore > CASCADE_LOW && onnxScore < CASCADE_HIGH
        val lgbScore = if (runLightGBM) runLightGBM(features) else 0.0f
        val hgbScore = if (runLightGBM) runHGB(features) else 0.0f

        // ── Layer 3: detector-supplied heuristic evidence ──
        val boundedHeuristicScore = boundedHeuristic(heuristicScore)

        // ── Score Fusion (only active signals) ──
        val fusedScore = fuseScores(onnxScore, lgbScore, hgbScore, boundedHeuristicScore, signatureScore)

        // ── Evidence presence ──
        val hasONNXSignal = onnxScore > SIGNAL_ABSENT
        val hasLGBSignal = lgbScore > SIGNAL_ABSENT
        val hasHGBSignal = hgbScore > SIGNAL_ABSENT
        val hasHeuristicSignal = boundedHeuristicScore > SIGNAL_ABSENT
        val evidencePresent = hasONNXSignal || hasLGBSignal || hasHGBSignal || hasHeuristicSignal || signatureMatch

        // ── Verdict Classification ──
        val verdict = when {
            !evidencePresent -> VerdictLevel.REQUIRES_NETWORK_VERIFICATION
            fusedScore >= THRESHOLD_PHISHING -> VerdictLevel.PHISHING
            fusedScore >= THRESHOLD_SUSPICIOUS -> VerdictLevel.SUSPICIOUS
            else -> VerdictLevel.SAFE
        }

        // ── Triggered Indicators ──
        val indicators = verdictEngine.identifyIndicators(features, cleanDomain).toMutableList()
        if (onnxScore > THRESHOLD_PHISHING) {
            indicators.add("Calibrated ONNX model flagged phishing (score=${"%.2f".format(onnxScore)})")
        } else if (onnxScore > SIGNAL_ABSENT) {
            indicators.add("ONNX signal: ${"%.2f".format(onnxScore)}")
        }
        if (lgbScore > THRESHOLD_PHISHING) {
            indicators.add("LightGBM ML model flagged phishing (score=${"%.2f".format(lgbScore)})")
        }
        if (hgbScore > THRESHOLD_PHISHING) {
            indicators.add("HistGradientBoosting ML model flagged phishing (score=${"%.2f".format(hgbScore)})")
        } else if (hgbScore > SIGNAL_ABSENT) {
            indicators.add("HGB signal: ${"%.2f".format(hgbScore)}")
        }
        if (boundedHeuristicScore > THRESHOLD_PHISHING) {
            indicators.add("Heuristic analysis flagged phishing (score=${"%.2f".format(boundedHeuristicScore)})")
        }
        if (signatureMatch) {
            indicators.add("Known phishing-domain signature matched")
        }
        if (!runLightGBM && onnxScore >= CASCADE_LOW && onnxScore <= CASCADE_HIGH) {
            // shouldn't happen, but note if lightgbm skipped
        }
        if (!runLightGBM) {
            indicators.add("LightGBM + HGB skipped (ONNX confident — battery saving)")
        }

        // ── Explanation ──
        val explanation = buildExplanation(
            verdict, fusedScore, onnxScore, lgbScore, hgbScore, boundedHeuristicScore,
            signatureMatch, indicators, evidencePresent, runLightGBM
        )

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

    private fun boundedHeuristic(score: Float): Float = score.coerceIn(0f, 0.80f)

    /**
     * Run calibrated ONNX (NeuroShield LogisticRegression) inference.
     * Returns temperature-scaled probability.
     */
    private fun runCalibratedONNX(domain: String): Float {
        return neuroShieldEngine?.infer(domain) ?: 0.0f  // infer() is calibrated
    }

    /**
     * Run LightGBM model inference.
     */
    private fun runLightGBM(features: FloatArray): Float {
        return lightGBMModel?.infer(features) ?: 0.0f
    }

    /**
     * Run HistGradientBoosting ONNX model inference.
     */
    private fun runHGB(features: FloatArray): Float {
        return hgbModel?.inferFromFeatures(features) ?: 0.0f
    }

    /**
     * Fuse scores from active signals using weighted average (weights re-normalized
     * over only the signals that are present).
     */
    private fun fuseScores(
        onnxScore: Float,
        lightGBMScore: Float,
        hgbScore: Float,
        heuristicScore: Float,
        signatureScore: Float
    ): Float {
        var totalWeight = 0f
        var weightedSum = 0f

        if (onnxScore > SIGNAL_ABSENT) {
            weightedSum += onnxScore * WEIGHT_ONNX
            totalWeight += WEIGHT_ONNX
        }
        if (lightGBMScore > SIGNAL_ABSENT) {
            weightedSum += lightGBMScore * WEIGHT_LIGHTGBM
            totalWeight += WEIGHT_LIGHTGBM
        }
        if (hgbScore > SIGNAL_ABSENT) {
            weightedSum += hgbScore * WEIGHT_HGB
            totalWeight += WEIGHT_HGB
        }
        if (heuristicScore > SIGNAL_ABSENT) {
            weightedSum += heuristicScore * WEIGHT_HEURISTIC
            totalWeight += WEIGHT_HEURISTIC
        }
        if (signatureScore > SIGNAL_ABSENT) {
            weightedSum += signatureScore * WEIGHT_SIGNATURE
            totalWeight += WEIGHT_SIGNATURE
        }

        return if (totalWeight > 0f) weightedSum / totalWeight else 0.0f
    }

    private fun buildVerdict(
        domain: String,
        features: FloatArray,
        signatureScore: Float,
        onnxScore: Float,
        hgbScore: Float,
        heuristicScore: Float,
        signatureMatch: Boolean,
        extraIndicators: List<String>
    ): ThreatVerdict {
        val fusedScore = fuseScores(onnxScore, 0.0f, hgbScore, heuristicScore, signatureScore)
        val indicators = verdictEngine.identifyIndicators(features, domain).toMutableList()
        indicators.addAll(extraIndicators)
        val explanation = "High-confidence phishing signature match. Score: ${"%.0f".format(fusedScore * 100)}%."
        return ThreatVerdict(
            verdict = VerdictLevel.PHISHING,
            confidence = fusedScore,
            explanation = explanation,
            triggeredIndicators = indicators,
            recommendedAction = "Domain blocked. Known phishing domain."
        )
    }

    private fun buildExplanation(
        verdict: VerdictLevel,
        fusedScore: Float,
        onnxScore: Float,
        lightGBMScore: Float,
        hgbScore: Float,
        heuristicScore: Float,
        signatureMatch: Boolean,
        indicators: List<String>,
        evidencePresent: Boolean,
        ranLightGBM: Boolean
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
        if (onnxScore > SIGNAL_ABSENT) {
            modelDetails.add("ONNX(LR): ${"%.0f".format(onnxScore * 100)}%")
        }
        if (ranLightGBM && lightGBMScore > SIGNAL_ABSENT) {
            modelDetails.add("LightGBM: ${"%.0f".format(lightGBMScore * 100)}%")
        }
        if (ranLightGBM && hgbScore > SIGNAL_ABSENT) {
            modelDetails.add("HGB: ${"%.0f".format(hgbScore * 100)}%")
        } else if (!ranLightGBM) {
            modelDetails.add("LightGBM + HGB: skipped (battery saving)")
        }
        if (heuristicScore > SIGNAL_ABSENT) {
            modelDetails.add("Heuristic: ${"%.0f".format(heuristicScore * 100)}%")
        }
        if (signatureMatch) {
            modelDetails.add("Signature: matched")
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

    private fun sanitizeInput(input: String): String = input.trim().lowercase()

    /**
     * Bundled high-confidence phishing domain signatures.
     * In production these are updated from a signed threat feed; here we ship
     * a small seed set so the signature layer works offline from day one.
     */
    private fun loadPhishingSignatures(): Set<String> {
        return setOf(
            // Commonly abused / clearly malicious patterns (seed set).
            // These are exact host matches; substrings are handled by heuristics + ONNX.
            "paypal-security.suspicious.tk",
            "apple-id-verify.ml",
            "microsoft-account.gq",
            "netflix-login.cf",
            "amazon-verify.ga",
            "bankofamerica-secure.tk",
            "chase-online.ml",
            "wellsfargo-verify.gq",
            "update-apple-id.xyz",
            "signin-amazon.freehosting.com",
            "secure-paypal-login.cf",
            "verify-bankofamerica.tk",
        )
    }

    /**
     * Get the operational status of all engines.
     */
    fun getEngineStatus(): EngineStatus {
        return EngineStatus(
            onnxReady = neuroShieldEngine?.isReady ?: false,
            heuristicsReady = true,
            vpnActive = false,
            modelSizeBytes = (neuroShieldEngine?.modelSizeBytes ?: 0L) + (hgbModel?.modelSizeBytes ?: 0L),
            featureCount = LexicalFeatureExtractor.FEATURE_COUNT,
            mode = if ((lightGBMModel != null || hgbModel != null) && neuroShieldEngine?.isReady == true) EngineMode.FULL else EngineMode.DEGRADED
        )
    }

    /**
     * Release all engine resources.
     */
    fun close() {
        neuroShieldEngine?.close()
        hgbModel?.close()
    }
}
