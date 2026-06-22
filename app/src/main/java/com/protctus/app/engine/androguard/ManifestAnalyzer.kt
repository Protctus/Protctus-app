package com.protctus.app.engine.androguard

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * AndroidManifest.xml analysis engine.
 *
 * Implements the manifest analysis concepts from Androguard's core/apk/__init__.py:
 *   - Permission classification (normal, dangerous, signature, special)
 *   - Exported component detection (activities, services, receivers, providers)
 *   - Intent filter analysis for suspicious patterns
 *   - Security configuration assessment
 *
 * Uses Android PackageManager APIs instead of binary XML parsing,
 * making it compatible with on-device execution without Python.
 *
 * Source reference: androguard/core/apk/__init__.py (APK class)
 */
class ManifestAnalyzer(private val context: Context) {

    /**
     * Analyze the manifest of an installed package.
     */
    fun analyze(pm: PackageManager, packageInfo: PackageInfo): ManifestFindings {
        val findings = mutableListOf<AnalysisFinding>()

        // ── Permission Analysis ──
        val allPerms = packageInfo.requestedPermissions?.toList() ?: emptyList()
        val dangerousPerms = allPerms.filter { DANGEROUS_PERMISSIONS.containsKey(it) }

        if (dangerousPerms.size >= 5) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.MEDIUM,
                "Excessive Dangerous Permissions",
                "App requests ${dangerousPerms.size} dangerous permissions.",
                dangerousPerms.joinToString(", ") { it.substringAfterLast('.') }
            ))
        }

        // Check for specific dangerous permission combinations
        checkPermissionCombinations(allPerms, findings)

        // ── Exported Component Analysis ──
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS

        val detailedInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageInfo.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(packageInfo.packageName, flags)
            }
        } catch (e: Exception) { packageInfo }

        val exportedActivities = analyzeExportedActivities(detailedInfo, findings)
        val exportedServices = analyzeExportedServices(detailedInfo, findings)
        val exportedReceivers = analyzeExportedReceivers(detailedInfo, findings)
        val exportedProviders = analyzeExportedProviders(detailedInfo, findings)

        // ── Intent Filter Analysis ──
        val suspiciousFilters = analyzeSuspiciousIntents(detailedInfo, findings)

        // ── Security Configuration Flags ──
        val appInfo = packageInfo.applicationInfo
        val isDebuggable = appInfo?.let {
            (it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } ?: false
        val allowsBackup = appInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // FLAG_ALLOW_BACKUP was deprecated; check flags
                (it.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
            } else {
                (it.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
            }
        } ?: false

        if (isDebuggable) {
            findings.add(AnalysisFinding(
                FindingCategory.COMPONENT, Severity.HIGH,
                "Debuggable Application",
                "App has android:debuggable=true. This allows attackers to attach debuggers and inspect runtime data.",
                "ApplicationInfo.FLAG_DEBUGGABLE is set"
            ))
        }

        // ── Feature Flags ──
        val hasAccessibility = allPerms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        val hasDeviceAdmin = allPerms.contains("android.permission.BIND_DEVICE_ADMIN")
        val hasOverlay = allPerms.contains("android.permission.SYSTEM_ALERT_WINDOW")
        val hasBoot = allPerms.contains("android.permission.RECEIVE_BOOT_COMPLETED")

        if (hasAccessibility) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.HIGH,
                "Accessibility Service",
                "App can observe and control all user interactions via AccessibilityService.",
                "BIND_ACCESSIBILITY_SERVICE declared"
            ))
        }
        if (hasDeviceAdmin) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.HIGH,
                "Device Administrator",
                "App can enforce device policies and resist uninstallation.",
                "BIND_DEVICE_ADMIN declared"
            ))
        }
        if (hasOverlay && hasAccessibility) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.CRITICAL,
                "Overlay + Accessibility Attack Surface",
                "App has both overlay (SYSTEM_ALERT_WINDOW) and accessibility privileges. " +
                "This combination enables tapjacking and credential theft.",
                "Classic banking trojan permission pattern"
            ))
        }

        val hasHighPriority = detailedInfo.receivers?.any { receiver ->
            // High-priority receivers can intercept broadcasts before other apps
            receiver.name.contains("High", ignoreCase = true) ||
            receiver.name.contains("Priority", ignoreCase = true)
        } ?: false

        return ManifestFindings(
            dangerousPermissions = dangerousPerms,
            allPermissions = allPerms,
            exportedActivities = exportedActivities,
            exportedServices = exportedServices,
            exportedReceivers = exportedReceivers,
            exportedProviders = exportedProviders,
            suspiciousIntentFilters = suspiciousFilters,
            hasAccessibilityService = hasAccessibility,
            hasDeviceAdmin = hasDeviceAdmin,
            hasOverlayPermission = hasOverlay,
            hasBootReceiver = hasBoot,
            hasHighPriorityReceiver = hasHighPriority,
            hasBackupEnabled = allowsBackup,
            isDebuggable = isDebuggable,
            allowsBackup = allowsBackup,
            findings = findings
        )
    }

    private fun checkPermissionCombinations(perms: List<String>, findings: MutableList<AnalysisFinding>) {
        val permSet = perms.toSet()

        // SMS + Internet → data exfiltration
        if ((permSet.contains("android.permission.READ_SMS") ||
             permSet.contains("android.permission.RECEIVE_SMS")) &&
            permSet.contains("android.permission.INTERNET")) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.HIGH,
                "SMS + Internet Access",
                "App can read SMS messages and send data to the internet. " +
                "Common pattern in SMS-stealing malware.",
                "READ_SMS/RECEIVE_SMS + INTERNET"
            ))
        }

        // Location + Internet → location tracking
        if ((permSet.contains("android.permission.ACCESS_FINE_LOCATION") ||
             permSet.contains("android.permission.ACCESS_COARSE_LOCATION")) &&
            permSet.contains("android.permission.INTERNET") &&
            permSet.contains("android.permission.ACCESS_BACKGROUND_LOCATION")) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.HIGH,
                "Background Location Tracking",
                "App can track location in background and transmit data.",
                "ACCESS_BACKGROUND_LOCATION + INTERNET"
            ))
        }

        // Camera + Audio + Internet → surveillance
        if (permSet.contains("android.permission.CAMERA") &&
            permSet.contains("android.permission.RECORD_AUDIO") &&
            permSet.contains("android.permission.INTERNET")) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.HIGH,
                "Surveillance Capability",
                "App can capture video, record audio, and transmit data.",
                "CAMERA + RECORD_AUDIO + INTERNET"
            ))
        }

        // Install packages → dropper
        if (permSet.contains("android.permission.REQUEST_INSTALL_PACKAGES") &&
            permSet.contains("android.permission.INTERNET")) {
            findings.add(AnalysisFinding(
                FindingCategory.PERMISSION, Severity.HIGH,
                "App Installer Capability",
                "App can download and install other applications. Potential malware dropper.",
                "REQUEST_INSTALL_PACKAGES + INTERNET"
            ))
        }
    }

    private fun analyzeExportedActivities(info: PackageInfo, findings: MutableList<AnalysisFinding>): List<ExportedComponent> {
        val exported = mutableListOf<ExportedComponent>()
        info.activities?.forEach { activity ->
            if (activity.exported) {
                val risk = if (activity.permission.isNullOrEmpty()) RiskLevel.MEDIUM else RiskLevel.LOW
                exported.add(ExportedComponent(
                    name = activity.name,
                    type = ComponentType.ACTIVITY,
                    intentFilters = emptyList(),
                    permissionRequired = activity.permission,
                    riskLevel = risk
                ))
            }
        }
        if (exported.size > 5) {
            findings.add(AnalysisFinding(
                FindingCategory.COMPONENT, Severity.MEDIUM,
                "Many Exported Activities (${exported.size})",
                "Large attack surface — ${exported.size} activities accessible by other apps.",
                exported.take(5).joinToString { it.name.substringAfterLast('.') }
            ))
        }
        val unprotected = exported.count { it.permissionRequired.isNullOrEmpty() }
        if (unprotected > 3) {
            findings.add(AnalysisFinding(
                FindingCategory.COMPONENT, Severity.HIGH,
                "Unprotected Exported Activities ($unprotected)",
                "$unprotected activities are exported without permission protection.",
                "Any app can invoke these activities"
            ))
        }
        return exported
    }

    private fun analyzeExportedServices(info: PackageInfo, findings: MutableList<AnalysisFinding>): List<ExportedComponent> {
        val exported = mutableListOf<ExportedComponent>()
        info.services?.forEach { service ->
            if (service.exported) {
                val risk = if (service.permission.isNullOrEmpty()) RiskLevel.HIGH else RiskLevel.MEDIUM
                exported.add(ExportedComponent(
                    name = service.name,
                    type = ComponentType.SERVICE,
                    intentFilters = emptyList(),
                    permissionRequired = service.permission,
                    riskLevel = risk
                ))
            }
        }
        val unprotected = exported.count { it.permissionRequired.isNullOrEmpty() }
        if (unprotected > 0) {
            findings.add(AnalysisFinding(
                FindingCategory.COMPONENT, Severity.HIGH,
                "Unprotected Exported Services ($unprotected)",
                "Services accessible without permission. Other apps can start/bind to them.",
                exported.filter { it.permissionRequired.isNullOrEmpty() }
                    .joinToString { it.name.substringAfterLast('.') }
            ))
        }
        return exported
    }

    private fun analyzeExportedReceivers(info: PackageInfo, findings: MutableList<AnalysisFinding>): List<ExportedComponent> {
        val exported = mutableListOf<ExportedComponent>()
        info.receivers?.forEach { receiver ->
            if (receiver.exported) {
                exported.add(ExportedComponent(
                    name = receiver.name,
                    type = ComponentType.RECEIVER,
                    intentFilters = emptyList(),
                    permissionRequired = receiver.permission,
                    riskLevel = if (receiver.permission.isNullOrEmpty()) RiskLevel.MEDIUM else RiskLevel.LOW
                ))
            }
        }
        return exported
    }

    private fun analyzeExportedProviders(info: PackageInfo, findings: MutableList<AnalysisFinding>): List<ExportedComponent> {
        val exported = mutableListOf<ExportedComponent>()
        info.providers?.forEach { provider ->
            if (provider.exported) {
                val risk = if (provider.readPermission.isNullOrEmpty() &&
                    provider.writePermission.isNullOrEmpty()) RiskLevel.CRITICAL else RiskLevel.MEDIUM
                exported.add(ExportedComponent(
                    name = provider.name,
                    type = ComponentType.PROVIDER,
                    intentFilters = emptyList(),
                    permissionRequired = provider.readPermission ?: provider.writePermission,
                    riskLevel = risk
                ))
                if (risk == RiskLevel.CRITICAL) {
                    findings.add(AnalysisFinding(
                        FindingCategory.COMPONENT, Severity.CRITICAL,
                        "Unprotected Content Provider",
                        "Content provider '${provider.name.substringAfterLast('.')}' is exported without read/write permissions. " +
                        "Any app can access its data.",
                        provider.name
                    ))
                }
            }
        }
        return exported
    }

    private fun analyzeSuspiciousIntents(info: PackageInfo, findings: MutableList<AnalysisFinding>): List<SuspiciousIntentFilter> {
        val suspicious = mutableListOf<SuspiciousIntentFilter>()

        // Check receivers for suspicious broadcast actions
        info.receivers?.forEach { receiver ->
            val name = receiver.name
            SUSPICIOUS_BROADCAST_ACTIONS.forEach { (action, reason) ->
                if (name.lowercase().contains(action.substringAfterLast('.').lowercase())) {
                    suspicious.add(SuspiciousIntentFilter(name, action, reason))
                }
            }
        }

        if (suspicious.isNotEmpty()) {
            findings.add(AnalysisFinding(
                FindingCategory.INTENT_FILTER, Severity.MEDIUM,
                "Suspicious Broadcast Receivers (${suspicious.size})",
                "App listens for ${suspicious.size} sensitive system broadcast(s).",
                suspicious.joinToString { it.action.substringAfterLast('.') }
            ))
        }

        return suspicious
    }

    companion object {
        /** Android dangerous permissions with their risk descriptions */
        val DANGEROUS_PERMISSIONS = mapOf(
            "android.permission.READ_SMS" to "Read SMS messages",
            "android.permission.RECEIVE_SMS" to "Intercept incoming SMS",
            "android.permission.SEND_SMS" to "Send SMS messages",
            "android.permission.READ_CONTACTS" to "Read contacts",
            "android.permission.WRITE_CONTACTS" to "Modify contacts",
            "android.permission.READ_CALL_LOG" to "Read call history",
            "android.permission.WRITE_CALL_LOG" to "Modify call history",
            "android.permission.CAMERA" to "Access camera",
            "android.permission.RECORD_AUDIO" to "Record audio",
            "android.permission.ACCESS_FINE_LOCATION" to "Precise GPS location",
            "android.permission.ACCESS_COARSE_LOCATION" to "Approximate location",
            "android.permission.ACCESS_BACKGROUND_LOCATION" to "Background location",
            "android.permission.READ_EXTERNAL_STORAGE" to "Read storage",
            "android.permission.WRITE_EXTERNAL_STORAGE" to "Write storage",
            "android.permission.READ_PHONE_STATE" to "Read phone state (IMEI, etc.)",
            "android.permission.CALL_PHONE" to "Make phone calls",
            "android.permission.READ_CALENDAR" to "Read calendar",
            "android.permission.WRITE_CALENDAR" to "Modify calendar",
            "android.permission.BODY_SENSORS" to "Body sensor data"
        )

        /** Suspicious broadcast actions that receivers commonly abuse */
        val SUSPICIOUS_BROADCAST_ACTIONS = mapOf(
            "android.intent.action.BOOT_COMPLETED" to "Starts on device boot — persistence mechanism",
            "android.intent.action.PACKAGE_REPLACED" to "Activates when app is updated",
            "android.intent.action.MY_PACKAGE_REPLACED" to "Self-update activation",
            "android.intent.action.NEW_OUTGOING_CALL" to "Intercepts outgoing calls",
            "android.provider.Telephony.SMS_RECEIVED" to "Intercepts incoming SMS",
            "android.intent.action.SCREEN_ON" to "Triggers on screen activation",
            "android.net.conn.CONNECTIVITY_CHANGE" to "Network state monitoring"
        )
    }
}
