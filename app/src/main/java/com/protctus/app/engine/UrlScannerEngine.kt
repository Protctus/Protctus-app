package com.protctus.app.engine

import android.content.Context
import android.util.Log
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.entity.ThreatTelemetry
import com.protctus.app.data.local.entity.UserSafeList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.math.log2

enum class ScanVerdict {
    SAFE, BLOCKED
}

class UrlScannerEngine(private val context: Context) {

    companion object {
        private const val TAG = "UrlScannerEngine"
        private val IP_PATTERN = java.util.regex.Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")
        private const val THRESHOLD_BLOCK = 0.85f

        // In-memory highly trusted allowlist
        private val GLOBAL_ALLOWLIST = setOf(
            "google.com", "gov.in", "apple.com", "microsoft.com", "amazon.com"
        )
    }

    private val db = AppDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // For TFLite ML - Ideally inject or instantiate your TFLite wrapper here.
    // Assuming PhishingDetector or a similar class provides a `predict(url)` method.
    // For this engine implementation, we will use a placeholder function for ML execution.
    private fun executeTFLiteModel(url: String): Float {
        // TODO: Bridge to actual TFLite model execution
        // Example: return tfliteWrapper.predict(url)
        return 0.1f // Default safe score for mock
    }

    /**
     * Core Multi-Layer Detection Pipeline
     */
    suspend fun analyzeUrl(url: String): ScanVerdict {
        val domain = extractDomain(url)

        // Step 1: Allowlisting (Fast O(1) Check)
        if (GLOBAL_ALLOWLIST.contains(domain)) {
            Log.d(TAG, "Step 1: Domain $domain is in Global Allowlist -> SAFE")
            return ScanVerdict.SAFE
        }

        // Check local UserSafeList (User Overrides)
        val isLocallySafe = db.userSafeListDao().isDomainSafe(domain)
        if (isLocallySafe) {
            Log.d(TAG, "Step 1: Domain $domain is in Local UserSafeList -> SAFE")
            return ScanVerdict.SAFE
        }

        // Step 2: Heuristic Analysis
        val heuristicScore = calculateHeuristicRisk(domain)
        if (heuristicScore >= 1.0f) { // Immediate block for raw IP
            Log.d(TAG, "Step 2: Heuristic indicates severe risk -> BLOCKED")
            queueTelemetry(domain, heuristicScore, "IP_HEURISTIC", isFalsePositive = false)
            return ScanVerdict.BLOCKED
        }

        // Step 3: ML Execution
        val mlConfidence = executeTFLiteModel(url)
        
        // Combine ML confidence with Heuristic base risk
        val finalScore = (mlConfidence + heuristicScore).coerceIn(0.0f, 1.0f)
        
        Log.d(TAG, "Step 3: ML= $mlConfidence, Heuristic= $heuristicScore, Final= $finalScore")

        return if (finalScore >= THRESHOLD_BLOCK) {
            queueTelemetry(domain, finalScore, "ML_PHISHING", isFalsePositive = false)
            ScanVerdict.BLOCKED
        } else {
            ScanVerdict.SAFE
        }
    }

    /**
     * Step 4: User Override
     * Marks a blocked URL as a False Positive. Adds to local allowlist and queues telemetry.
     */
    fun markAsFalsePositive(domain: String) {
        scope.launch {
            // Add to Local SafeList
            db.userSafeListDao().markAsSafe(UserSafeList(domain = domain, addedAt = System.currentTimeMillis()))
            
            // Queue Telemetry to correct global model
            queueTelemetry(domain, 1.0f, "FALSE_POSITIVE_OVERRIDE", isFalsePositive = true)
            Log.d(TAG, "Step 4: Marked $domain as False Positive and queued telemetry.")
        }
    }

    private fun calculateHeuristicRisk(domain: String): Float {
        var score = 0.0f

        // 1. Raw IP Check
        if (IP_PATTERN.matcher(domain).matches()) {
            return 1.0f // Max risk
        }

        // 2. Excessive Subdomains
        val dotCount = domain.count { it == '.' }
        if (dotCount >= 4) {
            score += 0.15f
        }

        // 3. Shannon Entropy Check
        val entropy = calculateShannonEntropy(domain)
        if (entropy > 4.2) {
            score += 0.20f
        }

        return score.coerceIn(0.0f, 0.4f) // Cap heuristic bonus
    }

    private fun calculateShannonEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val len = s.length.toDouble()
        val counts = IntArray(128)
        for (c in s) {
            val code = c.code
            if (code < 128) counts[code]++
        }
        var entropy = 0.0
        for (count in counts) {
            if (count > 0) {
                val p = count / len
                entropy -= p * log2(p)
            }
        }
        return entropy
    }

    private fun extractDomain(url: String): String {
        var clean = url.trim().lowercase()
        if (clean.startsWith("http://")) clean = clean.substring(7)
        if (clean.startsWith("https://")) clean = clean.substring(8)
        return clean.substringBefore('/')
    }

    private fun queueTelemetry(domain: String, confidence: Float, type: String, isFalsePositive: Boolean) {
        scope.launch {
            val hash = hashDomainSha256(domain)
            val record = ThreatTelemetry(
                domainHashSha256 = hash,
                confidenceScore = confidence,
                threatType = type,
                timestamp = System.currentTimeMillis(),
                isFalsePositive = isFalsePositive
            )
            db.threatTelemetryDao().insertTelemetry(record)
            Log.d(TAG, "Queued telemetry for hash: $hash")
        }
    }

    private fun hashDomainSha256(domain: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(domain.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
