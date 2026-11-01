package com.protctus.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

object QuestsApiClient {
    private const val TAG = "QuestsApiClient"
    
    // Remote Production Endpoint
    private const val BASE_URL = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev"

    private val client = SecureHttpClient.fast

    // Returns the raw list of dynamic quests filtered by type
    suspend fun fetchQuests(type: String? = null): JSONArray? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$BASE_URL/quests")
            if (type != null) {
                urlBuilder.append("?type=$type")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                // If you secure the worker later, add API headers here
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext null
                return@withContext JSONArray(bodyStr)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fetch Quests API Failed: ${e.message}")
            null
        }
    }

    // Returns the list of static defined ranks/rewards
    suspend fun fetchRewards(): JSONArray? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$BASE_URL/rewards")
            
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext null
                return@withContext JSONArray(bodyStr)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fetch Rewards API Failed: ${e.message}")
            null
        }
    }
}
