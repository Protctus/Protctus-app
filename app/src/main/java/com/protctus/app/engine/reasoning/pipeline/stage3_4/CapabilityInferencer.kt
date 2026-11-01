package com.protctus.app.engine.reasoning.pipeline.stage3_4

import com.protctus.app.engine.reasoning.models.Capability
import com.protctus.app.engine.reasoning.models.EvidenceNode
import com.protctus.app.engine.reasoning.models.ReasoningState
import kotlin.math.max

object CapabilityInferencer {

    fun inferCapabilities(properties: List<EvidenceNode>): Map<Capability, ReasoningState> {
        val states = mutableMapOf<Capability, ReasoningState>()
        for (cap in Capability.values()) {
            states[cap] = ReasoningState()
        }

        val propSet = properties.map { it.property }.toSet()

        // 1. Dynamic Code Execution
        if (propSet.contains("HIGH_ENTROPY") || propSet.contains("EMBEDDED_EXECUTABLE")) {
            updateState(states[Capability.DYNAMIC_CODE_EXECUTION]!!, 0.7f, 0.6f, properties, listOf("HIGH_ENTROPY", "EMBEDDED_EXECUTABLE"))
        }
        if (propSet.contains("YARA_SIGNATURE_MATCH") || propSet.contains("CLAMAV_SIGNATURE_MATCH") || propSet.contains("SIGNATURE_MATCH")) {
            updateState(states[Capability.DYNAMIC_CODE_EXECUTION]!!, 0.9f, 0.9f, properties, listOf("YARA_SIGNATURE_MATCH", "CLAMAV_SIGNATURE_MATCH", "SIGNATURE_MATCH"))
            // Signature matches imply stealth and persistence capabilities as well
            updateState(states[Capability.STEALTH]!!, 0.8f, 0.8f, properties, listOf("YARA_SIGNATURE_MATCH", "CLAMAV_SIGNATURE_MATCH", "SIGNATURE_MATCH"))
        }

        // 2. Stealth
        if (propSet.contains("RIGHT_TO_LEFT_OVERRIDE") || propSet.contains("DOUBLE_EXTENSION") || propSet.contains("DYNAMIC_STEALTH") || propSet.contains("POLYGLOT_STRUCTURE")) {
            updateState(states[Capability.STEALTH]!!, 0.85f, 0.8f, properties, listOf("RIGHT_TO_LEFT_OVERRIDE", "DOUBLE_EXTENSION", "DYNAMIC_STEALTH", "POLYGLOT_STRUCTURE"))
        }

        // 3. Persistence
        if (propSet.contains("EXECUTABLE_IN_DATA_DIR") || propSet.contains("STRUCTURAL_ANOMALY") || propSet.contains("ARCHIVE_TRAVERSAL_PATH")) {
            updateState(states[Capability.PERSISTENCE]!!, 0.75f, 0.7f, properties, listOf("EXECUTABLE_IN_DATA_DIR", "STRUCTURAL_ANOMALY", "ARCHIVE_TRAVERSAL_PATH"))
        }

        // 4. UI Manipulation
        if (propSet.contains("UI_ABUSE")) {
            updateState(states[Capability.USER_INTERFACE_MANIPULATION]!!, 0.8f, 0.75f, properties, listOf("UI_ABUSE"))
        }

        // 5. Privilege Usage
        if (propSet.contains("PRIVILEGE_ESCALATION") || propSet.contains("DEVICE_ROOTED") || propSet.contains("ADB_ENABLED") || propSet.contains("UNKNOWN_SOURCES_ENABLED")) {
            updateState(states[Capability.PRIVILEGE_USAGE]!!, 0.8f, 0.8f, properties, listOf("PRIVILEGE_ESCALATION", "DEVICE_ROOTED", "ADB_ENABLED", "UNKNOWN_SOURCES_ENABLED"))
        }

        // 6. Data Collection / Exfiltration
        if (propSet.contains("DATA_EXFILTRATION")) {
            updateState(states[Capability.DATA_COLLECTION]!!, 0.8f, 0.75f, properties, listOf("DATA_EXFILTRATION"))
            updateState(states[Capability.NETWORK_COMMUNICATION]!!, 0.7f, 0.7f, properties, listOf("DATA_EXFILTRATION"))
        }
        if (propSet.contains("HIGH_COMPRESSION_RATIO")) {
            updateState(states[Capability.DATA_COLLECTION]!!, 0.6f, 0.5f, properties, listOf("HIGH_COMPRESSION_RATIO"))
        }

        return states.filter { it.value.probability > 0.0f }
    }

    private fun updateState(
        state: ReasoningState, 
        prob: Float, 
        conf: Float, 
        allProps: List<EvidenceNode>, 
        matchKeys: List<String>
    ) {
        state.probability = max(state.probability, prob)
        state.confidence = max(state.confidence, conf)
        
        val matchedNodes = allProps.filter { it.property in matchKeys }
        for (node in matchedNodes) {
            if (!state.supportingEvidence.contains(node)) {
                state.supportingEvidence.add(node)
            }
        }
    }
}
