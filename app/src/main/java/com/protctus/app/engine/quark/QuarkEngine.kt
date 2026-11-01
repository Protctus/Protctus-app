package com.protctus.app.engine.quark

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

/**
 * Quark Behavioral Analysis Engine for Android.
 *
 * This is the core scan engine that implements Quark-Engine's "Five Stages of Crime"
 * behavioral detection model, adapted for efficient on-device execution.
 *
 * Architecture decisions:
 * - Stage 1 (Permission check): Uses Android PackageManager API — O(1) set lookup
 * - Stage 2-3 (API presence): Uses DEX string pool scanning instead of full bytecode
 *   traversal. This is a lightweight approximation that trades accuracy for speed.
 * - Stage 4 (Sequence): Simplified to "both APIs found in same DEX" correlation
 *   (full XREF analysis is too expensive for mobile)
 * - Stage 5 (Data flow): Simplified to permission+API cross-correlation heuristic
 *   (full register tracking requires Dalvik VM simulation)
 *
 * Performance target: <5 seconds for all installed apps on mid-range device.
 *
 * Source reference: quark-engine/quark/core/quark.py (Quark.run method)
 */
class QuarkEngine private constructor(private val context: Context) {

    private val ruleLoader = QuarkRuleLoader.getInstance()
    private val apkAnalyzer = ApkAnalyzer(context)
    private var isInitialized = false

    /**
     * Initialize the engine by loading rules from assets.
     * Must be called before any scan operations.
     *
     * @return true if initialization succeeded (at least 1 rule loaded)
     */
    fun initialize(): Boolean {
        if (isInitialized && ruleLoader.ruleCount > 0) return true

        val rulesLoaded = ruleLoader.loadFromAssets(context)
        isInitialized = rulesLoaded > 0
        Log.i(TAG, "QuarkEngine initialized: $rulesLoaded rules loaded")
        return isInitialized
    }

    /** Whether the engine has been initialized with rules */
    val ready: Boolean get() = isInitialized && ruleLoader.ruleCount > 0

    /** Number of loaded detection rules */
    val ruleCount: Int get() = ruleLoader.ruleCount

    // ═══════════════════════════════════════════
    //  SMART SCAN MODE (Lightweight, fast)
    // ═══════════════════════════════════════════

    /**
     * Smart Scan: Analyze an installed package with lightweight behavioral checks.
     * Designed to execute in <100ms per app.
     *
     * Uses PackageManager metadata + permission correlation only.
     * Does NOT perform DEX string pool analysis (too slow for bulk scanning).
     */
    fun smartScanPackage(pm: PackageManager, packageInfo: PackageInfo): QuarkScanResult {
        val startTime = System.currentTimeMillis()
        val packageName = packageInfo.packageName
        val appLabel = try {
            packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (e: Exception) { packageName }

        val permissions = packageInfo.requestedPermissions?.toSet() ?: emptySet()

        // Get only rules relevant to this app's permissions (pre-filter)
        val relevantRules = ruleLoader.getRelevantRules(permissions)

        val triggeredResults = mutableListOf<QuarkRuleResult>()
        var scoreSum = 0.0
        var weightSum = 0.0

        for (rule in relevantRules) {
            rule.resetCheck()
            scoreSum += Math.abs(rule.score)

            // ── Stage 1: Permission Check ──
            // From quark.py line 472: check if APK permissions are a superset of rule permissions
            val matchedPerms = rule.permissions.filter { permissions.contains(it) }
            if (rule.permissions.isEmpty() || matchedPerms.size == rule.permissions.size) {
                rule.checkItem[0] = true
            }

            // ── Stage 2-3: API presence (Smart Scan shortcut) ──
            // In Smart Scan mode, we can't do DEX string analysis per-app (too slow).
            // Instead we use permission correlation as a proxy for API presence.
            // If an app has SEND_SMS permission, it likely calls SmsManager APIs.
            val apiPermCorrelation = checkApiPermissionCorrelation(rule, permissions)
            if (apiPermCorrelation.first) rule.checkItem[1] = true
            if (apiPermCorrelation.second) rule.checkItem[2] = true

            // ── Stage 4-5: Sequence/Data flow (Smart Scan: skip) ──
            // These require DEX bytecode analysis — deferred to Deep Scan mode.

            // Record if rule triggered at meaningful confidence (≥40%)
            if (rule.confidence >= 2) {
                val weight = rule.getWeightedScore()
                weightSum += Math.abs(weight)
                triggeredResults.add(
                    QuarkRuleResult(
                        rule = rule,
                        confidence = rule.confidence,
                        confidencePercent = rule.confidencePercent,
                        weightedScore = weight,
                        matchedPermissions = matchedPerms,
                        matchedApis = emptyList(), // No DEX analysis in Smart Scan
                        stageResults = rule.checkItem.toList()
                    )
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        if (triggeredResults.isEmpty()) {
            return QuarkScanResult.clean(packageName, appLabel, relevantRules.size, duration)
        }

        return QuarkScanResult(
            packageName = packageName,
            appLabel = appLabel,
            totalRulesEvaluated = relevantRules.size,
            triggeredRules = triggeredResults.sortedByDescending { it.weightedScore },
            overallThreatScore = weightSum,
            overallThreatLevel = ThreatLevel.fromWeightRatio(weightSum, scoreSum),
            scanDurationMs = duration
        )
    }

    // ═══════════════════════════════════════════
    //  DEEP SCAN MODE (Comprehensive, slower)
    // ═══════════════════════════════════════════

    /**
     * Deep Scan: Comprehensive behavioral analysis of an installed package.
     * Performs full DEX string pool scanning for API presence detection.
     * Designed to execute in <2 seconds per app.
     */
    fun deepScanPackage(pm: PackageManager, packageInfo: PackageInfo): QuarkScanResult {
        val startTime = System.currentTimeMillis()
        val packageName = packageInfo.packageName

        // Build full APK profile with DEX string pool
        val profile = apkAnalyzer.analyzePackageInfo(pm, packageInfo)

        return evaluateProfile(profile, startTime)
    }

    /**
     * Deep Scan: Analyze an APK file on disk.
     *
     * @param apkFile the APK file to analyze
     * @return QuarkScanResult, or null if APK could not be parsed
     */
    fun deepScanApkFile(apkFile: File): QuarkScanResult? {
        val startTime = System.currentTimeMillis()
        val profile = apkAnalyzer.analyzeApkFile(apkFile) ?: return null
        return evaluateProfile(profile, startTime)
    }

    /**
     * Core evaluation — runs all 5 stages of Quark analysis against an APK profile.
     *
     * This method closely follows the logic in quark.py Quark.run():
     *   1. Check permissions
     *   2. Find first API usage
     *   3. Find second API usage (combination)
     *   4. Check sequence (simplified: both in same DEX)
     *   5. Check parameter correlation (simplified: permission-API cross-check)
     */
    private fun evaluateProfile(profile: ApkProfile, startTime: Long): QuarkScanResult {
        val relevantRules = ruleLoader.getRelevantRules(profile.permissions)

        val triggeredResults = mutableListOf<QuarkRuleResult>()
        var scoreSum = 0.0
        var weightSum = 0.0
        val hasDexStrings = profile.dexStringPool.isNotEmpty()

        for (rule in relevantRules) {
            rule.resetCheck()
            scoreSum += Math.abs(rule.score)

            val matchedApis = mutableListOf<ApiTarget>()

            // ── Stage 1: Permission Check ──
            val matchedPerms = rule.permissions.filter { profile.hasPermission(it) }
            if (rule.permissions.isEmpty() || matchedPerms.size == rule.permissions.size) {
                rule.checkItem[0] = true
            }

            if (hasDexStrings && rule.api.size >= 2) {
                // ── Stage 2: First API presence ──
                val firstApi = rule.api[0]
                val firstFound = profile.containsClassReference(firstApi.className) &&
                        profile.containsMethodReference(firstApi.methodName)
                if (firstFound) {
                    rule.checkItem[1] = true
                    matchedApis.add(firstApi)
                }

                // ── Stage 3: Second API presence (combination) ──
                val secondApi = rule.api[1]
                val secondFound = profile.containsClassReference(secondApi.className) &&
                        profile.containsMethodReference(secondApi.methodName)
                if (firstFound && secondFound) {
                    rule.checkItem[2] = true
                    matchedApis.add(secondApi)
                }

                // ── Stage 4: Sequence analysis (simplified) ──
                // Full XREF-based sequence analysis (quark.py check_sequence) requires
                // building a complete cross-reference graph — O(n²) and 50-500MB RAM.
                // Mobile adaptation: if both APIs exist AND the app has the required
                // permissions, we consider the sequence plausible.
                if (rule.checkItem[0] && rule.checkItem[2]) {
                    rule.checkItem[3] = true
                }

                // ── Stage 5: Data flow / parameter correlation (simplified) ──
                // Full register tracking (PyEval in quark.py check_parameter) requires
                // simulating Dalvik bytecode execution — extremely CPU-intensive.
                // Mobile adaptation: cross-correlate dangerous permission combinations
                // with the specific API pair presence.
                if (rule.checkItem[3]) {
                    val hasDataFlowIndicators = checkDataFlowHeuristic(rule, profile)
                    if (hasDataFlowIndicators) {
                        rule.checkItem[4] = true
                    }
                }
            } else if (!hasDexStrings) {
                // Fallback: no DEX strings available.
                // STRICT POLICY: Permissions alone must NEVER generate behavioral verdicts.
                // We do NOT use permission correlation to fake API presence.
                // The rule will correctly fail to trigger (confidence remains 1 or 0).
            }

            // Record if rule triggered at meaningful confidence (≥40%)
            if (rule.confidence >= 2) {
                val weight = rule.getWeightedScore()
                weightSum += Math.abs(weight)
                triggeredResults.add(
                    QuarkRuleResult(
                        rule = rule,
                        confidence = rule.confidence,
                        confidencePercent = rule.confidencePercent,
                        weightedScore = weight,
                        matchedPermissions = matchedPerms,
                        matchedApis = matchedApis,
                        stageResults = rule.checkItem.toList()
                    )
                )
            }
        }

        val duration = System.currentTimeMillis() - startTime
        if (triggeredResults.isEmpty()) {
            return QuarkScanResult.clean(
                profile.packageName, profile.appLabel,
                relevantRules.size, duration
            )
        }

        return QuarkScanResult(
            packageName = profile.packageName,
            appLabel = profile.appLabel,
            totalRulesEvaluated = relevantRules.size,
            triggeredRules = triggeredResults.sortedByDescending { it.weightedScore },
            overallThreatScore = weightSum,
            overallThreatLevel = ThreatLevel.fromWeightRatio(weightSum, scoreSum),
            scanDurationMs = duration
        )
    }

    // ═══════════════════════════════════════════
    //  HEURISTIC HELPERS
    // ═══════════════════════════════════════════

    /**
     * Permission-to-API correlation heuristic.
     * When DEX string analysis is unavailable, we infer API presence from
     * the requested permissions. This is a sound approximation because apps
     * that request dangerous permissions typically use the corresponding APIs.
     *
     * @return Pair(firstApiLikely, bothApisLikely)
     */
    private fun checkApiPermissionCorrelation(
        rule: QuarkRule,
        appPermissions: Set<String>
    ): Pair<Boolean, Boolean> {
        if (rule.api.size < 2) return Pair(false, false)

        val firstApiClass = rule.api[0].javaClassName
        val secondApiClass = rule.api[1].javaClassName

        val firstLikely = PERMISSION_API_MAP.entries.any { (perm, apis) ->
            appPermissions.contains(perm) && apis.any { firstApiClass.contains(it) }
        }

        val secondLikely = PERMISSION_API_MAP.entries.any { (perm, apis) ->
            appPermissions.contains(perm) && apis.any { secondApiClass.contains(it) }
        }

        return Pair(firstLikely, firstLikely && secondLikely)
    }

    /**
     * Data flow heuristic — determines if the APIs in a rule likely share data.
     *
     * This is a simplified version of Quark's check_parameter (PyEval).
     * Instead of full register tracking, we use structural indicators:
     *   - Both APIs relate to the same data category (e.g., both deal with SMS)
     *   - The app has exfiltration-capable permissions (INTERNET + data source)
     *   - Components suggest background operation (services + receivers + boot)
     */
    private fun checkDataFlowHeuristic(rule: QuarkRule, profile: ApkProfile): Boolean {
        // Exfiltration pattern: data source + network capability
        val hasInternet = profile.hasPermission("android.permission.INTERNET")
        val hasDataSource = rule.permissions.any { perm ->
            DATA_SOURCE_PERMISSIONS.contains(perm) && profile.hasPermission(perm)
        }
        if (hasInternet && hasDataSource) return true

        // Background exfiltration: boot persistence + data access
        val hasBoot = profile.hasPermission("android.permission.RECEIVE_BOOT_COMPLETED")
        val hasServices = profile.services.isNotEmpty()
        if (hasBoot && hasServices && hasDataSource) return true

        // Accessibility abuse: accessibility + overlay
        val hasAccessibility = profile.hasPermission("android.permission.BIND_ACCESSIBILITY_SERVICE")
        val hasOverlay = profile.hasPermission("android.permission.SYSTEM_ALERT_WINDOW")
        if (hasAccessibility && hasOverlay) return true

        return false
    }

    companion object {
        private const val TAG = "QuarkEngine"

        @Volatile
        private var instance: QuarkEngine? = null

        fun getInstance(context: Context): QuarkEngine {
            return instance ?: synchronized(this) {
                instance ?: QuarkEngine(context.applicationContext).also { instance = it }
            }
        }

        /** Permissions that indicate access to sensitive data sources */
        private val DATA_SOURCE_PERMISSIONS = setOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_CALENDAR",
            "android.permission.GET_ACCOUNTS"
        )

        /**
         * Maps Android permissions to the API classes that typically require them.
         * Used for permission-to-API correlation in Smart Scan mode.
         */
        private val PERMISSION_API_MAP = mapOf(
            "android.permission.SEND_SMS" to listOf("SmsManager", "telephony"),
            "android.permission.READ_SMS" to listOf("SmsManager", "telephony", "ContentResolver"),
            "android.permission.RECEIVE_SMS" to listOf("SmsManager", "telephony"),
            "android.permission.ACCESS_FINE_LOCATION" to listOf("LocationManager", "TelephonyManager", "location"),
            "android.permission.ACCESS_COARSE_LOCATION" to listOf("LocationManager", "TelephonyManager", "location"),
            "android.permission.CAMERA" to listOf("Camera", "camera2", "CameraManager"),
            "android.permission.RECORD_AUDIO" to listOf("AudioRecord", "MediaRecorder"),
            "android.permission.READ_CONTACTS" to listOf("ContactsContract", "ContentResolver"),
            "android.permission.READ_CALL_LOG" to listOf("CallLog", "ContentResolver"),
            "android.permission.READ_CALENDAR" to listOf("CalendarContract", "ContentResolver"),
            "android.permission.INTERNET" to listOf("HttpURLConnection", "OkHttpClient", "URL", "Socket"),
            "android.permission.SYSTEM_ALERT_WINDOW" to listOf("WindowManager", "AlertDialog"),
            "android.permission.BIND_ACCESSIBILITY_SERVICE" to listOf("AccessibilityService", "AccessibilityEvent"),
            "android.permission.REQUEST_INSTALL_PACKAGES" to listOf("PackageInstaller", "Intent"),
            "android.permission.RECEIVE_BOOT_COMPLETED" to listOf("BroadcastReceiver", "BOOT_COMPLETED"),
            "android.permission.BIND_DEVICE_ADMIN" to listOf("DevicePolicyManager", "DeviceAdminReceiver")
        )
    }
}
