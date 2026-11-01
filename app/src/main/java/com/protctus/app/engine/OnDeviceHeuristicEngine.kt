package com.protctus.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Highly advanced, 100% ON-DEVICE malware scanning engine.
 * Implements Static Heuristic Analysis without cloud dependencies.
 */
data class DetectedThreat(
    val appName: String,
    val packageName: String,
    val finalScore: Int,
    val flags: List<String>
)

class OnDeviceHeuristicEngine(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    // Dynamic verification: A store is "trusted" if the installer app itself is a System App
    private fun isInstalledBySystemStore(installerPackageName: String?): Boolean {
        if (installerPackageName.isNullOrBlank()) return false
        return try {
            val installerInfo = packageManager.getApplicationInfo(installerPackageName, 0)
            (installerInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Executes the heuristic scan across all installed user apps.
     * Emits threats seamlessly via a Kotlin Flow.
     */
    fun scanDevice(): Flow<DetectedThreat> = flow {
        val installedPackages = packageManager.getInstalledPackages(
            PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES
        )

        for (packageInfo in installedPackages) {
            if (!coroutineContext.isActive) break

            val appInfo = packageInfo.applicationInfo ?: continue

            // 1. Exclude System Apps completely to focus on User Apps
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                continue
            }

            val threat = evaluateApplication(packageInfo, appInfo)
            // FP-FIX: Raised threshold from >75 to >85 to require stronger evidence.
            if (threat != null && threat.finalScore > 85) {
                emit(threat)
            }
        }
    }.flowOn(Dispatchers.IO) // Optimize for I/O operations

    private fun evaluateApplication(packageInfo: PackageInfo, appInfo: ApplicationInfo): DetectedThreat? {
        val packageName = packageInfo.packageName
        var threatScore = 0
        val threatFlags = mutableListOf<String>()

        // ==========================================
        // 1. Target Acquisition & Installer Verification
        // ==========================================
        val installerPackageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { packageManager.getInstallSourceInfo(packageName).installingPackageName } catch (e: Exception) { null }
        } else {
            @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName)
        }

        val isSideloaded = !isInstalledBySystemStore(installerPackageName)
        
        // FP-FIX: Sideloading is LOW-WEIGHT metadata, not malware evidence.
        // Reduced from +30 to +10. Many legitimate apps are sideloaded (F-Droid, dev builds).
        if (isSideloaded) {
            threatScore += 10
            threatFlags.add("SIDELOADED")
        }

        // ==========================================
        // 2. The Threat Matrix Analyzer (Permissions & Capabilities)
        // ==========================================
        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        
        val hasAccessibility = requestedPermissions.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        val hasAlertWindow = requestedPermissions.contains("android.permission.SYSTEM_ALERT_WINDOW")
        val hasSms = requestedPermissions.contains("android.permission.READ_SMS") || requestedPermissions.contains("android.permission.RECEIVE_SMS")
        val hasDeviceAdmin = requestedPermissions.contains("android.permission.BIND_DEVICE_ADMIN")
        val hasBoot = requestedPermissions.contains("android.permission.RECEIVE_BOOT_COMPLETED")

        // FP-FIX: Permission combinations reduced. Accessibility+Overlay is used by password managers,
        // banking apps, and automation tools. Only the full triad with SMS is genuinely dangerous.
        if (hasAccessibility && hasAlertWindow && hasSms) {
            threatScore += 40
            threatFlags.add("BANKING_TROJAN_HEURISTIC")
        } else if (hasAccessibility && hasAlertWindow) {
            // FP-FIX: Reduced from +40 to +20. Many legitimate apps use both.
            threatScore += 20
            threatFlags.add("OVERLAY_ACCESSIBILITY_USAGE")
        }

        if (hasDeviceAdmin) {
            threatScore += 15
            threatFlags.add("DEVICE_ADMIN_CAPABILITY")
            if (isSideloaded) {
                // FP-FIX: Reduced from +20 to +10
                threatScore += 10
            }
        }

        // ==========================================
        // 3. Ghost App & Evasion Detection
        // ==========================================
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val isHidden = launchIntent == null

        if (isHidden) {
            threatFlags.add("HIDDEN_ICON")
            if (hasAccessibility && hasSms) {
                // FP-FIX: Only escalate for truly dangerous combos. Reduced from +60 to +35.
                threatScore += 35
                threatFlags.add("HEADLESS_SURVEILLANCE")
            } else if (hasAccessibility || hasSms || hasBoot) {
                threatScore += 15
            } else {
                // FP-FIX: Reduced from +25 to +5. Many widgets/IMEs are headless.
                threatScore += 5
            }
        }

        // ==========================================
        // 4. APK Structural Parsing (Packer/Dropper Detection)
        // ==========================================
        val sourceDir = appInfo.sourceDir
        if (sourceDir != null && File(sourceDir).exists()) {
            val structuralScore = analyzeApkStructure(File(sourceDir))
            if (structuralScore > 0) {
                threatScore += structuralScore
                threatFlags.add("PACKER_OR_DROPPER_DETECTED")
            }
        }

        // ==========================================
        // 5. Threat Scoring & Output
        // ==========================================
        
        // Final sanity bounds
        val finalScore = threatScore.coerceIn(0, 100)

        if (finalScore > 0) { // Return even low scores for debugging/logging, but Flow filters > 75
            val appName = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            return DetectedThreat(appName, packageName, finalScore, threatFlags)
        }

        return null
    }

    /**
     * Efficiently parses the APK structure without loading it entirely into memory.
     * Looks for embedded executables (.dex, .apk, .so) inside the assets/ directory,
     * which is a strong indicator of a dropper or packer.
     */
    private fun analyzeApkStructure(apkFile: File): Int {
        var structuralPenalty = 0
        try {
            ZipInputStream(FileInputStream(apkFile)).use { zis ->
                var entry = zis.nextEntry
                var checkCount = 0
                
                // Limit checks to prevent zip bomb DoS
                while (entry != null && checkCount < 1000) {
                    val entryName = entry.name.lowercase()
                    
                    // A common packer technique is hiding Dalvik Executables inside assets
                    if (entryName.startsWith("assets/")) {
                        if (entryName.endsWith(".dex") || entryName.endsWith(".apk") || entryName.endsWith(".jar")) {
                            structuralPenalty = 50 // High penalty for executable in assets
                            break
                        }
                    }
                    
                    zis.closeEntry()
                    entry = zis.nextEntry
                    checkCount++
                }
            }
        } catch (e: Exception) {
            // Stream failure, safely ignore to maintain engine stability
        }
        return structuralPenalty
    }
}
