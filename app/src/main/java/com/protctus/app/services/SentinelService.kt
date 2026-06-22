package com.protctus.app.services

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
import androidx.core.app.NotificationCompat
import com.protctus.app.R
import com.protctus.app.ui.screens.scanner.ThreatBlockedActivity
import com.protctus.app.engine.EliteThreat
import com.protctus.app.viewmodel.scanSinglePackage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SentinelService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(installReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(installReceiver)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
                description = "Runs background heuristics when new apps are installed to block zero-days instantly."
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
