package com.protctus.app.engine.reasoning.pipeline.stage7_8_9

import com.protctus.app.engine.EngineDecision
import com.protctus.app.engine.reasoning.models.Hypothesis
import com.protctus.app.engine.reasoning.models.ReasoningState

object DecisionPolicy {

    data class PolicyResult(
        val decision: EngineDecision,
        val recommendedAction: String,
        val maxProbability: Float,
        val maxConfidence: Float
    )

    fun applyPolicy(hypotheses: Map<Hypothesis, ReasoningState>, properties: List<com.protctus.app.engine.reasoning.models.EvidenceNode>): PolicyResult {
        val malicious = hypotheses[Hypothesis.MALICIOUS_ACTIVITY] ?: ReasoningState()
        val pua = hypotheses[Hypothesis.POTENTIALLY_UNWANTED_ACTIVITY] ?: ReasoningState()
        val benign = hypotheses[Hypothesis.BENIGN_ACTIVITY] ?: ReasoningState()

        var malProb = malicious.probability
        var malConf = malicious.confidence
        var maxFloor: Float? = null
        var minCap: Float? = null

        // Extract deterministic bounds from immutable evidence
        properties.forEach { node ->
            node.probabilityFloor?.let { floor ->
                maxFloor = if (maxFloor == null) floor else maxOf(maxFloor!!, floor)
            }
            node.probabilityCap?.let { cap ->
                minCap = if (minCap == null) cap else minOf(minCap!!, cap)
            }
        }

        // Apply strict architectural bounds
        maxFloor?.let { floor ->
            if (malProb < floor) {
                malProb = floor
                malConf = maxOf(malConf, 0.95f) // Definitive evidence maximizes confidence
            }
        }

        minCap?.let { rawCap ->
            val cap = maxFloor?.let { floor -> maxOf(rawCap, floor) } ?: rawCap
            if (malProb > cap) {
                malProb = cap
            }
        }

        return when {
            malProb >= 0.8f && malConf >= 0.7f -> {
                PolicyResult(EngineDecision.MALWARE, "Quarantine and Block", malProb, malConf)
            }
            malProb >= 0.5f -> {
                PolicyResult(EngineDecision.HIGH_RISK, "Recommend Deep Scan", malProb, malConf)
            }
            pua.probability >= 0.7f -> {
                PolicyResult(EngineDecision.SUSPICIOUS, "Monitor and Warn User", pua.probability, pua.confidence)
            }
            pua.probability >= 0.4f -> {
                PolicyResult(EngineDecision.LOW_RISK, "Monitor", pua.probability, pua.confidence)
            }
            else -> {
                PolicyResult(EngineDecision.CLEAN, "No Action", benign.probability, benign.confidence)
            }
        }
    }
}
