package com.protctus.vpncore

/**
 * Callback triggered natively when a DNS request is parsed by the C/C++ engine.
 * IMPORTANT: This must be evaluated synchronously and extremely fast (< 2ms) to avoid blocking the TUN thread.
 */
interface DnsInterceptorCallback {
    
    /**
     * Evaluates a domain name extracted natively from a DNS UDP packet on port 53.
     * 
     * @param domain The domain string (e.g., "google.com", "malicious-c2.net")
     * @return `true` if the domain is malicious and the native engine should instantly forge a 0.0.0.0 sinkhole response.
     *         `false` to allow the packet to pass through normally.
     */
    fun isDomainMalicious(domain: String): Boolean
}
