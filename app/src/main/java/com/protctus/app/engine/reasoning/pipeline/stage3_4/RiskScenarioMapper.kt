package com.protctus.app.engine.reasoning.pipeline.stage3_4

import com.protctus.app.engine.reasoning.models.Capability
import com.protctus.app.engine.reasoning.models.ReasoningState
import com.protctus.app.engine.reasoning.models.RiskScenario
import kotlin.math.max

object RiskScenarioMapper {

    fun mapRisks(capabilities: Map<Capability, ReasoningState>): Map<RiskScenario, ReasoningState> {
        val states = mutableMapOf<RiskScenario, ReasoningState>()
        for (risk in RiskScenario.values()) {
            states[risk] = ReasoningState()
        }

        if (capabilities.containsKey(Capability.STEALTH)) {
            updateState(states[RiskScenario.DEFENSE_EVASION]!!, capabilities[Capability.STEALTH]!!)
        }

        if (capabilities.containsKey(Capability.DYNAMIC_CODE_EXECUTION)) {
            updateState(states[RiskScenario.UNAUTHORIZED_EXECUTION]!!, capabilities[Capability.DYNAMIC_CODE_EXECUTION]!!)
        }

        if (capabilities.containsKey(Capability.PERSISTENCE)) {
            val cap = capabilities[Capability.PERSISTENCE]!!
            val adjustedState = cap.copy().apply { probability *= 0.9f }
            updateState(states[RiskScenario.PRIVILEGE_ABUSE]!!, adjustedState)
        }

        if (capabilities.containsKey(Capability.PRIVILEGE_USAGE)) {
            updateState(states[RiskScenario.PRIVILEGE_ABUSE]!!, capabilities[Capability.PRIVILEGE_USAGE]!!)
        }

        if (capabilities.containsKey(Capability.DATA_COLLECTION) || capabilities.containsKey(Capability.NETWORK_COMMUNICATION)) {
            val cap = capabilities[Capability.DATA_COLLECTION] ?: capabilities[Capability.NETWORK_COMMUNICATION]!!
            updateState(states[RiskScenario.SENSITIVE_DATA_ACCESS]!!, cap)
        }

        if (capabilities.containsKey(Capability.USER_INTERFACE_MANIPULATION)) {
            val cap = capabilities[Capability.USER_INTERFACE_MANIPULATION]!!
            updateState(states[RiskScenario.UNKNOWN_TECHNICAL_RISK]!!, cap)
        }

        return states.filter { it.value.probability > 0.0f }
    }
    
    private fun updateState(target: ReasoningState, source: ReasoningState) {
        target.probability = max(target.probability, source.probability)
        target.confidence = max(target.confidence, source.confidence)
        for (node in source.supportingEvidence) {
            if (!target.supportingEvidence.contains(node)) {
                target.supportingEvidence.add(node)
            }
        }
    }
}
