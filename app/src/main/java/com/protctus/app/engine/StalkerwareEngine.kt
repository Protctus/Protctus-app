package com.protctus.app.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.protctus.app.data.local.SettingsManager
import kotlinx.coroutines.flow.first

data class StalkerwareThreat(
    val packageName: String,
    val appName: String,
    val score: Int,
    val reason: String
)

object StalkerwareEngine {
    private val SAFE_WHITELIST = setOf(
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.inputmethod.latin" // Gboard
    )

    suspend fun analyzePackages(context: Context, settingsManager: SettingsManager): List<StalkerwareThreat> {
        val pm = context.packageManager
        val installedPackages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
        val userWhitelist = settingsManager.stalkerwareWhitelist.first()
        val detectedThreats = mutableListOf<StalkerwareThreat>()

        for (pkg in installedPackages) {
            val packageName = pkg.packageName

            val flags = pkg.applicationInfo?.flags ?: 0
            val isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystemApp) continue
            if (SAFE_WHITELIST.contains(packageName) || userWhitelist.contains(packageName)) continue

            var score = 0
            val reasons = mutableListOf<String>()

            // Rule 1: Sideloading
            val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try { pm.getInstallSourceInfo(packageName).installingPackageName } catch (e: Exception) { null }
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
            if (installer != "com.android.vending") {
                score += 40
                reasons.add("Sideloaded")
            }

            // Rule 2: Accessibility & Device Admin
            val requestedPermissions = pkg.requestedPermissions ?: emptyArray()
            if (requestedPermissions.contains(android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE) || 
                requestedPermissions.contains(android.Manifest.permission.BIND_DEVICE_ADMIN)) {
                score += 30
                reasons.add("Device Admin/Accessibility Requested")
            }

            // Rule 3: Disguised Icon (No active Launcher)
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                score += 40
                reasons.add("Hidden App Icon")
            }

            // Rule 4: Toxic Permissions
            val toxicPerms = listOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
            val foundToxic = toxicPerms.filter { requestedPermissions.contains(it) }
            if (foundToxic.isNotEmpty()) {
                score += (foundToxic.size * 10)
                reasons.add("Accesses: ${foundToxic.joinToString { it.substringAfterLast(".") }}")
            }

            if (score > 70) {
                val appName = pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
                detectedThreats.add(StalkerwareThreat(packageName, appName, score, reasons.joinToString(" | ")))
            }
        }
        return detectedThreats
    }

    suspend fun executeBatchScan(context: Context) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val settingsManager = SettingsManager(context)
            val threats = analyzePackages(context, settingsManager)
            
            if (threats.isNotEmpty()) {
                val db = com.protctus.app.data.local.AppDatabase.getInstance(context).threatDao()
                
                // Check if we already logged these (deduplication by threatType which contains package name)
                for (threat in threats) {
                    val threatType = "Stalkerware:${threat.packageName}"
                    val existingCount = db.countByThreatType(threatType)
                    if (existingCount == 0) {
                        db.insertThreat(
                            com.protctus.app.data.local.ThreatAlert(
                                title = "Stalkerware Detected: ${threat.appName}",
                                description = threat.reason,
                                priority = "Critical",
                                threatType = threatType,
                                priorityColorHex = "#EF4444",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // Fire 1 grouped notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel("STALKERWARE_ALERTS", "Stalkerware Alerts", android.app.NotificationManager.IMPORTANCE_HIGH)
                    notificationManager.createNotificationChannel(channel)
                }

                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    putExtra("NAVIGATE_TO_THREAT_FEED", true)
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context, 0, launchIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                val notification = androidx.core.app.NotificationCompat.Builder(context, "STALKERWARE_ALERTS")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Protctus Engine")
                    .setContentText("Found ${threats.size} suspicious apps requiring your review.")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(1002, notification)
            }
        }
    }
}
