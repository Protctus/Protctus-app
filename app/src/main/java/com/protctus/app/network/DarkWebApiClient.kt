package com.protctus.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class BreachDetail(
    val name: String,
    val domain: String = "",
    val breachDate: String = "Unknown",
    val dataClasses: List<String> = emptyList(),
    val description: String = "",
    val pwnCount: Long = 0L,
    val isSensitive: Boolean = false
)

data class DarkWebScanResult(
    val isSafe: Boolean,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val breachCount: Int = 0,
    val details: List<String> = emptyList(),
    val breaches: List<BreachDetail> = emptyList(),
    val scannedEmail: String = "",
    val scanTimestamp: Long = System.currentTimeMillis()
)

object DarkWebApiClient {
    private const val TAG = "DarkWebApiClient"
    private val client = SecureHttpClient.default
    private const val MAX_RETRIES = 3

    suspend fun scanEmail(email: String): DarkWebScanResult = withContext(Dispatchers.IO) {
        var currentRetry = 0
        var backoffMs = 2000L

        while (currentRetry <= MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url("https://api.xposedornot.com/v1/check-email/$email")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                // VULN-D1 fix: Redact email from debug logs to prevent PII leaking into logcat
                Log.d(TAG, "Response code: ${response.code}")

                when (response.code) {
                    404 -> {
                        // 404 means no breaches found for this email on xposedornot
                        return@withContext DarkWebScanResult(isSafe = true, scannedEmail = email)
                    }
                    429 -> {
                        // Rate Limited
                        if (currentRetry == MAX_RETRIES) {
                            return@withContext DarkWebScanResult(isSafe = false, isError = true, errorMessage = "Too many requests. Please try again later.", scannedEmail = "")
                        }
                        val retryAfter = response.header("Retry-After")?.toLongOrNull()
                        val delayTime = (retryAfter?.times(1000)) ?: backoffMs
                        Log.w(TAG, "Rate limited (429). Retrying after $delayTime ms.")
                        delay(delayTime)
                        backoffMs *= 2
                        currentRetry++
                        continue
                    }
                    in 500..599 -> {
                        // Server error
                        if (currentRetry == MAX_RETRIES) {
                            return@withContext DarkWebScanResult(isSafe = false, isError = true, errorMessage = "Server is currently unavailable. Please try again later.", scannedEmail = "")
                        }
                        delay(backoffMs)
                        backoffMs *= 2
                        currentRetry++
                        continue
                    }
                    in 200..299 -> {
                        return@withContext parseSuccessResponse(responseBody, email)
                    }
                    else -> {
                        Log.e(TAG, "Dark Web scan failed - HTTP ${response.code}")
                        return@withContext DarkWebScanResult(isSafe = false, isError = true, errorMessage = "Scan Failed (HTTP ${response.code})", scannedEmail = "")
                    }
                }
            } catch (e: Exception) {
                // Network Exceptions (Timeout, UnknownHostException)
                Log.e(TAG, "Dark Web scan exception: ${e.javaClass.simpleName}")
                if (currentRetry == MAX_RETRIES) {
                    return@withContext DarkWebScanResult(isSafe = false, isError = true, errorMessage = "Network Error. Please check your connection.", scannedEmail = "")
                }
                delay(backoffMs)
                backoffMs *= 2
                currentRetry++
            }
        }
        
        return@withContext DarkWebScanResult(isSafe = false, isError = true, errorMessage = "Network Error. Please check your connection.", scannedEmail = "")
    }

    private fun parseSuccessResponse(responseBody: String, email: String): DarkWebScanResult {
        try {
            val responseJson = JSONObject(responseBody)

            if (responseJson.optBoolean("safe", false) || responseJson.optString("message").contains("safe", ignoreCase = true)) {
                return DarkWebScanResult(isSafe = true, scannedEmail = email)
            }

            val breachesArray = responseJson.optJSONArray("breaches")
            if (breachesArray != null && breachesArray.length() > 0) {
                val richBreaches = mutableListOf<BreachDetail>()
                val detailsList = mutableListOf<String>()

                val firstItem = breachesArray.opt(0)
                if (firstItem is org.json.JSONArray) {
                    val innerArray = firstItem
                    for (i in 0 until innerArray.length()) {
                        val name = innerArray.optString(i, "Unknown Breach")
                        detailsList.add(name)
                        richBreaches.add(BreachDetail(
                            name = name,
                            description = "Your data was found in the $name breach database.",
                            isSensitive = false
                        ))
                    }
                } else {
                    for (i in 0 until breachesArray.length()) {
                        val breach = breachesArray.optJSONObject(i) ?: continue
                        val name = breach.optString("Name", breach.optString("title", "Unknown Breach"))
                        val domain = breach.optString("Domain", "")
                        val breachDate = breach.optString("BreachDate", "")
                        val pwnCount = breach.optLong("PwnCount", 0L)
                        val isSensitive = breach.optBoolean("IsSensitive", false)
                        val description = breach.optString("Description", "")
                            .replace(Regex("<[^>]*>"), "")
                            .take(200)

                        val dataClassesArr = breach.optJSONArray("DataClasses")
                        val dataClasses = mutableListOf<String>()
                        if (dataClassesArr != null) {
                            for (j in 0 until dataClassesArr.length()) {
                                dataClasses.add(dataClassesArr.optString(j))
                            }
                        }

                        detailsList.add(name)
                        richBreaches.add(BreachDetail(
                            name = name,
                            domain = domain,
                            breachDate = breachDate,
                            dataClasses = dataClasses,
                            description = description,
                            pwnCount = pwnCount,
                            isSensitive = isSensitive
                        ))
                    }
                }

                return DarkWebScanResult(
                    isSafe = false,
                    breachCount = richBreaches.size,
                    details = detailsList,
                    breaches = richBreaches,
                    scannedEmail = email
                )
            }

            return DarkWebScanResult(isSafe = true, scannedEmail = email)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse API response: ${e.javaClass.simpleName}")
            return DarkWebScanResult(isSafe = false, isError = true, errorMessage = "Error parsing scan data.", scannedEmail = "")
        }
    }
}
