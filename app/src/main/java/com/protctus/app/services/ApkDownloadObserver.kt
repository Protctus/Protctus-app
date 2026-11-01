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
import com.protctus.app.engine.FileFeatureExtractor
import com.protctus.app.engine.ProcMalwareEngine
import com.protctus.app.ui.screens.features.ApkQuarantineActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Observes the Downloads directory for newly written APK files.
 * Routes all APKs through ProcMalwareEngine for verdict.
 * Only quarantines APKs that receive an actionable verdict (not all APKs).
 */
@Suppress("DEPRECATION")
class ApkDownloadObserver(private val context: Context) : 
    FileObserver(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath, FileObserver.CLOSE_WRITE) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return
        
        if (path.endsWith(".apk")) {
            val originalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), path)
            if (originalFile.exists()) {
                // Enqueue scanning on background thread — never block the observer callback
                scope.launch {
                    scanAndQuarantineIfMalicious(originalFile, path)
                }
            }
        }
    }

    /**
     * Scans the APK through the shared FileFeatureExtractor → ProcMalwareEngine pipeline.
     * Only quarantines if the engine returns an actionable verdict.
     */
    private suspend fun scanAndQuarantineIfMalicious(originalFile: File, fileName: String) {
        try {
            val evidence = FileFeatureExtractor.extractEvidence(context, originalFile)
            if (evidence == null) {
                Log.w("Protctus", "APK could not be analyzed: $fileName")
                return
            }

            val verdict = ProcMalwareEngine.evaluateFile(evidence)

            if (verdict.decision.isActionable) {
                // Actionable verdict — quarantine the APK
                Log.w("Protctus", "Malicious APK detected (${verdict.decision.displayName}): $fileName")
                val quarantinedFile = File(originalFile.parent, "$fileName.quarantine")
                val success = originalFile.renameTo(quarantinedFile)

                if (success) {
                    showQuarantineNotification(
                        quarantinedPath = quarantinedFile.absolutePath,
                        originalName = fileName,
                        verdictName = verdict.decision.displayName,
                        explanation = verdict.explanation
                    )
                }
            } else {
                // Clean or low-risk — allow the APK without disruption
                Log.d("Protctus", "APK scanned clean: $fileName (${verdict.decision.displayName})")
            }
        } catch (e: Exception) {
            Log.e("Protctus", "APK scan failed for $fileName", e)
        }
    }

    private fun showQuarantineNotification(quarantinedPath: String, originalName: String, verdictName: String, explanation: String) {
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
            .setContentTitle("$verdictName: $originalName")
            .setContentText(explanation.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(explanation))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(requestCode, notification)
    }
}
