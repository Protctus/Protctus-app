package com.protctus.app.domain.model

/**
 * Represents the output state of the 3-Tier Phishing Defense Pipeline.
 *
 * SAFE              -> Score < 0.60, or domain is in VVIP Allowlist / User Safe List.
 *                      VPN allows traffic to pass uninterrupted.
 *
 * SUSPICIOUS_WARNING -> Score >= 0.60 and < 0.85 (Grey zone).
 *                       VPN should show a UI warning prompt, letting the user decide.
 *
 * MALICIOUS_AUTO_BLOCK -> Score >= 0.85 (High-confidence threat).
 *                         VPN sinkholes the request. No user prompt needed.
 */
enum class DomainSafetyStatus {
    SAFE,
    SUSPICIOUS_WARNING,
    MALICIOUS_AUTO_BLOCK
}
