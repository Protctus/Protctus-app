package com.protctus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.protctus.app.data.local.SettingsManager
import com.protctus.app.services.SentinelService
import com.protctus.edge.phishing.PhishingVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            Log.d("BootCompletedReceiver", "Starting Background Protections after boot/update")
            
            val settingsManager = SettingsManager(context)
            CoroutineScope(Dispatchers.IO).launch {
                val isRealTimeProtectionEnabled = settingsManager.isRealTimeProtectionEnabled.first()
                if (isRealTimeProtectionEnabled) {
                    val sentinelIntent = Intent(context, SentinelService::class.java)
                    try {
                        ContextCompat.startForegroundService(context, sentinelIntent)
                    } catch (e: Exception) {
                        Log.e("BootCompletedReceiver", "Failed to start SentinelService", e)
                    }
                }
                
                val isPhishingShieldEnabled = settingsManager.isPhishingShieldEnabled.first()
                if (isPhishingShieldEnabled) {
                    val vpnIntent = Intent(context, PhishingVpnService::class.java)
                    try {
                        ContextCompat.startForegroundService(context, vpnIntent)
                    } catch (e: Exception) {
                        Log.e("BootCompletedReceiver", "Failed to start PhishingVpnService", e)
                    }
                }
            }
        }
    }
}
