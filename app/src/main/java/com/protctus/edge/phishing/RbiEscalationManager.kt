package com.protctus.edge.phishing

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Async Cloud RBI Enrichment via Llama3 8B.
 *
 * NEVER blocks the DNS packet loop. All calls are fire-and-forget.
 * Results are cached in persistent blacklist/safelist for instant future decisions.
 *
 * Flow:
 *   1. PhishingDetector runs local ML → makes instant decision (~2ms)
 *   2. For suspicious/hosting-platform domains, calls escalate() → fires background coroutine
 *   3. RBI Worker scrapes HTML/JS, feeds to Llama3 → returns verdict
 *   4. If MALICIOUS → domain blacklisted → next DNS query blocked in ~0μs
 *   5. If SAFE → domain whitelisted → next DNS query skips ML entirely
 */
object RbiEscalationManager {

    private const val TAG = "RbiEscalation"
    private const val RBI_WORKER_URL = "https://protctus-rbi-worker.jatinsharmashivpuri02122008.workers.dev/"
    private const val PREFS_NAME = "protctus_rbi_cache"
    private const val KEY_BLACKLIST = "rbi_blacklist"
    private const val KEY_VERIFIED_SAFE = "rbi_verified_safe"

    private val pendingDomains = ConcurrentHashMap.newKeySet<String>()
    private val blacklistedDomains = ConcurrentHashMap.newKeySet<String>()
    private val verifiedSafeDomains = ConcurrentHashMap.newKeySet<String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedBlacklist = prefs?.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()
        val savedSafe = prefs?.getStringSet(KEY_VERIFIED_SAFE, emptySet()) ?: emptySet()
        blacklistedDomains.addAll(savedBlacklist)
        verifiedSafeDomains.addAll(savedSafe)
        Log.d(TAG, "Init: ${blacklistedDomains.size} blacklisted, ${verifiedSafeDomains.size} verified safe")
    }

    /** O(1) blacklist check — called by PhishingDetector before ML. */
    fun isBlacklisted(domain: String): Boolean = blacklistedDomains.contains(domain.lowercase())

    /** O(1) safelist check — skips ML for RBI-verified safe domains. */
    fun isVerifiedSafe(domain: String): Boolean = verifiedSafeDomains.contains(domain.lowercase())

    /**
     * BLOCKING inline RBI scan — used for unknown domains (like thecool.com).
     * This blocks the caller (which we will put in an IO Coroutine) for ~800ms
     * to get a PERFECT Llama3 verdict BEFORE allowing the DNS packet through.
     *
     * @return true if the domain is confirmed MALICIOUS, false otherwise.
     */
    fun escalateNow(context: Context, domain: String): Boolean {
        val d = domain.lowercase().trim()

        if (blacklistedDomains.contains(d)) return true
        if (verifiedSafeDomains.contains(d)) return false

        return try {
            val url = if (d.startsWith("http")) d else "https://$d"
            val json = JSONObject().apply { put("url", url) }.toString()

            val request = Request.Builder()
                .url(RBI_WORKER_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .addHeader("X-API-Key", com.protctus.app.BuildConfig.PHISHING_API_KEY)
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && body != null) {
                val result = JSONObject(body)
                val status = result.optString("status", "UNKNOWN").uppercase()
                val reason = result.optString("reason", "")

                Log.d(TAG, "RBI NOW → $d: $status — $reason")

                when (status) {
                    "MALICIOUS" -> {
                        addToBlacklist(d)
                        broadcastAlert(context, d, reason)
                        try { DetectionStats(context).incrementRbiConfirmedCount() } catch (_: Exception) {}
                        true
                    }
                    "SAFE" -> {
                        addToVerifiedSafe(d)
                        false
                    }
                    else -> false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "RBI NOW failed: $d — ${e.message}")
            false
        }
    }

    /**
     * Fire-and-forget async RBI scan. NEVER blocks.
     * DNS is forwarded immediately by the caller.
     * If confirmed malicious, domain is blacklisted for instant future blocking.
     */
    fun escalate(context: Context, domain: String, localScore: Float) {
        val d = domain.lowercase().trim()

        // Dedup: don't re-scan domains already pending or resolved
        if (pendingDomains.contains(d) || blacklistedDomains.contains(d) || verifiedSafeDomains.contains(d)) return

        pendingDomains.add(d)
        Log.d(TAG, "Escalating → $d (local: ${"%.3f".format(localScore)})")

        scope.launch {
            try {
                val url = if (d.startsWith("http")) d else "https://$d"
                val json = JSONObject().apply { put("url", url) }.toString()

                val request = Request.Builder()
                    .url(RBI_WORKER_URL)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .addHeader("X-API-Key", com.protctus.app.BuildConfig.PHISHING_API_KEY)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val result = JSONObject(body)
                    val status = result.optString("status", "UNKNOWN").uppercase()
                    val reason = result.optString("reason", "")

                    Log.d(TAG, "RBI → $d: $status — $reason")

                    when (status) {
                        "MALICIOUS" -> {
                            addToBlacklist(d)
                            broadcastAlert(context, d, reason)
                            try { DetectionStats(context).incrementRbiConfirmedCount() } catch (_: Exception) {}
                        }
                        "SAFE" -> addToVerifiedSafe(d)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RBI failed: $d — ${e.message}")
            } finally {
                pendingDomains.remove(d)
            }
        }
    }

    private fun addToBlacklist(domain: String) {
        blacklistedDomains.add(domain)
        persistSet(KEY_BLACKLIST, blacklistedDomains)
    }

    private fun addToVerifiedSafe(domain: String) {
        verifiedSafeDomains.add(domain)
        if (verifiedSafeDomains.size > 5000) {
            verifiedSafeDomains.take(1000).forEach { verifiedSafeDomains.remove(it) }
        }
        persistSet(KEY_VERIFIED_SAFE, verifiedSafeDomains)
    }

    private fun persistSet(key: String, set: MutableSet<String>) {
        prefs?.edit()?.putStringSet(key, set.toSet())?.apply()
    }

    private fun broadcastAlert(context: Context, domain: String, reason: String) {
        try {
            val intent = Intent("com.protctus.app.ACTION_PHISHING_ALERT").apply {
                setPackage("com.protctus.app")
                putExtra("domain", domain)
                putExtra("reason", "AI Analysis: $reason")
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast failed", e)
        }
    }

    fun getBlacklistSize(): Int = blacklistedDomains.size

    fun clearAll() {
        blacklistedDomains.clear()
        verifiedSafeDomains.clear()
        pendingDomains.clear()
        prefs?.edit()?.clear()?.apply()
    }
}
