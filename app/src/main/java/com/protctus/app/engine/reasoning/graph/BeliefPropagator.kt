package com.protctus.app.engine.reasoning.graph

import com.protctus.app.engine.reasoning.models.ReasoningState
import com.protctus.app.engine.reasoning.models.Hypothesis
import com.protctus.app.engine.reasoning.models.SemanticRelation

object BeliefPropagator {

    /**
     * Propagates belief from evidence nodes to hypotheses.
     * Updates Probability, Confidence, and Uncertainty independently.
     */
    fun propagate(graph: ReasoningGraph, hypotheses: Map<Hypothesis, ReasoningState>, computationDepth: Int = 1) {
        // If computation depth is 0, we don't perform heavy reasoning, just skip
        if (computationDepth <= 0) return

        for ((hypothesis, state) in hypotheses) {
            val edges = graph.getEdgesForHypothesis(hypothesis)
            
            var supportWeight = 0.0f
            var contradictWeight = 0.0f
            var maxConfidence = 0.0f
            
            for (edge in edges) {
                val evidence = edge.sourceNode
                // Reliability determines how much this evidence can sway probability/confidence
                val effectivePower = evidence.reliability.weight * evidence.confidence
                
                when (edge.relation) {
                    SemanticRelation.SUPPORTS, SemanticRelation.STRENGTHENS -> {
                        supportWeight += effectivePower
                        maxConfidence = maxOf(maxConfidence, evidence.reliability.weight)
                        state.supportingEvidence.add(evidence)
                    }
                    SemanticRelation.CONTRADICTS, SemanticRelation.WEAKENS -> {
                        contradictWeight += effectivePower
                        state.contradictingEvidence.add(evidence)
                    }
                    SemanticRelation.REQUIRES -> {
                        supportWeight += effectivePower * 1.5f
                        maxConfidence = maxOf(maxConfidence, evidence.reliability.weight)
                        state.supportingEvidence.add(evidence)
                    }
                    SemanticRelation.UNKNOWN -> {
                        // Unknown relations increase uncertainty without affecting probability
                        state.uncertainty = (state.uncertainty + 0.1f).coerceAtMost(1.0f)
                    }
                    else -> {}
                }
            }

            // Probability is driven by the net support vs contradiction
            val netSupport = (supportWeight - contradictWeight).coerceAtLeast(0.0f)
            
            // Accumulation math
            state.probability = (1.0f - kotlin.math.exp(-netSupport.toDouble() * 0.5)).toFloat().coerceIn(0.0f, 1.0f)
            
            // Confidence is bounded by the highest reliability of supporting evidence
            // Multiple low-reliability supports won't push confidence to 1.0
            val confidenceFactor = (1.0f - kotlin.math.exp(-supportWeight.toDouble() * 0.3)).toFloat()
            state.confidence = (confidenceFactor * maxConfidence).coerceIn(0.0f, 1.0f)

            // Uncertainty spikes when there are both strong supports and contradictions
            if (supportWeight > 0.1f && contradictWeight > 0.1f) {
                val conflictRatio = minOf(supportWeight, contradictWeight) / maxOf(supportWeight, contradictWeight)
                state.uncertainty = (state.uncertainty + conflictRatio).coerceAtMost(1.0f)
            }
        }
    }
}
