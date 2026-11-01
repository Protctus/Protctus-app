package com.protctus.app.repository

import com.protctus.app.data.local.dao.ScanHistoryDao
import com.protctus.app.data.local.entity.ScanHistoryEntity
import com.protctus.app.neuroshield.engine.EngineStatus
import com.protctus.app.neuroshield.engine.NeuroShieldEngine
import com.protctus.app.neuroshield.engine.ThreatVerdict
import com.protctus.app.neuroshield.engine.TrustedDomainCache
import com.protctus.app.neuroshield.engine.VerdictEngine
import com.protctus.edge.phishing.PhishingDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeuroShieldRepository @Inject constructor(
    private val neuroShieldEngine: NeuroShieldEngine,
    private val verdictEngine: VerdictEngine,
    private val trustedDomainCache: TrustedDomainCache,
    private val scanHistoryDao: ScanHistoryDao,
    private val phishingDetector: PhishingDetector? = null // Optional if not provided by Hilt yet
) {

    /**
     * Main entry point for scanning a domain from the UI or external intents.
     * Uses the full PhishingDetector pipeline if available, otherwise just ONNX.
     */
    suspend fun scanDomain(domain: String): ThreatVerdict {
        val verdict = if (phishingDetector != null) {
            phishingDetector.getVerdict(domain)
        } else {
            // Fallback to pure ONNX + Heuristics if full pipeline isn't wired via DI
            val onnxScore = if (neuroShieldEngine.isReady) neuroShieldEngine.infer(domain) else 0f
            verdictEngine.evaluate(domain, onnxScore, 0f, neuroShieldEngine.isReady)
        }

        // Persist to history
        val entity = ScanHistoryEntity(
            domain = domain,
            verdictCode = verdict.verdict.code,
            confidence = verdict.confidence,
            explanation = verdict.explanation,
            triggeredIndicators = verdict.triggeredIndicators.joinToString(","),
            timestamp = verdict.timestamp
        )
        scanHistoryDao.insert(entity)
        
        return verdict
    }

    /**
     * Get the current operational status of the NeuroShield engine.
     */
    fun getEngineStatus(): EngineStatus {
        return neuroShieldEngine.getStatus()
    }

    /**
     * Get a flow of recent scan results.
     */
    fun getRecentScans(limit: Int = 100): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getRecentScans(limit)
    }

    /**
     * Get aggregate statistics.
     */
    suspend fun getStats(): NeuroShieldStats {
        val total = scanHistoryDao.getTotalScanCount().firstOrNull() ?: 0L
        val blocked = scanHistoryDao.getBlockedThreatCount().firstOrNull() ?: 0L
        return NeuroShieldStats(totalScanned = total, threatsBlocked = blocked)
    }

    /**
     * Clean up old scan history (e.g., older than 30 days).
     */
    suspend fun cleanupOldHistory(daysToKeep: Int = 30) {
        val olderThanMs = System.currentTimeMillis() - (daysToKeep * 24L * 60L * 60L * 1000L)
        scanHistoryDao.deleteOlderThan(olderThanMs)
    }
}

data class NeuroShieldStats(
    val totalScanned: Long,
    val threatsBlocked: Long
)
