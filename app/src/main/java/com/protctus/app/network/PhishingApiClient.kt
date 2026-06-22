package com.protctus.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.google.gson.annotations.SerializedName
import okio.Buffer


data class ScanRequest(val url: String)

/**
 * Enhanced response model for the Protctus RBI Worker.
 * The backend scrapes HTML/JS from the URL, then runs Llama3 8B AI analysis
 * on the scraped content to detect phishing, credential harvesting, and brand impersonation.
 *
 * All fields use sensible defaults so the model is backward-compatible if the
 * worker returns only the basic fields (status, riskScore, reason).
 */

data class ScanResponse(
    val status: String,              // "SAFE", "SUSPICIOUS", "MALICIOUS", "NEEDS_HYDRATION", "ERROR", "SCANNED"
    @SerializedName("threat_score") val riskScore: Int = 0, // 0-100
    val reason: String = "",         // Primary human-readable verdict
    // ── Enhanced fields from analysis ──
    val analysisDepth: String = "STATIC",  
    val aiVerdict: String = "",            
    val hasLoginForm: Boolean = false,     
    val detectedBrand: String = "",        
    val redirectCount: Int = 0,            
    val finalUrl: String = "",             
    val sslValid: Boolean = true,          
    val pageTitle: String = "",            
    @SerializedName("flags") val threats: List<String> = emptyList() // List of specific threats detected
)

interface PhishingApi {
    @Headers("Content-Type: application/json")
    @POST(".") // Relative to BASE_URL
    suspend fun scanUrl(@Body request: ScanRequest): ScanResponse
}

object PhishingApiClient {
    private const val BASE_URL = "https://protctus-phishing-engine.jatinsharmashivpuri02122008.workers.dev/"

    val api: PhishingApi by lazy {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val apiSecret = com.protctus.app.BuildConfig.PHISHING_API_KEY

                // Dynamic HMAC-SHA256 Payload Signing
                val timestamp = (System.currentTimeMillis() / 1000).toString()
                val nonce = UUID.randomUUID().toString().replace("-", "")

                // Read the request body for signing
                val bodyHash = original.body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val bodyBytes = buffer.readByteArray()
                    bytesToHex(java.security.MessageDigest.getInstance("SHA-256").digest(bodyBytes))
                } ?: ""

                // Sign: HMAC-SHA256(secret, timestamp + nonce + bodyHash)
                val message = "$timestamp.$nonce.$bodyHash"
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                val signature = bytesToHex(mac.doFinal(message.toByteArray(Charsets.UTF_8)))

                val request = original.newBuilder()
                    .header("X-Protctus-Auth", apiSecret)
                    .header("X-Timestamp", timestamp)
                    .header("X-Nonce", nonce)
                    .header("X-Protctus-Signature", signature)
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PhishingApi::class.java)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}
