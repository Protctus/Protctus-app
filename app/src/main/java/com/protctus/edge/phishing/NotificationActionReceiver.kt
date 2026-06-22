package com.protctus.edge.phishing

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_SAFE = "com.protctus.app.ACTION_MARK_SAFE"
        const val ACTION_KEEP_BLOCKED = "com.protctus.app.ACTION_KEEP_BLOCKED"
        const val EXTRA_DOMAIN = "extra_domain"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val TAG = "NotificationAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val domain = intent.getStringExtra(EXTRA_DOMAIN)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (domain == null || notificationId == -1) {
            Log.e(TAG, "Invalid intent extras received.")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            ACTION_MARK_SAFE -> {
                Log.d(TAG, "User marked $domain as SAFE. Adding to whitelist.")
                val whitelistManager = UserWhitelistManager(context)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        whitelistManager.addDomain(domain)
                        Log.d(TAG, "$domain added to whitelist (decided). No future notifications.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add $domain to whitelist", e)
                    } finally {
                        notificationManager.cancel(notificationId)
                        pendingResult.finish()
                    }
                }
            }
            ACTION_KEEP_BLOCKED -> {
                Log.d(TAG, "User chose to BLOCK $domain permanently.")
                val whitelistManager = UserWhitelistManager(context)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        whitelistManager.blockDomain(domain)
                        Log.d(TAG, "$domain added to blocklist (decided). No future notifications.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add $domain to blocklist", e)
                    } finally {
                        notificationManager.cancel(notificationId)
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
