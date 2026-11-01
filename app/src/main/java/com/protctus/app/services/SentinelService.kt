package com.protctus.app.services

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.protctus.app.R
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.ui.screens.scanner.ThreatBlockedActivity
import com.protctus.app.engine.EliteThreat
import com.protctus.app.viewmodel.scanSinglePackage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SentinelService : Service() {

    companion object {
        private const val TAG = "SentinelService"
        private const val CACHE_TTL_DAYS = 7L
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var fileObserver: RealTimeFileObserver? = null
    private var notificationManager: SentinelNotificationManager? = null
    
    // ─── Package Install Receiver (preserved) ────────────────────────
    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_PACKAGE_ADDED) {
                val data = intent.data
                val packageName = data?.schemeSpecificPart ?: return
                
                // Exclude app updates, only scan fresh installs for highest accuracy
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return
                }

                scope.launch {
                    val settingsManager = com.protctus.app.data.local.SettingsManager(context)
                    val isGhostAppDetectorEnabled = settingsManager.isGhostAppDetectorEnabled.first()
                    if (isGhostAppDetectorEnabled) {
                        com.protctus.app.engine.StalkerwareEngine.executeBatchScan(context)
                    }
                    val isHeuristicsEnabled = settingsManager.isHeuristicsEnabled.first()
                    if (isHeuristicsEnabled) {
                        val threat = scanSinglePackage(context, packageName)
                        // If the heuristic engine flags a threat
                        if (threat != null && threat.riskScore > 0) { 
                            val blockIntent = Intent(context, ThreatBlockedActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("PACKAGE_NAME", threat.packageName)
                                putExtra("APP_NAME", threat.appName)
                                putExtra("RISK_SCORE", threat.riskScore)
                                putStringArrayListExtra("ANOMALIES", ArrayList(threat.detectedAnomalies))
                            }
                            context.startActivity(blockIntent)
                        }
                    }
                }
            }
        }
    }

    // ─── DownloadManager Completion Receiver ─────────────────────────
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L) return

            scope.launch {
                resolveAndScanDownload(downloadId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize notification manager for threats
        notificationManager = SentinelNotificationManager(this)
        notificationManager?.createChannel()
        
        // Register package install receiver (preserved)
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(installReceiver, filter)
        }

        // Register DownloadManager completion receiver
        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, downloadFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, downloadFilter)
        }
        
        // Initialize real-time file observer with notification manager
        fileObserver = RealTimeFileObserver(this, scope, notificationManager!!)
        fileObserver?.register()

        // Cleanup stale cache entries on startup
        scope.launch {
            cleanupStaleCache()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(installReceiver)
        try { unregisterReceiver(downloadCompleteReceiver) } catch (e: Exception) {}
        fileObserver?.unregister()
        // Flush any pending batched notifications before shutdown
        notificationManager?.flushPendingNotifications()
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── DownloadManager resolution ──────────────────────────────────

    /**
     * Resolves a completed download to its local file path and enqueues it
     * for immediate real-time scanning at HIGHEST priority.
     */
    private fun resolveAndScanDownload(downloadId: Long) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)

            dm.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIdx >= 0 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIdx >= 0) {
                            val localUri = cursor.getString(uriIdx) ?: return
                            val filePath = if (localUri.startsWith("file://")) {
                                localUri.removePrefix("file://")
                            } else {
                                // Try to resolve content URI
                                try {
                                    val uri = android.net.Uri.parse(localUri)
                                    var resolved: String? = null
                                    contentResolver.query(uri, arrayOf(android.provider.MediaStore.Files.FileColumns.DATA), null, null, null)?.use { c ->
                                        if (c.moveToFirst()) {
                                            val dataIdx = c.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
                                            if (dataIdx >= 0) resolved = c.getString(dataIdx)
                                        }
                                    }
                                    resolved ?: return
                                } catch (e: Exception) {
                                    Log.w(TAG, "Cannot resolve download URI: $localUri", e)
                                    return
                                }
                            }

                            Log.d(TAG, "Download complete, scanning: $filePath")
                            fileObserver?.enqueueDirectScan(filePath)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve download $downloadId", e)
        }
    }

    // ─── Cache cleanup ───────────────────────────────────────────────

    private suspend fun cleanupStaleCache() {
        try {
            val cutoff = System.currentTimeMillis() - (CACHE_TTL_DAYS * 24 * 60 * 60 * 1000L)
            val cacheDao = AppDatabase.getInstance(this@SentinelService).scannedFileCacheDao()
            cacheDao.deleteOlderThan(cutoff)
            Log.d(TAG, "Stale cache entries cleaned (older than $CACHE_TTL_DAYS days)")
        } catch (e: Exception) {
            Log.w(TAG, "Cache cleanup failed", e)
        }
    }

    // ─── Notifications ───────────────────────────────────────────────

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "SENTINEL_CHANNEL")
            .setContentTitle("Protctus Sentinel Active")
            .setContentText("Real-Time Background Protection is guarding your device.")
            .setSmallIcon(R.mipmap.ic_launcher_round) 
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SENTINEL_CHANNEL",
                "Real-Time Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs background heuristics when new apps and files are added to block threats instantly."
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
