package com.protctus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.protctus.app.managers.UserWhitelistManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles actionable notification clicks for the Phishing Shield.
 * This receiver allows users to whitelist domains directly from the notification tray.
 */
@AndroidEntryPoint
class VpnActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userWhitelistManager: UserWhitelistManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_MARK_SAFE -> {
                scope.launch {
                    userWhitelistManager.addDomain(domain)
                }
            }
            ACTION_KEEP_BLOCKED -> {
                // No-op, notification will be dismissed below
            }
        }

        // Always dismiss the notification after an action is taken
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_MARK_SAFE = "com.protctus.app.ACTION_MARK_DOMAIN_SAFE"
        const val ACTION_KEEP_BLOCKED = "com.protctus.app.ACTION_KEEP_BLOCKED"
        
        const val EXTRA_DOMAIN = "extra_domain"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
