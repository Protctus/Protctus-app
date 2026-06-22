package com.protctus.app.engine.ml

import java.util.concurrent.ConcurrentHashMap

data class TrackerState(
    val triggerDomain: String,
    val windowExpiryTimeMs: Long
)

/**
 * StatefulUidTracker manages Phase 3 and Phase 4 of the Phishing Pipeline.
 * It tracks traffic bursts per UID and opens stateful Suspicion Windows 
 * specifically bound to the application that hit a URL shortener.
 */
class StatefulUidTracker {
    
    // Known redirectors/trackers that trigger the suspicion window
    private val knownShorteners = setOf(
        "bit.ly", "t.co", "sendgrid.net", "tinyurl.com", "goo.gl", "ow.ly", "tiny.cc", "is.gd"
    )

    // Thread-safe map: App UID -> TrackerState
    private val suspicionWindows = ConcurrentHashMap<Int, TrackerState>()

    // Thread-safe map: App UID -> Last Request Timestamp for bursting (Phase 3)
    private val burstMap = ConcurrentHashMap<Int, Long>()

    // Architectural constraints
    private val SUSPICION_WINDOW_MS = 3000L
    private val BURST_WINDOW_MS = 500L

    enum class RequestType {
        SAFE_KNOWN,         // Pre-verified as benign CDNs/APIs (Handled by C++ Bloom Filter)
        SHORTENER_TRIGGER,  // Hit a shortener, starts a suspicion window
        REDIRECT_TARGET,    // Hit during an active window -> High Suspicion Target
        NEW_BURST_INITIATOR,// Start of a new traffic burst (> 500ms silence)
        SUB_RESOURCE        // Occurs within 500ms of a burst initiator
    }

    /**
     * Evaluates a domain request based on its UID and temporal context.
     */
    fun evaluateRequest(domain: String, uid: Int): RequestType {
        val currentTime = System.currentTimeMillis()

        // 1. Clean up expired suspicion windows lazily to prevent memory leaks
        cleanExpiredWindows(currentTime)

        // 2. Check Stateful Redirect Logic (Phase 4)
        val activeTracker = suspicionWindows[uid]
        if (activeTracker != null && currentTime <= activeTracker.windowExpiryTimeMs) {
            if (isKnownShortener(domain)) {
                // User hit another redirector in the chain. Extend the window.
                suspicionWindows[uid] = TrackerState(domain, currentTime + SUSPICION_WINDOW_MS)
                burstMap[uid] = currentTime
                return RequestType.SHORTENER_TRIGGER
            } else {
                // This is an unrecognized domain from the EXACT SAME UID within 3 seconds.
                // This is the target of the redirect. VERY HIGH SUSPICION.
                // Close the window as we've found the target to prevent false positives.
                suspicionWindows.remove(uid)
                burstMap[uid] = currentTime
                return RequestType.REDIRECT_TARGET
            }
        }

        // 3. Trigger new suspicion window if domain is a shortener
        if (isKnownShortener(domain)) {
            suspicionWindows[uid] = TrackerState(domain, currentTime + SUSPICION_WINDOW_MS)
            burstMap[uid] = currentTime
            return RequestType.SHORTENER_TRIGGER
        }

        // 4. UID-Bound Temporal Grouping (Phase 3)
        val lastRequestTime = burstMap[uid] ?: 0L
        burstMap[uid] = currentTime

        return if (currentTime - lastRequestTime > BURST_WINDOW_MS) {
            // New request after 500ms silence -> Initiator
            RequestType.NEW_BURST_INITIATOR
        } else {
            // Happened within 500ms of previous -> Background Sub-resource
            RequestType.SUB_RESOURCE
        }
    }

    private fun isKnownShortener(domain: String): Boolean {
        // Simple heuristic: Does it end with a known shortener root?
        return knownShorteners.any { domain.endsWith(it, ignoreCase = true) }
    }

    private fun cleanExpiredWindows(currentTime: Long) {
        suspicionWindows.entries.removeIf { currentTime > it.value.windowExpiryTimeMs }
    }
    
    /**
     * Call periodically (e.g., via WorkManager) to prevent unbounded memory growth.
     */
    fun evictStaleUids(maxAgeMs: Long = 3600_000L) {
        val currentTime = System.currentTimeMillis()
        burstMap.entries.removeIf { currentTime - it.value > maxAgeMs }
        cleanExpiredWindows(currentTime)
    }
}
