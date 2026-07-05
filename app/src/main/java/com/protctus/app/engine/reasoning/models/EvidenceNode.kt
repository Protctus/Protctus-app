package com.protctus.app.engine.reasoning.models

enum class TrustLevel(val weight: Float) {
    VERY_LOW(0.1f),
    LOW(0.3f),
    MEDIUM(0.5f),
    HIGH(0.8f),
    VERY_HIGH(1.0f)
}

enum class PropertyType {
    STRUCTURAL, HEURISTIC, SIGNATURE, BEHAVIORAL, CONTEXTUAL
}

data class EvidenceNode(
    val property: String,
    val type: PropertyType,
    val reliability: TrustLevel,
    val confidence: Float,
    val sourceQuality: String
)
