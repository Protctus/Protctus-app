package com.protctus.app.engine.reasoning.pipeline.stage7_8_9

import com.protctus.app.engine.EngineVerdict
import com.protctus.app.engine.reasoning.models.BeliefState
import com.protctus.app.engine.reasoning.pipeline.stage7_8_9.DecisionPolicy.PolicyResult

object VerdictExplainer {

    fun generateExplainableVerdict(policyResult: PolicyResult, beliefState: BeliefState): EngineVerdict {
        val explanation = buildString {
            appendLine("Reasoning Trace:")
            
            appendLine("  -> Inferred Capabilities: ${beliefState.capabilityStates.keys.joinToString { it.displayName }}")
            appendLine("  -> Technical Risks: ${beliefState.scenarioStates.keys.joinToString { it.displayName }}")
            appendLine("  -> Probable Intent: ${beliefState.intentStates.entries.sortedByDescending { it.value.probability }.take(2).joinToString { it.key.displayName }}")
            
            val dominantHypothesis = beliefState.hypothesisStates.maxByOrNull { it.value.probability }
            if (dominantHypothesis != null) {
                appendLine("  -> Dominant Hypothesis: ${dominantHypothesis.key.displayName} (Prob: ${String.format("%.2f", dominantHypothesis.value.probability)}, Conf: ${String.format("%.2f", dominantHypothesis.value.confidence)})")
            }
            
            appendLine("  -> Recommended Action: ${policyResult.recommendedAction}")
        }

        return EngineVerdict(
            decision = policyResult.decision,
            finalScore = policyResult.maxProbability * 10.0f,
            confidence = policyResult.maxConfidence,
            explanation = explanation.trimEnd()
        )
    }
}
