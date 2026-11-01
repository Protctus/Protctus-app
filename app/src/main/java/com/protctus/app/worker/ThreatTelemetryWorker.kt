package com.protctus.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.protctus.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ThreatTelemetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ThreatTelemetryWorker"
        // Replace with the actual endpoint URL of your Cloudflare D1 Worker
        private const val CLOUDFLARE_API_URL = "https://protctus-telemetry.jatinsharmashivpuri02122008.workers.dev/sync"
        private const val BATCH_LIMIT = 50
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getInstance(applicationContext)
            val telemetryDao = database.threatTelemetryDao()

            val pendingRecords = telemetryDao.getPendingTelemetry(BATCH_LIMIT)

            if (pendingRecords.isEmpty()) {
                Log.d(TAG, "No pending telemetry to upload.")
                return@withContext Result.success()
            }

            Log.d(TAG, "Found ${pendingRecords.size} records. Preparing upload payload...")

            val jsonArray = JSONArray()
            pendingRecords.forEach { record ->
                val jsonObj = JSONObject().apply {
                    put("hash", record.domainHashSha256)
                    put("confidence", record.confidenceScore)
                    put("threatType", record.threatType)
                    put("timestamp", record.timestamp)
                    put("isFalsePositive", record.isFalsePositive)
                }
                jsonArray.put(jsonObj)
            }

            val payload = JSONObject().apply {
                put("telemetry", jsonArray)
            }.toString()

            val requestBody = payload.toRequestBody("application/json".toMediaType())
            
            // Note: Include X-API-Key or Bearer token for security
            val request = Request.Builder()
                .url(CLOUDFLARE_API_URL)
                .post(requestBody)
                .addHeader("X-API-Key", com.protctus.app.BuildConfig.PHISHING_API_KEY)
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d(TAG, "Telemetry uploaded successfully. Deleting local records.")
                val idsToDelete = pendingRecords.map { it.id }
                telemetryDao.deleteTelemetryByIds(idsToDelete)
                return@withContext Result.success()
            } else {
                Log.e(TAG, "Failed to upload telemetry. Server returned ${response.code}: ${response.message}")
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during telemetry upload", e)
            return@withContext Result.retry()
        }
    }
}
