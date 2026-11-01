package com.protctus.app.engine.reasoning.pipeline.stage1_2

import com.protctus.app.engine.AppEvidence
import com.protctus.app.engine.EngineDecision
import com.protctus.app.engine.EngineVerdict
import com.protctus.app.engine.FileEvidence
import com.protctus.app.engine.SystemEvidence

object SafeScreener {
    
    fun screenFile(evidence: FileEvidence): EngineVerdict? {
        // Only trigger fast-path for files that are overwhelmingly benign and have NO anomalies.
        if (evidence.yaraMatches.isEmpty() && evidence.clamAvMatches.isEmpty() && 
            evidence.entropy ?: 0.0 < 6.5 && !evidence.isApkInWrongPlace && 
            !evidence.hasDoubleExtension && !evidence.hasRTLO && 
            !evidence.hasEOCDAtEnd && !evidence.hasEmbeddedExecutable && 
            (evidence.archiveRatio ?: 0.0) < 50.0 && !evidence.archiveTraversal) {
            
            return EngineVerdict(
                EngineDecision.CLEAN, 0.0f, 1.0f,
                "Fast Path: File exhibits zero technical anomalies."
            )
        }
        
        // Multi-engine match immediately warrants quarantine state to save battery, as it is non-negotiable.
        if (evidence.yaraMatches.isNotEmpty() && evidence.clamAvMatches.isNotEmpty()) {
            return EngineVerdict(
                EngineDecision.MALWARE, 10.0f, 0.99f, 
                "Fast Path: Multi-engine signature match confirmed."
            )
        }

        return null
    }

    fun screenApp(evidence: AppEvidence): EngineVerdict? {
        if (evidence.isSystemApp && evidence.correlationVerdict.signals.isEmpty()) {
            return EngineVerdict(
                EngineDecision.CLEAN, 0.0f, 1.0f,
                "Fast Path: Verified system application with no anomalies."
            )
        }
        return null
    }

    fun screenSystem(evidence: SystemEvidence): EngineVerdict? {
        if (!evidence.isRooted && !evidence.adbEnabled && !evidence.unknownSourcesEnabled) {
            return EngineVerdict(
                EngineDecision.CLEAN, 0.0f, 1.0f,
                "Fast Path: Device integrity is intact."
            )
        }
        return null
    }
}
