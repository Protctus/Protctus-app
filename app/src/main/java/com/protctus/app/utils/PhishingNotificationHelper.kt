package com.protctus.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.protctus.app.R
import com.protctus.app.receiver.VpnActionReceiver

/**
 * Helper to show actionable notifications when the Phishing Shield blocks a domain.
 */
object PhishingNotificationHelper {

    private const val CHANNEL_ID = "phishing_alerts"
    private const val CHANNEL_NAME = "Phishing Alerts"

    fun showBlockedNotification(context: Context, domain: String, threatScore: Float) {
        // SMART THRESHOLD: Only notify for high-confidence phishing attempts (> 0.85)
        // Silent block for background trackers/ads to avoid alert fatigue.
        if (threatScore < 0.85f) return

        createNotificationChannel(context)

        val notificationId = domain.hashCode()

        // Action 1: Mark as Safe
        val markSafeIntent = Intent(context, VpnActionReceiver::class.java).apply {
            action = VpnActionReceiver.ACTION_MARK_SAFE
            putExtra(VpnActionReceiver.EXTRA_DOMAIN, domain)
            putExtra(VpnActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markSafePendingIntent = PendingIntent.getBroadcast(
            context, notificationId, markSafeIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Keep Blocked
        val keepBlockedIntent = Intent(context, VpnActionReceiver::class.java).apply {
            action = VpnActionReceiver.ACTION_KEEP_BLOCKED
            putExtra(VpnActionReceiver.EXTRA_DOMAIN, domain)
            putExtra(VpnActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val keepBlockedPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 1, keepBlockedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_protctus_logo) // Use actual shield icon if available
            .setContentTitle("Threat Blocked: $domain")
            .setContentText("Protctus intercepted a high-risk phishing attempt.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Domain: $domain\nThreat Level: ${(threatScore * 100).toInt()}%\n" +
                "The connection was severed to protect your data."
            ))
            .addAction(android.R.drawable.ic_menu_add, "MARK AS SAFE", markSafePendingIntent)
            .addAction(android.R.drawable.ic_delete, "KEEP BLOCKED", keepBlockedPendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for phishing and malicious site blocks"
                enableLights(true)
                lightColor = android.graphics.Color.RED
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
