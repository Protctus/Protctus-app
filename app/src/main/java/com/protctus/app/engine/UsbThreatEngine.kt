package com.protctus.app.engine

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UsbStatus {
    SAFE, WARNING, CRITICAL
}

data class UsbThreatState(val riskScore: Int, val status: UsbStatus)

object UsbThreatEngine {
    private val _threatState = MutableStateFlow(UsbThreatState(0, UsbStatus.SAFE))
    val threatState: StateFlow<UsbThreatState> = _threatState.asStateFlow()

    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val CHANNEL_ID = "usb_critical_alerts"

    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        // Use ContextCompat to register the receiver correctly for Android 14
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        createNotificationChannel(context)
        isInitialized = true
    }

    fun deinitialize(context: Context) {
        if (!isInitialized) return
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Ignored if not registered
        }
        isInitialized = false
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_STATE || intent.action == Intent.ACTION_POWER_CONNECTED) {
                analyzeUsbThreat(context, intent)
            }
        }
    }

    private fun analyzeUsbThreat(context: Context, intent: Intent) {
        var riskScore = 0

        val connected = intent.getBooleanExtra("connected", false)
        val hostConnected = intent.getBooleanExtra("host_connected", false)
        val unlocked = intent.getBooleanExtra("unlocked", false)

        val adbEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) == 1

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isDeviceLocked

        // --- Threat Matrix ---
        if (connected && unlocked) riskScore += 50
        if (hostConnected) riskScore += 30
        if (adbEnabled) riskScore += 20
        if (hostConnected && isLocked) riskScore += 10

        val status = when {
            riskScore >= 80 -> UsbStatus.CRITICAL
            riskScore >= 50 -> UsbStatus.WARNING
            else -> UsbStatus.SAFE
        }

        _threatState.value = UsbThreatState(riskScore, status)

        if (status == UsbStatus.CRITICAL) {
            triggerCriticalAlert(context)
        }
    }

    private fun triggerCriticalAlert(context: Context) {
        // 1. Play SOS Vibration
        val sosPattern = longArrayOf(0, 200, 200, 200, 200, 200, 500, 600, 200, 600, 200, 600, 500, 200, 200, 200, 200, 200)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(sosPattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createWaveform(sosPattern, -1))
        }

        // 2. Notification with Intent to app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("CRITICAL USB BREACH")
            .setContentText("Hostile connection detected! Unplug now.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(999, notification)

        // 3. Log to Threat Feed Database
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.protctus.app.data.local.AppDatabase.getInstance(context).threatDao().insertThreat(
                    com.protctus.app.data.local.ThreatAlert(
                        title = "USB Juice Jacking Attempt",
                        description = "A hostile physical connection with data transfer capabilities was detected and blocked while device was locked.",
                        priority = "Critical",
                        threatType = "Hardware Vector",
                        priorityColorHex = "#EF4444", // Red
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (ex: Exception) { ex.printStackTrace() }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USB Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for malicious USB connections"
                setBypassDnd(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
