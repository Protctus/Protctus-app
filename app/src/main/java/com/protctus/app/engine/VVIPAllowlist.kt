package com.protctus.app.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.entity.VvipDomain
import kotlinx.coroutines.*

/**
 * VVIPAllowlist — Layer 1 of the 3-Tier Defense Architecture.
 *
 * Stores the Tranco Top-380K domains in a Room DB table (indexed on PK)
 * for O(log n) lookups using SQLite's B-tree. Zero persistent RAM usage.
 *
 * A small LRU cache (500 entries, ~25KB RAM) sits in front of the DB
 * to accelerate repeated hot-path lookups on the VPN interceptor.
 *
 * POPULATION STRATEGY:
 *   On first launch after install/update, the asset file (vvip_domains.txt, ~5MB)
 *   is read line-by-line and batch-inserted into the DB in chunks of 5,000.
 *   This runs on a background coroutine (~3-8 seconds on a mid-range device)
 *   and is a one-time cost. During population, lookups that miss the LRU cache
 *   simply fall through to the ML model — identical to pre-VVIP behaviour.
 */
object VVIPAllowlist {

    private const val TAG = "VVIPAllowlist"
    private const val ASSET_FILE = "vvip_domains.txt"
    private const val BATCH_SIZE = 5000

    /**
     * Tiny LRU cache to avoid hitting SQLite on repeated lookups for the same host.
     * 500 entries × ~50 bytes = ~25KB. Negligible RAM, massive hot-path speedup.
     */
    private val lookupCache = LruCache<String, Boolean>(500)

    /**
     * Supplementary domains critical for Indian users that may fall outside Tranco Top-380K.
     */
    private val SUPPLEMENTARY_DOMAINS = arrayOf(
        "onlinesbi.sbi",
        "phonepe.com",
        "axisbank.com",
        "kotak.com",
        "bseindia.com",
        "nseindia.com",
        "incometax.gov.in",
        "digilocker.gov.in",
        "nsdl.co.in",
        "epfindia.gov.in",
        "jiomart.com",
        "olaelectric.com",
        "makemytrip.com",
        "yesbank.in",
        "bankofbaroda.in",
        "pnbindia.in",
        "indianbank.in",
        "canarabank.com",
        "unionbankofindia.co.in",
        "npci.org.in"
    )

    /**
     * Initialise and populate the VVIP domains table from the asset file if empty.
     * Call from Application.onCreate(). Population runs on a background coroutine
     * so it does NOT block app startup.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val dao = AppDatabase.getInstance(appContext).vvipDomainDao()
                val existingCount = dao.count()

                if (existingCount > 0) {
                    Log.i(TAG, "VVIP table already populated ($existingCount domains). Skipping.")
                    return@launch
                }

                val startTime = System.nanoTime()
                var totalInserted = 0

                // Read asset and batch-insert in chunks
                appContext.assets.open(ASSET_FILE).bufferedReader().useLines { lines ->
                    val batch = mutableListOf<VvipDomain>()
                    lines.forEach { line ->
                        val domain = line.trim()
                        if (domain.isNotEmpty()) {
                            batch.add(VvipDomain(domain))
                            if (batch.size >= BATCH_SIZE) {
                                dao.insertAll(batch.toList())
                                totalInserted += batch.size
                                batch.clear()
                            }
                        }
                    }
                    // Flush remaining
                    if (batch.isNotEmpty()) {
                        dao.insertAll(batch.toList())
                        totalInserted += batch.size
                    }
                }

                // Insert supplementary Indian domains
                val supplementary = SUPPLEMENTARY_DOMAINS.map { VvipDomain(it) }
                dao.insertAll(supplementary)
                totalInserted += supplementary.size

                val elapsed = (System.nanoTime() - startTime) / 1_000_000
                Log.i(TAG, "VVIP table populated: $totalInserted domains in ${elapsed}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to populate VVIP domains table", e)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Extracts a clean, lowercase hostname from any input (full URL or bare domain).
     * Uses Android's [Uri] parser with a scheme fallback for bare domains.
     */
    fun extractCleanHost(urlOrDomain: String): String {
        var input = urlOrDomain.trim().lowercase()
        if (!input.contains("://")) {
            input = "http://$input"
        }
        return try {
            Uri.parse(input).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Checks if the domain (or any parent domain) exists in the VVIP table.
     *
     * Performs subdomain stripping and issues a single IN-clause DB query:
     *   "mail.google.com" → candidates: ["mail.google.com", "google.com"] → MATCH ✅
     *   "google.com.scam.xyz" → candidates: ["google.com.scam.xyz", "com.scam.xyz", "scam.xyz"] → NO MATCH ✅
     *
     * Results are cached in an LRU cache to avoid redundant DB hits on the VPN hot-path.
     *
     * @param urlOrDomain Full URL or bare domain.
     * @return true if the base domain is in the VVIP allowlist.
     */
    suspend fun isDomainSafe(context: Context, urlOrDomain: String): Boolean {
        val host = extractCleanHost(urlOrDomain)
        if (host.isEmpty()) return false

        // Check LRU cache first (O(1), ~0μs)
        lookupCache.get(host)?.let { return it }

        // Build candidate list by stripping subdomains left-to-right
        val candidates = mutableListOf<String>()
        var current = if (host.startsWith("www.")) host.substring(4) else host

        while (current.contains('.')) {
            candidates.add(current)
            val dotIndex = current.indexOf('.')
            if (dotIndex >= current.length - 1) break
            current = current.substring(dotIndex + 1)
        }

        if (candidates.isEmpty()) {
            lookupCache.put(host, false)
            return false
        }

        // Single DB roundtrip with IN clause
        val dao = AppDatabase.getInstance(context.applicationContext).vvipDomainDao()
        val result = dao.isAnyDomainSafe(candidates)

        // Cache the result for this host
        lookupCache.put(host, result)
        return result
    }
}
