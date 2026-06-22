package com.protctus.app.repository

import android.content.Context
import android.util.Log
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.entity.UserSafeList
import com.protctus.app.domain.model.DomainSafetyStatus
import com.protctus.app.engine.OfflinePhishingModel
import com.protctus.app.engine.VVIPAllowlist

/**
 * PhishingDomainEvaluator — The core interceptor for the 3-Tier Defense Architecture.
 *
 * EXECUTION ORDER (cheapest-to-most-expensive bypass):
 *   1. VVIP Allowlist   — LRU cache hit ~0μs, DB miss ~0.2ms. 380K Tranco domains.
 *   2. User Safe List   — Single indexed Room DB query. Costs ~0.2-1ms. Bypasses ML.
 *   3. TFLite ML Model  — On-device inference. Costs ~5-15ms. With thresholded output.
 *
 * This class is designed to be instantiated once and reused (e.g., from a VPN service
 * or a ViewModel), as it holds a reference to the TFLite interpreter.
 */
class PhishingDomainEvaluator(context: Context) {

    private val tag = "PhishingEvaluator"

    // Confidence thresholds — match to the user-specified detection contract.
    companion object {
        const val AUTO_BLOCK_THRESHOLD = 0.85f
        const val WARNING_THRESHOLD = 0.60f
    }

    // Hold app context to avoid leaking Activity context across long-lived service lifecycle.
    private val appContext = context.applicationContext

    // TFLite interpreter — expensive to create; init once and reuse.
    private val mlModel = OfflinePhishingModel(appContext)

    // Room DAO reference — accessed via the existing singleton AppDatabase instance.
    private val userSafeListDao by lazy {
        AppDatabase.getInstance(appContext).userSafeListDao()
    }

    /**
     * Evaluates a URL or bare domain through all 3 defense layers in strict priority order.
     *
     * This is a suspend function because Layer 2 requires a DB coroutine query.
     * Layer 1 and Layer 3 are synchronous but safe to call on any dispatcher.
     * For VPN use-cases, call this from a Dispatchers.IO context.
     *
     * @param url Full URL (e.g., "https://mail.google.com/path") or bare domain.
     * @return [DomainSafetyStatus] describing the pipeline's verdict.
     */
    suspend fun evaluateDomain(url: String): DomainSafetyStatus {
        // ─── STEP 1: Extract a normalised base host from the input ───────────────────
        val host = VVIPAllowlist.extractCleanHost(url)
        val baseDomain = host.removePrefix("www.")

        if (baseDomain.isEmpty()) {
            // Cannot evaluate a malformed URL — fail open to avoid blocking valid traffic.
            Log.w(tag, "Malformed URL skipped: $url")
            return DomainSafetyStatus.SAFE
        }

        // ─── LAYER 1: VVIP Allowlist (LRU ~0μs / DB ~0.2ms) ─────────────────────────
        if (VVIPAllowlist.isDomainSafe(appContext, baseDomain)) {
            Log.d(tag, "[$baseDomain] -> LAYER 1 BYPASS (VVIP Allowlist) -> SAFE")
            return DomainSafetyStatus.SAFE
        }

        // ─── LAYER 2: User Safe List (Room DB, ~1-3ms) ───────────────────────────────
        if (userSafeListDao.isDomainSafe(baseDomain)) {
            Log.d(tag, "[$baseDomain] -> LAYER 2 BYPASS (User Safe List) -> SAFE")
            return DomainSafetyStatus.SAFE
        }

        // ─── LAYER 3: TFLite ML Model with Confidence Thresholds (~5-15ms) ───────────
        // Pass the full URL for maximum feature extraction by the char-map model.
        val score = mlModel.evaluateUrl(url)

        val status = when {
            score < 0f -> {
                // Model returned -1.0f, indicating an internal failure. Fail open.
                Log.e(tag, "[$baseDomain] -> ML inference failed. Defaulting to SAFE.")
                DomainSafetyStatus.SAFE
            }
            score >= AUTO_BLOCK_THRESHOLD -> DomainSafetyStatus.MALICIOUS_AUTO_BLOCK
            score >= WARNING_THRESHOLD    -> DomainSafetyStatus.SUSPICIOUS_WARNING
            else                          -> DomainSafetyStatus.SAFE
        }

        Log.d(tag, "[$baseDomain] -> ML Score: $score -> $status")
        return status
    }

    /**
     * Adds a domain to the local Room DB safe list. Called when a user clicks
     * "I trust this site / Continue anyway" on the SUSPICIOUS_WARNING UI prompt.
     *
     * This is idempotent — safely re-inserting an existing domain uses REPLACE strategy.
     *
     * @param domain The base domain to trust (e.g., "mybankportal.com").
     *               You can also pass a full URL and the function will extract the host.
     */
    suspend fun markDomainAsSafe(domain: String) {
        // Normalise the input so the UI can pass either bare domains or full URLs.
        val rawHost = if (domain.contains("://")) {
            VVIPAllowlist.extractCleanHost(domain).removePrefix("www.")
        } else {
            domain.trim().lowercase().removePrefix("www.")
        }

        if (rawHost.isEmpty() || !rawHost.contains(".")) {
            Log.w(tag, "markDomainAsSafe: Invalid domain '$domain', skipping.")
            return
        }

        userSafeListDao.markAsSafe(UserSafeList(domain = rawHost))
        Log.i(tag, "Domain '$rawHost' added to User Safe List.")
    }

    /**
     * Release the TFLite interpreter. Call this in the onDestroy() of the hosting
     * Service or when the ViewModel is cleared to avoid native memory leaks.
     */
    fun close() {
        mlModel.close()
    }
}
