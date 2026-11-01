package com.protctus.app.worker

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.ThreatAlert
import com.protctus.app.utils.SecurityScanner
import com.protctus.app.utils.SecurityFinding

/**
 * Runs a lightweight security posture audit every 24 hours.
 * If the device score drops below 70, logs findings to ThreatFeed
 * and fires a single notification prompting the user to review.
 */
class PeriodicScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "protctus_periodic_posture_scan"
        private const val TAG = "PeriodicScanWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val scanResult = SecurityScanner.performScan(context)

            if (scanResult.score < 70 && scanResult.findings.isNotEmpty()) {
                // Log the worst finding to ThreatFeed
                val worstFinding = scanResult.findings.first()
                val db = AppDatabase.getInstance(context).threatDao()

                // Deduplication: only log if not already logged in last 24 hours
                val threatType = "PostureAudit:${worstFinding.title}"
                val existingCount = db.countByThreatType(threatType)
                if (existingCount == 0) {
                    db.insertThreat(
                        ThreatAlert(
                            title = "Security Posture: ${worstFinding.title}",
                            description = "${worstFinding.description} Fix: ${worstFinding.fix}",
                            priority = when (worstFinding.severity) {
                                SecurityFinding.Severity.CRITICAL -> "Critical"
                                SecurityFinding.Severity.HIGH -> "High"
                                SecurityFinding.Severity.MEDIUM -> "Medium"
                                else -> "Low"
                            },
                            threatType = threatType,
                            priorityColorHex = when (worstFinding.severity) {
                                SecurityFinding.Severity.CRITICAL -> "#EF4444"
                                SecurityFinding.Severity.HIGH -> "#F59E0B"
                                else -> "#3B82F6"
                            },
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }

                // Fire one notification
                try {
                    val notification = NotificationCompat.Builder(context, DailyNotificationWorker.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Security Score: ${scanResult.score}/100")
                        .setContentText("${scanResult.findings.size} issue(s) need your attention.")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()

                    NotificationManagerCompat.from(context).notify(2001, notification)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Notification permission denied", e)
                }
            }

            Log.d(TAG, "Periodic posture audit complete. Score: ${scanResult.score}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic scan failed", e)
            Result.retry()
        }
    }
}
