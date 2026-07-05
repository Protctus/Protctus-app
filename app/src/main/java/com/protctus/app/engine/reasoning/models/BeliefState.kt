package com.protctus.app.engine.reasoning.models

data class ReasoningState(
    var probability: Float = 0.0f,
    var confidence: Float = 0.0f,
    var uncertainty: Float = 0.0f,
    val supportingEvidence: MutableList<EvidenceNode> = mutableListOf(),
    val contradictingEvidence: MutableList<EvidenceNode> = mutableListOf()
) {
    fun copy(): ReasoningState = ReasoningState(
        probability, confidence, uncertainty,
        supportingEvidence.toMutableList(),
        contradictingEvidence.toMutableList()
    )
}

data class BeliefState(
    val hypothesisStates: Map<Hypothesis, ReasoningState>,
    val intentStates: Map<Intent, ReasoningState>,
    val scenarioStates: Map<RiskScenario, ReasoningState>,
    val capabilityStates: Map<Capability, ReasoningState>
)
