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

    fun applyPolicy(hypotheses: Map<Hypothesis, ReasoningState>): PolicyResult {
        val malicious = hypotheses[Hypothesis.MALICIOUS_ACTIVITY] ?: ReasoningState()
        val pua = hypotheses[Hypothesis.POTENTIALLY_UNWANTED_ACTIVITY] ?: ReasoningState()
        val benign = hypotheses[Hypothesis.BENIGN_ACTIVITY] ?: ReasoningState()

        return when {
            malicious.probability >= 0.8f && malicious.confidence >= 0.7f -> {
                PolicyResult(EngineDecision.MALWARE, "Quarantine and Block", malicious.probability, malicious.confidence)
            }
            malicious.probability >= 0.5f -> {
                PolicyResult(EngineDecision.HIGH_RISK, "Recommend Deep Scan", malicious.probability, malicious.confidence)
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
