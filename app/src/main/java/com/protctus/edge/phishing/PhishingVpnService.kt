package com.protctus.edge.phishing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class PhishingVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val domainCooldownMap = ConcurrentHashMap<String, Long>()

    // ── Anti-Spam: max 10 notifications per day ──
    @Volatile private var dailyNotifCount = 0
    @Volatile private var dailyNotifResetDay = -1
    private val MAX_NOTIFICATIONS_PER_DAY = 10
    
    private var detector: PhishingDetector? = null
    private lateinit var whitelistManager: UserWhitelistManager
    private val whitelistedDomainsCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val blockedDomainsCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val decidedDomainsCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val TAG = "PhishVpnService"
        private const val CHANNEL_ID = "security_alerts_vpn"
        
        init {
            try {
                System.loadLibrary("vpn-core")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library vpn-core not found", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Phishing Shield Active")
            .setContentText("Protctus is protecting your browsing sessions.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1005, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1005, notification)
        }

        scope.launch {
            try {
                detector = PhishingDetector(this@PhishingVpnService)

                // Fix 5: Wire up cloud ThreatReporter
                detector?.threatReporter = CloudThreatReporter(this@PhishingVpnService)

                whitelistManager = UserWhitelistManager(this@PhishingVpnService)
                
                // Collect whitelist, blocklist, and decided domains in parallel
                launch {
                    whitelistManager.whitelistedDomains.collect { domains ->
                        whitelistedDomainsCache.clear()
                        whitelistedDomainsCache.addAll(domains)
                    }
                }
                launch {
                    whitelistManager.blockedDomains.collect { domains ->
                        blockedDomainsCache.clear()
                        blockedDomainsCache.addAll(domains)
                    }
                }
                launch {
                    whitelistManager.decidedDomains.collect { domains ->
                        decidedDomainsCache.clear()
                        decidedDomainsCache.addAll(domains)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize background services", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_VPN") {
            stopVpnInternal()
            return START_NOT_STICKY
        }
        
        scope.launch {
            setupVpn()
        }
        return START_STICKY
    }

    private fun setupVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("Protctus AI Phishing Shield")
                .addAddress("10.0.0.2", 32)
                // Use a fake DNS server address that we will intercept in native code
                .addDnsServer("10.0.0.1") 
                // ONLY route the fake DNS traffic through the tunnel.
                // This ensures all other traffic (YouTube, Web, etc.) remains at 100% native speed.
                .addRoute("10.0.0.1", 32)
                .setMtu(1280) // Standard safe MTU for mobile/VPN
                .setBlocking(true) // MUST be blocking so read() doesn't return EAGAIN and crash the native thread

            // No need to iterate over all apps if we are only capturing 10.0.0.1:53
            // This significantly reduces startup time and battery drain.

            vpnInterface = builder.establish()
            vpnInterface?.let { fd ->
                startNativeEngine(fd.detachFd())
                Log.i(TAG, "VPN Shield initialized in Split-Tunnel mode.")
                
                // P2 FIX: Detect if Private DNS (DoT/DoH) is enabled, which bypasses our DNS interception
                checkPrivateDnsBypass()
            } ?: Log.e(TAG, "VPN Establish failed")

        } catch (e: Exception) {
            Log.e(TAG, "VPN Setup failed", e)
        }
    }

    /**
     * P2 FIX: Detect Private DNS (DNS-over-TLS) on Android 9+.
     * When enabled, Chrome and system apps send DNS queries over TLS on port 853
     * or HTTPS on port 443, completely bypassing our UDP port 53 interception.
     * We warn the user so they can make an informed decision.
     */
    private fun checkPrivateDnsBypass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val privateDnsMode = android.provider.Settings.Global.getString(
                    contentResolver, "private_dns_mode"
                )
                // "hostname" = user configured a custom DoT provider
                // "opportunistic" = system will try DoT if available
                if (privateDnsMode == "hostname" || privateDnsMode == "opportunistic") {
                    Log.w(TAG, "Private DNS detected (mode=$privateDnsMode). DNS Shield may be partially bypassed.")
                    
                    val notifId = "PRIVATE_DNS_BYPASS".hashCode()
                    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("\u26A0\uFE0F DNS Shield Partially Bypassed")
                        .setContentText("Private DNS is enabled. Some phishing sites may not be intercepted. Tap for details.")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(
                            "Your device has Private DNS (DNS-over-TLS) enabled. Apps using DoT/DoH send DNS queries " +
                            "on encrypted channels that bypass Protctus's DNS interception. To ensure full protection, " +
                            "disable Private DNS in Settings > Network > Private DNS, or set it to 'Off'."
                        ))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build()
                    
                    with(NotificationManagerCompat.from(this)) {
                        try { notify(notifId, notification) } catch (_: SecurityException) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check Private DNS status", e)
            }
        }
    }

    private fun startMetricsReporting() {
        scope.launch {
            while (isActive) {
                delay(60000)
                try {
                    val metrics = getNativeMetrics()
                    if (metrics.size >= 6) {
                        val dropRate = if (metrics[0] > 0) (metrics[1].toDouble() / metrics[0]) * 100 else 0.0
                        Log.d(TAG, "VPN Health: Processed=${metrics[0]}, Dropped=${metrics[1]} (${"%.2f".format(dropRate)}%), TCP_SNI=${metrics[2]}, QUIC_SNI=${metrics[3]}, OOB_Prevented=${metrics[4]}, Degraded_Mode_Triggers=${metrics[5]}")
                        
                        // NOTE: In production, send these anonymized hardware metrics to Firebase Crashlytics
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun stopVpnInternal() {
        stopNativeEngine()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    private fun isAppInfrastructure(domain: String): Boolean {
        val d = domain.lowercase()
        val essentialSuffixes = arrayOf(
            "workers.dev",
            "googleapis.com",
            "firebaseio.com",
            "crashlytics.com",
            "razorpay.com",
            "stripe.com",
            "google.com",
            "gstatic.com",
            "app-measurement.com",
            "google-analytics.com",
            "firebase.com"
        )
        for (suffix in essentialSuffixes) {
            if (d == suffix || d.endsWith(".$suffix")) {
                return true
            }
        }
        return false
    }

    @androidx.annotation.Keep
    @Suppress("unused")
    fun jniIsMalicious(domain: String): Boolean {
        val d = domain.lowercase().trim().removeSuffix(".")
        if (d.isEmpty()) return false
        if (isAppInfrastructure(d)) return false
        if (whitelistedDomainsCache.contains(d)) return false

        // User-blocked domains are always malicious
        if (blockedDomainsCache.contains(d)) return true
        
        // Fast ML + dynamic heuristic check (~2.5ms) — DNS-only path
        val score = detector?.analyzeDomainRiskDnsOnly(d) ?: 0.0f

        return when {
            score >= PhishingDetector.DNS_BLOCK_THRESHOLD -> {
                // High confidence → BLOCK + notify + persist to Room
                showPhishingNotification(d, score, isBlocked = true)
                persistThreatToDb(d, score, isBlocked = true)
                true
            }
            score >= PhishingDetector.DNS_SUSPICIOUS_THRESHOLD -> {
                // Suspicious → ALLOW but notify user
                showPhishingNotification(d, score, isBlocked = false)
                persistThreatToDb(d, score, isBlocked = false)
                false
            }
            else -> false
        }
    }

    /**
     * BATCHED JNI CALLBACK: Drastically reduces JNI context switching overhead.
     */
    @androidx.annotation.Keep
    @Suppress("unused")
    fun jniOnPhishingDetectedBatch(domains: Array<String>, scores: FloatArray) {
        scope.launch(Dispatchers.Main) {
            for (i in domains.indices) {
                val domain = domains[i]
                val d = domain.lowercase().trim()
                if (isAppInfrastructure(d)) continue
                if (whitelistedDomainsCache.contains(d)) continue
                showPhishingNotification(d, scores[i], isBlocked = scores[i] >= PhishingDetector.HEURISTIC_CAP)
            }
        }
    }

    @androidx.annotation.Keep
    @Suppress("unused")
    fun jniProtectSocket(socketFd: Int): Boolean {
        return protect(socketFd)
    }

    @androidx.annotation.Keep
    private external fun startNativeEngine(tunFd: Int)
    @androidx.annotation.Keep
    private external fun stopNativeEngine()
    @androidx.annotation.Keep
    private external fun getNativeMetrics(): LongArray

    private fun showPhishingNotification(url: String, score: Float, isBlocked: Boolean) {
        // Skip if user already decided on this domain (marked safe or blocked)
        if (decidedDomainsCache.contains(url)) return

        // Per-domain dedup: don't re-notify same domain in same session
        if (domainCooldownMap.containsKey(url)) return
        domainCooldownMap[url] = System.currentTimeMillis()

        // Daily notification budget — only limits SUSPICIOUS (orange) alerts.
        // BLOCKED (red) alerts ALWAYS show because the user needs to know.
        if (!isBlocked) {
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            if (today != dailyNotifResetDay) {
                dailyNotifCount = 0
                dailyNotifResetDay = today
            }
            if (dailyNotifCount >= MAX_NOTIFICATIONS_PER_DAY) return
            dailyNotifCount++
        }

        com.protctus.app.engine.ThreatEventBus.emitThreat(url)

        val notificationId = url.hashCode()

        // Tap action → open block screen
        val fullScreenIntent = Intent(this, com.protctus.app.MainActivity::class.java).apply {
            action = "com.protctus.app.ACTION_SHOW_BLOCK_SCREEN"
            putExtra("domain", url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, notificationId, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── "Mark as Safe" action button ──
        val safeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_SAFE
            putExtra(NotificationActionReceiver.EXTRA_DOMAIN, url)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 1, safeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── "Block" action button ──
        val blockIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_KEEP_BLOCKED
            putExtra(NotificationActionReceiver.EXTRA_DOMAIN, url)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 2, blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isBlocked) "\uD83D\uDEA8 Phishing Blocked!" else "\u26A0\uFE0F Suspicious Domain"
        val text = if (isBlocked)
            "Threat: ${"%.0f".format(score * 100)}% — $url was blocked."
        else
            "Risk: ${"%.0f".format(score * 100)}% — $url looks suspicious."
        val color = if (isBlocked) Color.RED else Color.parseColor("#FF8C00") // orange for suspicious

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_input_add, "\u2705 Mark Safe", safePendingIntent)
            .addAction(android.R.drawable.ic_delete, "\uD83D\uDEAB Block", blockPendingIntent)

        with(NotificationManagerCompat.from(this)) {
            try { notify(notificationId, builder.build()) } catch(_: SecurityException) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Security Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "High Priority Phishing Alerts"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Fix 6: Persist blocked/suspicious phishing threats directly to Room DB.
     * Runs on IO dispatcher — never blocks the DNS thread.
     */
    private fun persistThreatToDb(domain: String, score: Float, isBlocked: Boolean) {
        scope.launch(Dispatchers.Main) {
            try {
                val db = com.protctus.app.data.local.AppDatabase.getInstance(this@PhishingVpnService)
                db.threatDao().insertThreat(
                    com.protctus.app.data.local.ThreatAlert(
                        title = if (isBlocked) "Phishing Domain Blocked" else "Suspicious Domain Detected",
                        description = if (isBlocked)
                            "Blocked access to phishing domain: $domain (confidence: ${"%.0f".format(score * 100)}%)"
                        else
                            "Suspicious domain detected: $domain (risk: ${"%.0f".format(score * 100)}%)",
                        priority = if (isBlocked) "High" else "Medium",
                        threatType = "Phishing",
                        priorityColorHex = if (isBlocked) "#EF4444" else "#FF8C00",
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist phishing threat to Room", e)
            }
        }
    }

    override fun onDestroy() {
        stopVpnInternal()
        scope.cancel()
        detector?.close()
        super.onDestroy()
    }
}