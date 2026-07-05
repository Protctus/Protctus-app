package com.protctus.app.services

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
import com.protctus.app.R
import com.protctus.app.engine.EngineDecision
import com.protctus.app.engine.EngineVerdict
import com.protctus.app.ui.screens.scanner.ThreatBlockedActivity

/**
 * Centralized threat notification manager for the Sentinel subsystem.
 *
 * Responsibilities:
 * - Throttling: max 1 notification per 30 seconds
 * - Batching: multiple threats within the window → single summary
 * - Duplicate suppression: same file path won't re-notify within 1 hour
 * - Priority routing: MALWARE/HIGH_RISK = immediate, MEDIUM = batch, LOW = history only
 * - Permission-safe: handles POST_NOTIFICATIONS denial on Android 13+
 */
class SentinelNotificationManager(private val context: Context) {

    companion object {
        const val THREAT_CHANNEL_ID = "SENTINEL_THREAT_CHANNEL"
        private const val THREAT_NOTIFICATION_ID = 3001
        private const val THROTTLE_INTERVAL_MS = 30_000L
        private const val DEDUP_WINDOW_MS = 60 * 60 * 1000L // 1 hour
        private const val TAG = "SentinelNotify"
    }

    private var lastNotificationTime = 0L
    private val pendingThreats = mutableListOf<ThreatInfo>()
    private val recentlyNotifiedPaths = LinkedHashMap<String, Long>(32, 0.75f, true)

    data class ThreatInfo(
        val filePath: String,
        val fileName: String,
        val verdict: EngineVerdict,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Creates the threat notification channel. Must be called once during service creation.
     */
    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                THREAT_CHANNEL_ID,
                "Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when suspicious or malicious files are detected in real-time."
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Report a threat detection. The notification manager decides whether to
     * notify immediately, batch, or suppress based on priority and throttle state.
     */
    @Synchronized
    fun reportThreat(filePath: String, fileName: String, verdict: EngineVerdict) {
        // Duplicate suppression: don't re-notify for the same file within 1 hour
        val now = System.currentTimeMillis()
        val lastNotified = recentlyNotifiedPaths[filePath]
        if (lastNotified != null && (now - lastNotified) < DEDUP_WINDOW_MS) {
            return
        }

        val threat = ThreatInfo(filePath, fileName, verdict, now)

        when (verdict.decision) {
            EngineDecision.MALWARE,
            EngineDecision.HIGH_RISK -> {
                // Critical: notify immediately (bypass throttle)
                recentlyNotifiedPaths[filePath] = now
                showImmediateNotification(threat)
                // Also launch ThreatBlockedActivity for critical threats
                launchThreatBlockedActivity(threat)
            }

            EngineDecision.SUSPICIOUS -> {
                // Medium: batch with throttle
                pendingThreats.add(threat)
                recentlyNotifiedPaths[filePath] = now
                if (now - lastNotificationTime >= THROTTLE_INTERVAL_MS) {
                    flushPendingNotifications()
                }
            }

            EngineDecision.INCONCLUSIVE,
            EngineDecision.LOW_RISK,
            EngineDecision.CLEAN -> {
                // Low/Clean: no notification, stored in cache history only
            }
        }

        // Prune old entries from dedup map
        if (recentlyNotifiedPaths.size > 100) {
            val cutoff = now - DEDUP_WINDOW_MS
            recentlyNotifiedPaths.entries.removeAll { it.value < cutoff }
        }
    }

    /**
     * Flushes any pending batched notifications. Called by SentinelService
     * periodically or when the throttle window elapses.
     */
    @Synchronized
    fun flushPendingNotifications() {
        if (pendingThreats.isEmpty()) return

        val now = System.currentTimeMillis()
        lastNotificationTime = now

        if (pendingThreats.size == 1) {
            showImmediateNotification(pendingThreats.first())
        } else {
            showBatchNotification(pendingThreats.toList())
        }
        pendingThreats.clear()
    }

    private fun showImmediateNotification(threat: ThreatInfo) {
        if (!hasNotificationPermission()) return

        lastNotificationTime = System.currentTimeMillis()

        val intent = Intent(context, ThreatBlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PACKAGE_NAME", "File: ${threat.fileName}")
            putExtra("APP_NAME", threat.verdict.decision.displayName)
            putExtra("RISK_SCORE", threat.verdict.finalScore.toInt())
            putStringArrayListExtra("ANOMALIES", arrayListOf(threat.verdict.explanation))
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            threat.filePath.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, THREAT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("${threat.verdict.decision.displayName}: ${threat.fileName}")
            .setContentText(threat.verdict.explanation.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(threat.verdict.explanation))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                threat.filePath.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied", e)
        }
    }

    private fun showBatchNotification(threats: List<ThreatInfo>) {
        if (!hasNotificationPermission()) return
        if (threats.isEmpty()) return

        val mostSevere = threats.maxByOrNull { it.verdict.finalScore } ?: return

        val intent = Intent(context, ThreatBlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("PACKAGE_NAME", "File: ${mostSevere.fileName}")
            putExtra("APP_NAME", mostSevere.verdict.decision.displayName)
            putExtra("RISK_SCORE", mostSevere.verdict.finalScore.toInt())
            putStringArrayListExtra("ANOMALIES", arrayListOf(mostSevere.verdict.explanation))
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            THREAT_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "${threats.size} threats detected"
        val body = threats.joinToString("\n") { "• ${it.fileName}: ${it.verdict.decision.displayName}" }

        val notification = NotificationCompat.Builder(context, THREAT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText("Tap to review the most severe threat.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(THREAT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission denied", e)
        }
    }

    private fun launchThreatBlockedActivity(threat: ThreatInfo) {
        try {
            val intent = Intent(context, ThreatBlockedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("PACKAGE_NAME", "File: ${threat.fileName}")
                putExtra("APP_NAME", threat.verdict.decision.displayName)
                putExtra("RISK_SCORE", threat.verdict.finalScore.toInt())
                putStringArrayListExtra("ANOMALIES", arrayListOf(threat.verdict.explanation))
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch ThreatBlockedActivity", e)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
