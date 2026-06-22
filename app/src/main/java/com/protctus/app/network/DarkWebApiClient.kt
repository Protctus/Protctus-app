package com.protctus.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.protctus.app.BuildConfig

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
    val breachCount: Int = 0,
    val details: List<String> = emptyList(),
    val breaches: List<BreachDetail> = emptyList(),
    val scannedEmail: String = "",
    val scanTimestamp: Long = System.currentTimeMillis()
)

object DarkWebApiClient {
    private const val TAG = "DarkWebApiClient"
    private val client = SecureHttpClient.default

    suspend fun scanEmail(email: String): DarkWebScanResult = withContext(Dispatchers.IO) {
        try {
            // VULN-D1 fix: URL-encode the email to prevent path injection from malformed emails
            val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")

            val request = Request.Builder()
                .url("https://api.xposedornot.com/v1/check-email/$encodedEmail")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            // VULN-D1 fix: Redact email from debug logs to prevent PII leaking into logcat
            Log.d(TAG, "Response code: ${response.code}, body length: ${responseBody.length}")

            if (response.code == 404) {
                return@withContext DarkWebScanResult(isSafe = true, scannedEmail = email)
            } else if (response.isSuccessful) {
                val responseJson = JSONObject(responseBody)

                if (responseJson.optBoolean("safe", false) || responseJson.optString("message").contains("safe", ignoreCase = true)) {
                    return@withContext DarkWebScanResult(isSafe = true, scannedEmail = email)
                }

                // xposedornot returns: {"breaches": [["Name1", "Name2", ...]], "email": "..."}
                val breachesArray = responseJson.optJSONArray("breaches")
                if (breachesArray != null && breachesArray.length() > 0) {
                    val richBreaches = mutableListOf<BreachDetail>()
                    val detailsList = mutableListOf<String>()

                    // Handle both nested array [[...]] and object array [{...}]
                    val firstItem = breachesArray.opt(0)
                    if (firstItem is org.json.JSONArray) {
                        // Flat string array inside outer array: [["Name1", "Name2"]]
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
                        // HIBP-style object array: [{"Name": "...", "Domain": "..."}]
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

                    return@withContext DarkWebScanResult(
                        isSafe = false,
                        breachCount = richBreaches.size,
                        details = detailsList,
                        breaches = richBreaches,
                        scannedEmail = email
                    )
                }

                return@withContext DarkWebScanResult(isSafe = true, scannedEmail = email)
            } else {
                Log.e(TAG, "Dark Web scan failed - HTTP ${response.code}: $responseBody")
                return@withContext DarkWebScanResult(isSafe = false, details = listOf("Scan Failed (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Dark Web scan exception: ${e.message}", e)
            return@withContext DarkWebScanResult(isSafe = false, details = listOf("Network error"))
        }
    }
}
