package com.protctus.app.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * DnsBurstClusterEngine evaluates DNS requests using Time-Window Heuristics.
 * Prevents "Collateral Blockage" where local ML models falsely block background
 * sub-resources (CDNs, trackers) that load immediately after the main URL.
 */
class DnsBurstClusterEngine {

    // Thread-safe map storing: App UID -> Timestamp of the last DNS request (in ms)
    private val appBurstMap = ConcurrentHashMap<Int, Long>()
    
    // The required network silence (in ms) to consider a request as a new Main Frame
    private val SILENCE_THRESHOLD_MS = 1500L

    enum class EvaluationResult {
        MAIN_FRAME_DOMAIN,
        SUB_RESOURCE
    }

    /**
     * Evaluates whether a domain request is a Main Frame or a cluster child.
     * 
     * @param domain The requested domain (e.g., "example.com")
     * @param uid The App UID making the request
     * @return EvaluationResult dictating whether the ML model should be invoked.
     */
    fun evaluate(domain: String, uid: Int): EvaluationResult {
        val currentTime = System.currentTimeMillis()
        
        // Default to 0 if we've never seen this UID before
        val lastRequestTime = appBurstMap[uid] ?: 0L
        val timeDifference = currentTime - lastRequestTime

        // Update the timestamp to extend the "burst" window. 
        // This resets the 1.5s silence timer on every request made by this UID.
        appBurstMap[uid] = currentTime

        return if (timeDifference > SILENCE_THRESHOLD_MS) {
            // Network silence exceeded 1.5s. This is a user-initiated navigation.
            EvaluationResult.MAIN_FRAME_DOMAIN
        } else {
            // Request arrived within the 1.5s burst window of previous activity.
            EvaluationResult.SUB_RESOURCE
        }
    }
    
    /**
     * Memory management routine to clean up dead UIDs.
     * Should be called periodically (e.g., once an hour via a Coroutine Worker).
     */
    fun evictStaleUids(maxAgeMs: Long = 3600_000L) {
        val currentTime = System.currentTimeMillis()
        appBurstMap.entries.removeIf { currentTime - it.value > maxAgeMs }
    }
}
