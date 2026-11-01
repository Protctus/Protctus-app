package com.protctus.app.engine.reasoning.models

enum class SemanticRelation {
    SUPPORTS,
    CONTRADICTS,
    STRENGTHENS,
    WEAKENS,
    REQUIRES,
    IMPLIES,
    UNKNOWN,
    INDEPENDENT,
    DEPENDENT
}

data class EvidenceEdge(
    val sourceNode: EvidenceNode,
    val targetHypothesis: Hypothesis,
    val relation: SemanticRelation
)
