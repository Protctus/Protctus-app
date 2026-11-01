package com.protctus.app.engine.reasoning.graph

import com.protctus.app.engine.reasoning.models.EvidenceEdge
import com.protctus.app.engine.reasoning.models.EvidenceNode
import com.protctus.app.engine.reasoning.models.Hypothesis
import com.protctus.app.engine.reasoning.models.SemanticRelation

class ReasoningGraph {
    private val nodes = mutableListOf<EvidenceNode>()
    private val edges = mutableListOf<EvidenceEdge>()

    fun addNode(node: EvidenceNode) {
        if (!nodes.contains(node)) {
            nodes.add(node)
        }
    }

    fun addEdge(edge: EvidenceEdge) {
        if (!edges.contains(edge)) {
            edges.add(edge)
            addNode(edge.sourceNode)
        }
    }

    fun getEdgesForHypothesis(hypothesis: Hypothesis): List<EvidenceEdge> {
        return edges.filter { it.targetHypothesis == hypothesis }
    }

    fun getAllNodes(): List<EvidenceNode> = nodes.toList()
    fun getAllEdges(): List<EvidenceEdge> = edges.toList()

    fun destroy() {
        nodes.clear()
        edges.clear()
    }
}
