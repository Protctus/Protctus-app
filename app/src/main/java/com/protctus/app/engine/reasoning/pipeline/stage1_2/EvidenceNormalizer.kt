package com.protctus.app.engine.reasoning.pipeline.stage1_2

import com.protctus.app.engine.AppEvidence
import com.protctus.app.engine.FileEvidence
import com.protctus.app.engine.SystemEvidence
import com.protctus.app.engine.correlation.EvidenceCluster
import com.protctus.app.engine.reasoning.models.EvidenceNode
import com.protctus.app.engine.reasoning.models.PropertyType
import com.protctus.app.engine.reasoning.models.TrustLevel

object EvidenceNormalizer {

    fun normalizeFileEvidence(evidence: FileEvidence): List<EvidenceNode> {
        val nodes = mutableListOf<EvidenceNode>()

        if (evidence.yaraMatches.isNotEmpty()) {
            nodes.add(EvidenceNode("YARA_SIGNATURE_MATCH", PropertyType.SIGNATURE, TrustLevel.HIGH, 0.9f, "YARA"))
        }

        if (evidence.clamAvMatches.isNotEmpty()) {
            nodes.add(EvidenceNode("CLAMAV_SIGNATURE_MATCH", PropertyType.SIGNATURE, TrustLevel.HIGH, 0.95f, "ClamAV"))
        }

        if (evidence.entropy != null && evidence.entropy >= 7.99) {
            nodes.add(EvidenceNode("HIGH_ENTROPY", PropertyType.HEURISTIC, TrustLevel.LOW, 0.8f, "FileExtr"))
        }

        if (evidence.hasRTLO) {
            nodes.add(EvidenceNode("RIGHT_TO_LEFT_OVERRIDE", PropertyType.STRUCTURAL, TrustLevel.MEDIUM, 0.9f, "FileExtr"))
        }

        if (evidence.hasEmbeddedExecutable) {
            nodes.add(EvidenceNode("EMBEDDED_EXECUTABLE", PropertyType.STRUCTURAL, TrustLevel.MEDIUM, 0.85f, "FileExtr"))
        }

        if (evidence.hasDoubleExtension) {
            nodes.add(EvidenceNode("DOUBLE_EXTENSION", PropertyType.STRUCTURAL, TrustLevel.MEDIUM, 0.8f, "FileExtr"))
        }

        if (evidence.isApkInWrongPlace) {
            nodes.add(EvidenceNode("EXECUTABLE_IN_DATA_DIR", PropertyType.CONTEXTUAL, TrustLevel.LOW, 0.7f, "FileExtr"))
        }

        if (evidence.hasEOCDAtEnd) {
            nodes.add(EvidenceNode("POLYGLOT_STRUCTURE", PropertyType.STRUCTURAL, TrustLevel.MEDIUM, 0.85f, "FileExtr"))
        }

        if (evidence.archiveTraversal) {
            nodes.add(EvidenceNode("ARCHIVE_TRAVERSAL_PATH", PropertyType.STRUCTURAL, TrustLevel.HIGH, 0.9f, "FileExtr"))
        }

        if (evidence.archiveRatio != null && evidence.archiveRatio > 100.0) {
            nodes.add(EvidenceNode("HIGH_COMPRESSION_RATIO", PropertyType.STRUCTURAL, TrustLevel.HIGH, 0.9f, "FileExtr"))
        }

        return nodes
    }

    fun normalizeAppEvidence(evidence: AppEvidence): List<EvidenceNode> {
        val nodes = mutableListOf<EvidenceNode>()

        if (evidence.isSystemApp) {
            nodes.add(EvidenceNode("SYSTEM_APP", PropertyType.CONTEXTUAL, TrustLevel.HIGH, 1.0f, "PackageManager"))
        }
        if (evidence.isSideloaded) {
            nodes.add(EvidenceNode("SIDELOADED_APP", PropertyType.CONTEXTUAL, TrustLevel.LOW, 1.0f, "PackageManager"))
        }

        evidence.correlationVerdict.signals.forEach { signal ->
            when (signal.cluster) {
                EvidenceCluster.UI_ABUSE -> nodes.add(EvidenceNode("UI_ABUSE", PropertyType.BEHAVIORAL, TrustLevel.LOW, signal.confidence, signal.engine))
                EvidenceCluster.DYNAMIC_STEALTH -> nodes.add(EvidenceNode("DYNAMIC_STEALTH", PropertyType.BEHAVIORAL, TrustLevel.LOW, signal.confidence, signal.engine))
                EvidenceCluster.THREAT_SIGNATURE -> nodes.add(EvidenceNode("SIGNATURE_MATCH", PropertyType.SIGNATURE, TrustLevel.HIGH, signal.confidence, signal.engine))
                EvidenceCluster.STRUCTURAL_ANOMALY -> nodes.add(EvidenceNode("STRUCTURAL_ANOMALY", PropertyType.STRUCTURAL, TrustLevel.MEDIUM, signal.confidence, signal.engine))
                EvidenceCluster.PRIVILEGE_ESCALATION -> nodes.add(EvidenceNode("PRIVILEGE_ESCALATION", PropertyType.BEHAVIORAL, TrustLevel.LOW, signal.confidence, signal.engine))
                EvidenceCluster.DATA_EXFILTRATION -> nodes.add(EvidenceNode("DATA_EXFILTRATION", PropertyType.BEHAVIORAL, TrustLevel.LOW, signal.confidence, signal.engine))
                else -> {}
            }
        }

        return nodes
    }

    fun normalizeSystemEvidence(evidence: SystemEvidence): List<EvidenceNode> {
        val nodes = mutableListOf<EvidenceNode>()
        if (evidence.unknownSourcesEnabled) {
            nodes.add(EvidenceNode("UNKNOWN_SOURCES_ENABLED", PropertyType.CONTEXTUAL, TrustLevel.LOW, 1.0f, "SystemSettings"))
        }
        if (evidence.adbEnabled) {
            nodes.add(EvidenceNode("ADB_ENABLED", PropertyType.CONTEXTUAL, TrustLevel.MEDIUM, 1.0f, "SystemSettings"))
        }
        if (evidence.isRooted) {
            nodes.add(EvidenceNode("DEVICE_ROOTED", PropertyType.CONTEXTUAL, TrustLevel.LOW, 1.0f, "SystemIntegrity"))
        }
        return nodes
    }
}
