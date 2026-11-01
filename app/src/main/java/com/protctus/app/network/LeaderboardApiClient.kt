package com.protctus.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LeaderboardApiClient {
    private const val TAG = "LeaderboardApiClient"
    
    // Defaulting to the local dev URL; can be mapped to BuildConfig easily
    private const val BASE_URL = "https://protctus-leaderboard.jatinsharmashivpuri02122008.workers.dev"

    private val client = SecureHttpClient.fast

    suspend fun syncUserTelemetry(
        uid: String,
        username: String,
        securityScore: Int,
        rankTitle: String,
        level: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "protctus")
            val userRef = firestore.collection("users").document(uid)
            val updateData = mapOf(
                "username" to username,
                "security_score" to securityScore,
                "rank_title" to rankTitle,
                "level" to level,
                "last_scan_time" to System.currentTimeMillis()
            )
            userRef.set(updateData, com.google.firebase.firestore.SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sync Telemetry Failed: ${e.message}")
            false
        }
    }

    suspend fun fetchUserProgression(uid: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/user/$uid")
                .get()
                .addHeader("X-App-Api-Key", com.protctus.app.BuildConfig.OTP_API_KEY.trim('"'))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyStr)
                if (root.optBoolean("success")) {
                    return@withContext root.optJSONObject("user")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fetch Progression Failed: ${e.message}")
            null
        }
    }

    suspend fun fetchLeaderboard(): org.json.JSONArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/leaderboard")
                .get()
                .addHeader("X-App-Api-Key", com.protctus.app.BuildConfig.OTP_API_KEY.trim('"'))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyStr)
                if (root.optBoolean("success")) {
                    return@withContext root.optJSONArray("leaderboard")
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fetch Leaderboard Failed: ${e.message}")
            null
        }
    }

    suspend fun claimMission(uid: String, missionId: String, xpReward: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "protctus")
            val userRef = firestore.collection("users").document(uid)
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                if (snapshot.exists()) {
                    val currentXp = snapshot.getLong("total_xp") ?: snapshot.getLong("xp") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val claimedList = snapshot.get("claimed_missions") as? List<String> ?: emptyList()
                    
                    if (!claimedList.contains(missionId)) {
                        val newList = claimedList.toMutableList()
                        newList.add(missionId)
                        transaction.update(userRef, "total_xp", currentXp + xpReward)
                        transaction.update(userRef, "claimed_missions", newList)
                    }
                } else {
                    // Create basic user profile if it doesn't exist
                    val newData = hashMapOf(
                        "total_xp" to xpReward.toLong(),
                        "claimed_missions" to listOf(missionId)
                    )
                    transaction.set(userRef, newData, com.google.firebase.firestore.SetOptions.merge())
                }
                null
            }.await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Claim Mission Failed: ${e.message}")
            false
        }
    }
}
