package com.protctus.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.protctus.app.R
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.ThreatAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

/**
 * Periodic worker that fires engagement notifications and polls the
 * admin-scheduled notification endpoint on D1.
 *
 * Runs every ~8 hours via WorkManager. Each notification is also written
 * to the local Room DB so it appears in the Notifications tab.
 */
class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "protctus_engagement"
        const val CHANNEL_NAME = "Protctus Alerts"
        private const val TAG = "DailyNotifWorker"
        // Base URL of the quests API worker
        private const val API_BASE = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev"
    }

    // Engagement notification pool removed by request

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            ensureNotificationChannel()

            // Get user email from Firebase Auth
            val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""

            // Engagement notifications are now handled in real-time via FCM (ProtctusMessagingService)
            // Polling from Cloudflare removed as per request.

            // Support ticket updates and global alerts are now handled via real-time FCM
            // Polling from Cloudflare/D1 removed to save battery and network.

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)
            Result.retry()
        }
    }

    // Polling methods removed - migrated to FCM
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily security alerts and quest reminders from Protctus"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
