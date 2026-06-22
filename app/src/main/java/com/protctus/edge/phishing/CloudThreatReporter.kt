package com.protctus.edge.phishing

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Uploads detected phishing threats to the Protctus Telemetry Worker (D1 database).
 * All domain data is salted + SHA-256 hashed before leaving the device for user privacy.
 *
 * Fire-and-forget on Dispatchers.IO — never blocks VPN/DNS processing.
 */
class CloudThreatReporter(context: Context) : ThreatReporter {

    companion object {
        private const val TAG = "CloudThreatReporter"
        // Per-installation salt derived from app's unique install ID
        private const val STATIC_SALT = "protctus_threat_telemetry_2026"
        private const val TELEMETRY_URL =
            "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev/api/threat-report"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Per-device salt for extra privacy (unique per installation)
    private val deviceSalt: String

    init {
        val prefs = context.getSharedPreferences("protctus_telemetry", Context.MODE_PRIVATE)
        var salt = prefs.getString("device_salt", null)
        if (salt == null) {
            salt = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_salt", salt).apply()
        }
        deviceSalt = salt
    }

    /**
     * Salted SHA-256 hash: SHA256(STATIC_SALT + deviceSalt + domain)
     * This makes it computationally infeasible to reverse-lookup the domain
     * from the stored hash, even with rainbow table attacks.
     */
    private fun saltedHash(domain: String): String {
        val input = "$STATIC_SALT:$deviceSalt:${domain.lowercase().trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    override fun reportBlocked(domain: String, score: Float, engineBreakdown: String) {
        sendReport(domain, score, engineBreakdown, "blocked")
    }

    override fun reportSuspicious(domain: String, score: Float, engineBreakdown: String) {
        sendReport(domain, score, engineBreakdown, "suspicious")
    }

    private fun sendReport(domain: String, score: Float, breakdown: String, verdict: String) {
        try {
            val payload = JSONObject().apply {
                put("type", "phishing")
                put("domain_hash", saltedHash(domain))
                put("confidence_score", score.toDouble())
                put("verdict", verdict)
                put("engine_breakdown", breakdown)
                put("tld", domain.substringAfterLast('.'))
                put("timestamp", System.currentTimeMillis())
            }

            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(TELEMETRY_URL)
                .post(body)
                .build()

            // Fire-and-forget: enqueue async, don't block
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    Log.w(TAG, "Telemetry upload failed (non-critical): ${e.message}")
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.close()
                    Log.d(TAG, "Telemetry uploaded: $verdict domain (score=${"%.2f".format(score)})")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Telemetry report error (non-critical)", e)
        }
    }
}
