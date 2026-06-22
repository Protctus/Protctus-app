package com.protctus.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.core.app.NotificationCompat
import com.protctus.app.R
import com.protctus.app.ui.screens.features.ApkQuarantineActivity
import java.io.File

@Suppress("DEPRECATION")
class ApkDownloadObserver(private val context: Context) : 
    FileObserver(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath, FileObserver.CLOSE_WRITE) {

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        
        if (path.endsWith(".apk")) {
            val originalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), path)
            if (originalFile.exists()) {
                Log.w("Protctus", "Suspicious APK detected: $path")
                val quarantinedFile = File(originalFile.parent, "$path.quarantine")
                val success = originalFile.renameTo(quarantinedFile)
                
                if (success) {
                    showQuarantineNotification(quarantinedFile.absolutePath, path)
                }
            }
        }
    }

    private fun showQuarantineNotification(quarantinedPath: String, originalName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channelId = "apk_quarantine_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "APK Security Blocks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for blocked dangerous app installations"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, ApkQuarantineActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FILE_PATH", quarantinedPath)
            putExtra("FILE_NAME", originalName)
        }
        
        // Use a unique request code for each pending intent so they don't overwrite
        val requestCode = originalName.hashCode()
        val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            // Fallback to launcher icon if specific blocker icon is missing
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle("Suspicious App Suspended")
            .setContentText("'$originalName' was paused. Tap to verify and install.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestCode, notification)
    }
}
