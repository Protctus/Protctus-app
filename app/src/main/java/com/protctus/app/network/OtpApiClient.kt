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

object OtpApiClient {
    private const val TAG = "OtpApiClient"
    private val client = SecureHttpClient.default
    private const val BASE_URL = "https://otp-auth-worker.jatinsharmashivpuri02122008.workers.dev"

    suspend fun sendOtpEmail(email: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "sendOtpEmail called for: $email")

        if (apiKey.isBlank() || apiKey == "your_api_key_here") {
            Log.e(TAG, "API key is blank or default placeholder")
            return@withContext false
        }

        try {
            val json = JSONObject()
            json.put("email", email)
            json.put("action", "send_otp")

            val cleanApiKey = apiKey.trim('"')
            Log.d(TAG, "Sending OTP request to $BASE_URL/send with key length: ${cleanApiKey.length}")

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/send")
                .post(requestBody)
                .addHeader("X-App-Api-Key", cleanApiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Response code: ${response.code}, body: $responseBody")

            if (!response.isSuccessful) {
                Log.e(TAG, "OTP send failed - HTTP ${response.code}: $responseBody")
            }

            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "OTP send exception: ${e.message}", e)
            false
        }
    }

    suspend fun verifyOtpEmail(email: String, otp: String, password: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "verifyOtpEmail called for: $email")

        if (apiKey.isBlank() || apiKey == "your_api_key_here") {
            Log.e(TAG, "API key is blank or default placeholder")
            return@withContext false
        }

        try {
            val json = JSONObject()
            json.put("email", email)
            json.put("otp", otp)
            json.put("password", password)

            val cleanApiKey = apiKey.trim('"')
            Log.d(TAG, "Sending OTP verify request to $BASE_URL/verify with key length: ${cleanApiKey.length}")

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/verify")
                .post(requestBody)
                .addHeader("X-App-Api-Key", cleanApiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Verify response code: ${response.code}, body: $responseBody")

            if (!response.isSuccessful) {
                Log.e(TAG, "OTP verify failed - HTTP ${response.code}: $responseBody")
                return@withContext false
            }

            try {
                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.has("success")) {
                    val isSuccess = jsonResponse.getBoolean("success")
                    if (!isSuccess && jsonResponse.has("error")) {
                        Log.e(TAG, "Worker reported explicitly: ${jsonResponse.getString("error")}")
                    }
                    return@withContext isSuccess
                }
            } catch (e: Exception) {
                Log.e(TAG, "OTP response parse error", e)
            }

            // SECURITY: Default to FAIL on ambiguous responses (never assume success)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "OTP verify exception: ${e.message}", e)
            false
        }
    }
    /**
     * Verify OTP for 2FA login flow. Calls the same /verify endpoint.
     * Since the user already exists, the worker's account-creation step will
     * hit a collision (which it handles gracefully), and we just check the
     * OTP validity from the `success` boolean.
     */
    suspend fun verify2faOtp(email: String, otp: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "verify2faOtp called for: $email")

        if (apiKey.isBlank() || apiKey == "your_api_key_here") {
            Log.e(TAG, "API key is blank or default placeholder")
            return@withContext false
        }

        try {
            val json = JSONObject()
            json.put("email", email)
            json.put("otp", otp)
            json.put("mode", "2fa") // Signal to worker this is a 2FA verify, not signup

            val cleanApiKey = apiKey.trim('"')
            Log.d(TAG, "Sending 2FA verify request to $BASE_URL/verify")

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/verify")
                .post(requestBody)
                .addHeader("X-App-Api-Key", cleanApiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "2FA verify response code: ${response.code}, body: $responseBody")

            if (!response.isSuccessful) {
                Log.e(TAG, "2FA verify failed - HTTP ${response.code}: $responseBody")
                return@withContext false
            }

            try {
                val jsonResponse = JSONObject(responseBody)
                if (jsonResponse.has("success")) {
                    return@withContext jsonResponse.getBoolean("success")
                }
            } catch (e: Exception) {
                Log.e(TAG, "2FA response parse error", e)
            }

            // SECURITY: Default to FAIL on ambiguous responses (never assume success)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "2FA verify exception: ${e.message}", e)
            false
        }
    }
}
