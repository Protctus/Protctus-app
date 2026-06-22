package com.protctus.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.protctus.app.MainActivity
import com.protctus.app.R
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.ThreatAlert

class ProtctusMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New FCM Token: $token")
        
        // Sync to Firestore if user is authenticated
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val db = FirebaseFirestore.getInstance()
            val deviceData = hashMapOf(
                "fcmToken" to token,
                "lastSeenToken" to System.currentTimeMillis()
            )
            // Save to existing device telemetry doc or user root
            db.collection("devices")
                .document(user.uid)
                .set(deviceData, SetOptions.merge())
                .addOnFailureListener { e -> Log.e("FCM", "Failed to sync token", e) }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Protctus Security"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        val imageUrl = remoteMessage.notification?.imageUrl?.toString() ?: remoteMessage.data["imageUrl"]
        val type = remoteMessage.data["type"] ?: "Global Alert"
        val targetEmail = remoteMessage.data["target_email"]
        val data = remoteMessage.data

        // If a target_email is specified, only show if it matches the current user
        if (!targetEmail.isNullOrBlank()) {
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
            if (currentUserEmail != targetEmail) {
                Log.d("FCM", "Skipping notification for different user: $targetEmail")
                return
            }
        }

        // Persist to local DB for History tab
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.threatDao().insertThreat(
                    ThreatAlert(
                        title = title,
                        description = body,
                        priority = "Medium",
                        threatType = type,
                        priorityColorHex = "#3B82F6",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e("FCM", "Failed to persist notification", e)
            }
        }

        sendNotification(title, body, imageUrl, data)
    }

    private fun sendNotification(title: String, messageBody: String, imageUrl: String?, data: Map<String, String>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Forward all data extras
            data.forEach { (key, value) -> putExtra(key, value) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, Random.nextInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "protctus_global_alerts"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback icon
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)

        // If high-fidelity image URL is provided, attach it as BigPictureStyle
        if (!imageUrl.isNullOrBlank()) {
            val bitmap = getBitmapFromUrl(imageUrl)
            if (bitmap != null) {
                notificationBuilder.setLargeIcon(bitmap)
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as Bitmap?)
                    )
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since Android Oreo, notification channel is required
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Protctus Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Security alerts, quests, and global updates from Protctus."
            channel.enableVibration(true)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }

    private fun getBitmapFromUrl(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e("FCM", "Failed to download image: $imageUrl", e)
            null
        }
    }
}
