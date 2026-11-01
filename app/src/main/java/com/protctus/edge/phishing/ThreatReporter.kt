package com.protctus.edge.phishing

/**
 * Callback interface for hybrid server enrichment.
 * Implement in VPN service to report suspicious/blocked domains to backend.
 *
 * All methods are suspending so implementations can consult async consent
 * state (e.g., DataStore) before any data leaves the device. Callers already
 * run inside a coroutine (see PhishingDetector.analyzeDomainRisk*), so the
 * suspending calls suspend only the reporting coroutine, never the DNS path.
 *
 * Score >= 0.80 → BLOCK + reportBlocked (server confirmation)
 * Score 0.50–0.80 → ALLOW + reportSuspicious (server analysis)
 */
interface ThreatReporter {
    /**
     * Domain scored 0.50-0.80. Allowed locally but sent to server
     * for deeper analysis (HTML content, WHOIS, certificate age).
     * No-op unless the user has explicitly enabled cloud telemetry sharing.
     */
    suspend fun reportSuspicious(domain: String, score: Float, engineBreakdown: String)

    /**
     * Domain scored >= 0.80. Blocked locally AND reported to server
     * for logging and threat intelligence enrichment.
     * No-op unless the user has explicitly enabled cloud telemetry sharing.
     */
    suspend fun reportBlocked(domain: String, score: Float, engineBreakdown: String)
}
