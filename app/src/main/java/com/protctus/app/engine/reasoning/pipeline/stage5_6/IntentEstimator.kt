package com.protctus.app.engine.reasoning.pipeline.stage5_6

import com.protctus.app.engine.reasoning.models.EvidenceNode
import com.protctus.app.engine.reasoning.models.Intent
import com.protctus.app.engine.reasoning.models.ReasoningState
import com.protctus.app.engine.reasoning.models.RiskScenario

object IntentEstimator {

    fun estimate(
        risks: Map<RiskScenario, ReasoningState>, 
        contextProperties: List<EvidenceNode>
    ): Map<Intent, ReasoningState> {
        val states = mutableMapOf<Intent, ReasoningState>()
        for (intent in Intent.values()) {
            states[intent] = ReasoningState()
        }

        val hasSystemContext = contextProperties.any { it.property == "SYSTEM_APP" }
        val hasSideloadContext = contextProperties.any { it.property == "SIDELOADED_APP" }

        // Malware Intent mapping
        val maliciousRisks = listOf(
            RiskScenario.UNAUTHORIZED_EXECUTION,
            RiskScenario.DEFENSE_EVASION,
            RiskScenario.PRIVILEGE_ABUSE,
            RiskScenario.DATA_THEFT,
            RiskScenario.UI_SPOOFING
        )

        var maxMaliciousRiskProb = 0.0f
        var maxMaliciousRiskConf = 0.0f

        for (risk in maliciousRisks) {
            val state = risks[risk]
            if (state != null) {
                if (state.probability > maxMaliciousRiskProb) maxMaliciousRiskProb = state.probability
                if (state.confidence > maxMaliciousRiskConf) maxMaliciousRiskConf = state.confidence
            }
        }

        if (maxMaliciousRiskProb > 0f) {
            if (hasSystemContext) {
                val intent = states[Intent.DEVELOPER_TOOL]!!
                intent.probability = maxMaliciousRiskProb * 0.8f
                intent.confidence = maxMaliciousRiskConf * 0.9f
            } else if (hasSideloadContext) {
                val intent = states[Intent.UNKNOWN_MALICIOUS_INTENT]!!
                intent.probability = maxMaliciousRiskProb
                intent.confidence = maxMaliciousRiskConf
            } else {
                val intent = states[Intent.UNKNOWN_UTILITY]!!
                intent.probability = maxMaliciousRiskProb * 0.6f
                intent.confidence = maxMaliciousRiskConf * 0.6f
            }
            
            // Explicitly boost malicious intent if defense evasion is present regardless of context
            if (risks.containsKey(RiskScenario.DEFENSE_EVASION)) {
                val evasionState = risks[RiskScenario.DEFENSE_EVASION]!!
                val intent = states[Intent.UNKNOWN_MALICIOUS_INTENT]!!
                intent.probability = maxOf(intent.probability, evasionState.probability)
                intent.confidence = maxOf(intent.confidence, evasionState.confidence)
            }
        }

        return states.filter { it.value.probability > 0.0f }
    }
}
