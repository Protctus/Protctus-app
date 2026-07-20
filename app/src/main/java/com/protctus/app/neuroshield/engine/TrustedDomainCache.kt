package com.protctus.app.neuroshield.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Trusted Domain Cache — LRU Cache with TTL-based Eviction
 *
 * Caches recent analysis results to avoid re-analyzing the same domain
 * within a short time window. This is critical for DNS-path performance
 * since the same domain may be queried multiple times during a single
 * page load (A record, AAAA record, CNAME chain, etc.).
 *
 * Design:
 * - ConcurrentHashMap for lock-free thread safety.
 * - TTL-based eviction: SAFE results live longer than SUSPICIOUS ones.
 * - Size-bounded: evicts oldest entries when capacity is exceeded.
 * - No background threads: eviction is done lazily during put/get.
 */
class TrustedDomainCache(
    private val maxSize: Int = 10_000,
    private val safeTtlMs: Long = 300_000L,        // 5 minutes for SAFE domains
    private val suspiciousTtlMs: Long = 60_000L,   // 1 minute for SUSPICIOUS domains
    private val phishingTtlMs: Long = 600_000L     // 10 minutes for PHISHING domains (stable classification)
) {
    private data class CacheEntry(
        val verdict: ThreatVerdict,
        val insertedAtMs: Long = System.currentTimeMillis()
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>(maxSize / 2)

    /**
     * Look up a cached verdict for the given domain.
     *
     * @return The cached [ThreatVerdict] if present and not expired, null otherwise.
     */
    fun get(domain: String): ThreatVerdict? {
        val key = normalize(domain)
        val entry = cache[key] ?: return null

        val ttl = ttlFor(entry.verdict.verdict)
        val age = System.currentTimeMillis() - entry.insertedAtMs

        return if (age <= ttl) {
            entry.verdict
        } else {
            cache.remove(key)
            null
        }
    }

    /**
     * Store a verdict in the cache.
     *
     * If the cache exceeds [maxSize], a lazy eviction pass removes
     * expired entries. If still over capacity, the oldest entries are removed.
     */
    fun put(domain: String, verdict: ThreatVerdict) {
        val key = normalize(domain)
        cache[key] = CacheEntry(verdict)

        // Lazy eviction when over capacity
        if (cache.size > maxSize) {
            evict()
        }
    }

    /**
     * Check if a domain is cached (without returning the full verdict).
     * Useful for fast-path decisions in the VPN loop.
     */
    fun contains(domain: String): Boolean {
        return get(normalize(domain)) != null
    }

    /**
     * Remove a specific domain from the cache.
     * Used when the user explicitly marks a domain as safe or blocked.
     */
    fun invalidate(domain: String) {
        cache.remove(normalize(domain))
    }

    /** Clear all cached entries. */
    fun clear() {
        cache.clear()
    }

    /** Current number of cached entries (including potentially expired ones). */
    val size: Int get() = cache.size

    private fun normalize(domain: String): String {
        return domain.trim().lowercase().removeSuffix(".")
    }

    private fun ttlFor(verdict: VerdictLevel): Long {
        return when (verdict) {
            VerdictLevel.SAFE -> safeTtlMs
            VerdictLevel.SUSPICIOUS -> suspiciousTtlMs
            VerdictLevel.PHISHING -> phishingTtlMs
            VerdictLevel.REQUIRES_NETWORK_VERIFICATION -> suspiciousTtlMs
        }
    }

    private fun evict() {
        val now = System.currentTimeMillis()

        // First pass: remove expired entries
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val ttl = ttlFor(entry.value.verdict.verdict)
            if (now - entry.value.insertedAtMs > ttl) {
                iterator.remove()
            }
        }

        // Second pass: if still over capacity, remove oldest entries
        if (cache.size > maxSize) {
            val sorted = cache.entries.sortedBy { it.value.insertedAtMs }
            val toRemove = cache.size - (maxSize * 3 / 4) // Trim to 75% capacity
            for (i in 0 until toRemove.coerceAtMost(sorted.size)) {
                cache.remove(sorted[i].key)
            }
        }
    }
}
