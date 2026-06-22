package com.protctus.app.engine.androguard

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Androguard APK Analysis Engine for Android.
 *
 * Orchestrates all analysis subsystems (manifest, DEX, certificate, structure)
 * to produce a comprehensive APK security assessment.
 *
 * Provides two scanning modes:
 *   - Smart Scan: Lightweight manifest + permission analysis (<100ms per app)
 *   - Deep Scan: Full analysis including DEX inspection and structure analysis (<2s per app)
 *
 * This is a Kotlin-native reimplementation of Androguard's core analysis
 * capabilities, optimized for on-device Android execution without Python.
 */
class AndroguardEngine private constructor(private val context: Context) {

    private val manifestAnalyzer = ManifestAnalyzer(context)
    private val dexInspector = DexInspector()
    private val certAnalyzer = CertificateAnalyzer()
    private val structAnalyzer = ApkStructureAnalyzer()

    val ready: Boolean = true // No initialization required — stateless analyzers

    // ═══════════════════════════════════════════
    //  SMART SCAN MODE
    // ═══════════════════════════════════════════

    /**
     * Smart Scan: Lightweight APK analysis using only PackageManager metadata.
     * Does NOT open APK file — uses Android APIs only.
     * Target: <100ms per app.
     */
    fun smartScanPackage(pm: PackageManager, packageInfo: PackageInfo): ApkAnalysisResult {
        val startTime = System.currentTimeMillis()
        val packageName = packageInfo.packageName
        val appLabel = try {
            packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (e: Exception) { packageName }

        val appInfo = packageInfo.applicationInfo

        // Manifest analysis (uses PackageManager only — fast)
        val manifestFindings = manifestAnalyzer.analyze(pm, packageInfo)

        // Empty DEX/structure findings for Smart Scan
        val emptyDex = DexFindings(0, 0, 0, emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), 0, emptyList())
        val emptyStructure = ApkStructureFindings(0, emptyList(), emptyList(), emptyList(),
            false, false, false, 0, emptyList())

        // Certificate analysis (fast — uses PackageManager)
        val certFindings = certAnalyzer.analyze(pm, packageInfo)

        val allFindings = manifestFindings.findings + certFindings.findings
        val riskScore = calculateRiskScore(allFindings)

        return ApkAnalysisResult(
            packageName = packageName,
            appLabel = appLabel,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                appInfo?.minSdkVersion ?: 0 else 0,
            manifestFindings = manifestFindings,
            dexFindings = emptyDex,
            certificateFindings = certFindings,
            structureFindings = emptyStructure,
            overallRiskScore = riskScore,
            overallRiskLevel = RiskLevel.fromScore(riskScore),
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    // ═══════════════════════════════════════════
    //  DEEP SCAN MODE
    // ═══════════════════════════════════════════

    /**
     * Deep Scan: Comprehensive APK analysis including DEX inspection.
     * Opens and inspects the APK file.
     * Target: <2s per app.
     */
    fun deepScanPackage(pm: PackageManager, packageInfo: PackageInfo): ApkAnalysisResult {
        val startTime = System.currentTimeMillis()
        val packageName = packageInfo.packageName
        val appLabel = try {
            packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (e: Exception) { packageName }

        val appInfo = packageInfo.applicationInfo

        // Full manifest analysis
        val manifestFindings = manifestAnalyzer.analyze(pm, packageInfo)

        // Certificate analysis
        val certFindings = certAnalyzer.analyze(pm, packageInfo)

        // DEX and structure analysis (requires APK file access)
        val apkPath = appInfo?.publicSourceDir
        val dexFindings: DexFindings
        val structFindings: ApkStructureFindings

        if (apkPath != null && File(apkPath).exists()) {
            val apkFile = File(apkPath)
            dexFindings = dexInspector.inspectApk(apkFile)
            structFindings = structAnalyzer.analyze(apkFile)
        } else {
            dexFindings = DexFindings(0, 0, 0, emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList(), 0, emptyList())
            structFindings = ApkStructureFindings(0, emptyList(), emptyList(), emptyList(),
                false, false, false, 0, emptyList())
        }

        val allFindings = manifestFindings.findings + certFindings.findings +
                dexFindings.findings + structFindings.findings
        val riskScore = calculateRiskScore(allFindings)

        return ApkAnalysisResult(
            packageName = packageName,
            appLabel = appLabel,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                appInfo?.minSdkVersion ?: 0 else 0,
            manifestFindings = manifestFindings,
            dexFindings = dexFindings,
            certificateFindings = certFindings,
            structureFindings = structFindings,
            overallRiskScore = riskScore,
            overallRiskLevel = RiskLevel.fromScore(riskScore),
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Deep Scan: Analyze an APK file on disk.
     */
    fun deepScanApkFile(apkFile: File): ApkAnalysisResult? {
        if (!apkFile.exists() || !apkFile.canRead()) return null
        val startTime = System.currentTimeMillis()

        val pm = context.packageManager
        val packageInfo = try {
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_PROVIDERS
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse APK: ${e.message}")
            null
        } ?: return null

        packageInfo.applicationInfo?.apply {
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }

        val manifestFindings = manifestAnalyzer.analyze(pm, packageInfo)
        val dexFindings = dexInspector.inspectApk(apkFile)
        val structFindings = structAnalyzer.analyze(apkFile)
        // Certificate analysis from archive
        val certFindings = certAnalyzer.analyze(pm, packageInfo)

        val allFindings = manifestFindings.findings + certFindings.findings +
                dexFindings.findings + structFindings.findings
        val riskScore = calculateRiskScore(allFindings)

        val packageName = packageInfo.packageName ?: apkFile.nameWithoutExtension
        val appLabel = try {
            packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (e: Exception) { packageName }
        val appInfo = packageInfo.applicationInfo

        return ApkAnalysisResult(
            packageName = packageName,
            appLabel = appLabel,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            targetSdk = appInfo?.targetSdkVersion ?: 0,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                appInfo?.minSdkVersion ?: 0 else 0,
            manifestFindings = manifestFindings,
            dexFindings = dexFindings,
            certificateFindings = certFindings,
            structureFindings = structFindings,
            overallRiskScore = riskScore,
            overallRiskLevel = RiskLevel.fromScore(riskScore),
            scanDurationMs = System.currentTimeMillis() - startTime
        )
    }

    // ═══════════════════════════════════════════
    //  RISK SCORE CALCULATION
    // ═══════════════════════════════════════════

    /**
     * Calculate overall risk score from all findings.
     * Uses a weighted scoring model:
     *   - CRITICAL: +25 per finding (capped at 100)
     *   - HIGH: +15 per finding
     *   - MEDIUM: +8 per finding
     *   - LOW: +3 per finding
     *   - INFO: +0
     */
    private fun calculateRiskScore(findings: List<AnalysisFinding>): Int {
        var score = 0
        for (finding in findings) {
            score += when (finding.severity) {
                Severity.CRITICAL -> 25
                Severity.HIGH -> 15
                Severity.MEDIUM -> 8
                Severity.LOW -> 3
                Severity.INFO -> 0
            }
        }
        return score.coerceIn(0, 100)
    }

    companion object {
        private const val TAG = "AndroguardEngine"

        @Volatile
        private var instance: AndroguardEngine? = null

        fun getInstance(context: Context): AndroguardEngine {
            return instance ?: synchronized(this) {
                instance ?: AndroguardEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
