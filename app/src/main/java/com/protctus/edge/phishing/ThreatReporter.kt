package com.protctus.edge.phishing

/**
 * Callback interface for hybrid server enrichment.
 * Implement in VPN service to report suspicious/blocked domains to backend.
 *
 * Score >= 0.80 → BLOCK + reportBlocked (server confirmation)
 * Score 0.50–0.80 → ALLOW + reportSuspicious (server analysis)
 */
interface ThreatReporter {
    /**
     * Domain scored 0.50-0.80. Allowed locally but sent to server
     * for deeper analysis (HTML content, WHOIS, certificate age).
     */
    fun reportSuspicious(domain: String, score: Float, engineBreakdown: String)

    /**
     * Domain scored >= 0.80. Blocked locally AND reported to server
     * for logging and threat intelligence enrichment.
     */
    fun reportBlocked(domain: String, score: Float, engineBreakdown: String)
}
