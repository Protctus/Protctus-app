package com.protctus.app.engine.reasoning.pipeline.stage5_6

import com.protctus.app.engine.reasoning.models.Hypothesis
import com.protctus.app.engine.reasoning.models.Intent
import com.protctus.app.engine.reasoning.models.ReasoningState

object BeliefPropagator {

    fun propagateToHypotheses(intents: Map<Intent, ReasoningState>): Map<Hypothesis, ReasoningState> {
        val states = mutableMapOf<Hypothesis, ReasoningState>()
        for (hyp in Hypothesis.values()) {
            states[hyp] = ReasoningState()
        }

        var maliciousSupport = 0.0f
        var benignSupport = 0.0f

        for ((intent, state) in intents) {
            when (intent) {
                Intent.MALICIOUS_CREDENTIAL_THEFT,
                Intent.MALICIOUS_EXFILTRATION,
                Intent.MALICIOUS_PERSISTENCE,
                Intent.UNKNOWN_MALICIOUS_INTENT -> {
                    maliciousSupport += state.probability * state.confidence
                    val target = states[Hypothesis.MALICIOUS_ACTIVITY]!!
                    target.probability = (target.probability + state.probability).coerceAtMost(1.0f)
                    target.confidence = maxOf(target.confidence, state.confidence)
                }
                Intent.LEGITIMATE_UTILITY,
                Intent.DEVELOPER_TOOL,
                Intent.ENTERPRISE_MANAGEMENT -> {
                    benignSupport += state.probability * state.confidence
                    val target = states[Hypothesis.BENIGN_ACTIVITY]!!
                    target.probability = (target.probability + state.probability).coerceAtMost(1.0f)
                    target.confidence = maxOf(target.confidence, state.confidence)
                }
                Intent.ACCESSIBILITY_SOFTWARE,
                Intent.AUTOMATION_TOOL,
                Intent.UNKNOWN_UTILITY -> {
                    val target = states[Hypothesis.POTENTIALLY_UNWANTED_ACTIVITY]!!
                    target.probability = (target.probability + state.probability).coerceAtMost(1.0f)
                    target.confidence = maxOf(target.confidence, state.confidence)
                }
            }
        }

        if (maliciousSupport > 0 && benignSupport > 0) {
            val maliciousState = states[Hypothesis.MALICIOUS_ACTIVITY]!!
            val benignState = states[Hypothesis.BENIGN_ACTIVITY]!!
            
            val conflict = minOf(maliciousSupport, benignSupport) / maxOf(maliciousSupport, benignSupport)
            
            maliciousState.uncertainty = conflict
            benignState.uncertainty = conflict
            
            if (maliciousSupport > benignSupport) {
                benignState.probability *= (1.0f - conflict)
            } else {
                maliciousState.probability *= (1.0f - conflict)
            }
        }

        return states.filter { it.value.probability > 0.0f }
    }
}
