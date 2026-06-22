package com.protctus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.ThreatAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhishingAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.protctus.app.ACTION_PHISHING_ALERT") {
            val domain = intent.getStringExtra("domain") ?: return
            Log.d("PhishingAlertReceiver", "Received phishing alert for domain: $domain")
            
            val threatDao = AppDatabase.getInstance(context.applicationContext).threatDao()
            
            val alert = ThreatAlert(
                title = "Phishing Domain Blocked",
                description = "Blocked access to known phishing domain: $domain",
                priority = "High",
                threatType = "Phishing",
                priorityColorHex = "#EF4444",
                timestamp = System.currentTimeMillis()
            )
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    threatDao.insertThreat(alert)
                    Log.d("PhishingAlertReceiver", "Successfully saved phishing alert to ThreatDao")
                } catch(e: Exception) {
                    Log.e("PhishingAlertReceiver", "Failed to save phishing alert to ThreatDao", e)
                }
            }
            
            // Show System Notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "phishing_alerts",
                    "Phishing Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for blocked phishing domains"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notificationId = domain.hashCode()

            val markSafeIntent = Intent(context, com.protctus.edge.phishing.NotificationActionReceiver::class.java).apply {
                this.action = com.protctus.edge.phishing.NotificationActionReceiver.ACTION_MARK_SAFE
                putExtra(com.protctus.edge.phishing.NotificationActionReceiver.EXTRA_DOMAIN, domain)
                putExtra(com.protctus.edge.phishing.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val markSafePendingIntent = android.app.PendingIntent.getBroadcast(
                context, notificationId, markSafeIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val keepBlockedIntent = Intent(context, com.protctus.edge.phishing.NotificationActionReceiver::class.java).apply {
                this.action = com.protctus.edge.phishing.NotificationActionReceiver.ACTION_KEEP_BLOCKED
                putExtra(com.protctus.edge.phishing.NotificationActionReceiver.EXTRA_DOMAIN, domain)
                putExtra(com.protctus.edge.phishing.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val keepBlockedPendingIntent = android.app.PendingIntent.getBroadcast(
                context, notificationId + 1, keepBlockedIntent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, "phishing_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Phishing Domain Blocked")
                .setContentText("Protctus Edge shielded you from: $domain")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(0, "Mark as Safe", markSafePendingIntent)
                .addAction(0, "Keep Blocked", keepBlockedPendingIntent)

            notificationManager.notify(notificationId, builder.build())
        }
    }
}
