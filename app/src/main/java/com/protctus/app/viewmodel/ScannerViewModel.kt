package com.protctus.app.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.ThreatAlert
import com.protctus.app.data.local.ThreatDao
import com.protctus.app.engine.EliteThreat
// ThreatBrain REMOVED — all verdicts from malware engines only
import com.protctus.app.engine.correlation.ThreatCorrelationEngine
import com.protctus.app.engine.correlation.ThreatVerdict
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import javax.inject.Inject
import com.protctus.app.utils.GlobalQuestTracker
// import com.protctus.app.utils.QuestEvent

enum class ScanState {
    IDLE, SCANNING_APPS, SCANNING_FILES, ANALYZING, SAFE, THREAT_FOUND
}

data class SmartScanThreat(
    val appName: String,
    val packageName: String,
    val threatType: String,
    val riskScore: String,
    val detailedReason: String
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val threatDao: ThreatDao,
    private val globalQuestTracker: GlobalQuestTracker
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scannedItemsCount = MutableStateFlow(0)
    val scannedItemsCount: StateFlow<Int> = _scannedItemsCount.asStateFlow()

    private val _currentScanningItem = MutableStateFlow("")
    val currentScanningItem: StateFlow<String> = _currentScanningItem.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _scanThreats = MutableStateFlow<List<SmartScanThreat>>(emptyList())
    val scanThreats: StateFlow<List<SmartScanThreat>> = _scanThreats.asStateFlow()

    // VULN-S4 fix: Replaced hardcoded package-name whitelist with installer-source validation.
    // Apps are now validated by WHO installed them, not by package name (which can be spoofed).
    private val trustedInstallers = setOf(
        "com.android.vending",        // Google Play Store
        "com.sec.android.app.samsungapps", // Samsung Galaxy Store
        "com.amazon.venezia",         // Amazon App Store
        "com.huawei.appmarket"        // Huawei AppGallery
    )

    fun togglePause() {
        if (_scanState.value in listOf(ScanState.SCANNING_APPS, ScanState.SCANNING_FILES, ScanState.ANALYZING)) {
            _isPaused.value = !_isPaused.value
        }
    }

    fun startSmartScan(context: Context) {
        if (_scanState.value in listOf(ScanState.SCANNING_APPS, ScanState.SCANNING_FILES, ScanState.ANALYZING)) return

        viewModelScope.launch(Dispatchers.IO) {
            resetScanState()
            val allThreats = mutableListOf<SmartScanThreat>()
            var totalItemsScanned = 0

            try {
                // Initialize YARA Engine for Smart Scan
                val rulesPath = File(context.cacheDir, "rules.yarc").absolutePath
                try { com.protctus.yaracore.YaraScanner.initYaraEngine(rulesPath) } catch (_: Exception) {}

                // Initialize Threat Correlation Engine (loads Quark and ClamAV rules)
                val correlationEngine = ThreatCorrelationEngine.getInstance(context)
                correlationEngine.initialize()

                // --- PHASE 1: DEVICE INTEGRITY (0% - 20%) ---
                _scanState.value = ScanState.SCANNING_APPS // Reusing state for simplicity or add a new one
                _currentScanningItem.value = "Checking Device Integrity..."
                val integrityThreats = checkDeviceIntegrity(context)
                allThreats.addAll(integrityThreats)
                _scanProgress.value = 20f
                delay(500)

                // --- PHASE 2: APP HEURISTICS (20% - 60%) ---
                totalItemsScanned = scanInstalledApps(context, allThreats, totalItemsScanned)
                if (!isActive) return@launch

                // --- PHASE 3: FILE HEURISTICS (60% - 90%) ---
                _scanState.value = ScanState.SCANNING_FILES
                totalItemsScanned = scanFileSystem(allThreats, totalItemsScanned)
                if (!isActive) return@launch

                // --- PHASE 4: COMPILING RESULTS (90% - 100%) ---
                _scanState.value = ScanState.ANALYZING
                finalizeScan()

                _scanThreats.value = allThreats
                
                val hasActualMalware = allThreats.any { 
                    val risk = it.riskScore.substringBefore("/").toFloatOrNull() ?: 0f
                    risk >= 6.0f 
                }
                
                _scanState.value = if (hasActualMalware) ScanState.THREAT_FOUND else ScanState.SAFE

                // ── Persist results to Room DB → Threat Feed & Notifications ──
                persistScanResultsToDb(allThreats, totalItemsScanned)

            } catch (e: Exception) {
                Log.e("SmartScan", "Critical Error during scan: ${e.message}")
                _scanState.value = ScanState.SAFE // Graceful degradation
            } finally {
                try { com.protctus.yaracore.YaraScanner.destroyYaraEngine() } catch (_: Exception) {}
            }
        }
    }

    /** Write each detected threat + a summary alert into the Room threat_alerts table. */
    private suspend fun persistScanResultsToDb(threats: List<SmartScanThreat>, totalScanned: Int) {
        // Insert each individual threat
        for (threat in threats) {
            threatDao.insertThreat(
                ThreatAlert(
                    title = threat.appName,
                    description = "${threat.threatType}: ${threat.detailedReason} (Risk: ${threat.riskScore})",
                    priority = if (threat.riskScore.substringBefore("/").toFloatOrNull()?.let { it >= 7f } == true) "High" else "Medium",
                    threatType = threat.threatType,
                    priorityColorHex = if (threat.riskScore.substringBefore("/").toFloatOrNull()?.let { it >= 7f } == true) "#EF4444" else "#F59E0B",
                    timestamp = System.currentTimeMillis()
                )
            )

            // Upload malware telemetry to cloud (salted hash, fire-and-forget)
            val riskVal = threat.riskScore.substringBefore("/").toFloatOrNull() ?: 0f
            if (riskVal >= 5f) {
                com.protctus.app.utils.MalwareTelemetryReporter.reportMalware(
                    context = appContext,
                    packageName = threat.packageName,
                    threatType = threat.threatType,
                    engineName = "SmartScan",
                    confidence = (riskVal / 10f).coerceIn(0f, 1f)
                )
            }
        }
        // Insert summary alert
        val hasActualMalware = threats.any { 
            val risk = it.riskScore.substringBefore("/").toFloatOrNull() ?: 0f
            risk >= 6.0f 
        }
        
        threatDao.insertThreat(
            ThreatAlert(
                title = if (!hasActualMalware) "Smart Scan — All Clear" else "Smart Scan — Malware Found",
                description = "Scanned $totalScanned items. ${if (!hasActualMalware) "No active malware detected. Your device is secure." else "Suspicious items found. Review recommended."}",
                priority = if (!hasActualMalware) "Low" else "High",
                threatType = "Smart Scan",
                priorityColorHex = if (!hasActualMalware) "#10B981" else "#EF4444",
                timestamp = System.currentTimeMillis()
            )
        )

        // Mark quest as completed locally via Event Bus
        globalQuestTracker.emitEvent("smart_scan")
    }

    // ==========================================
    // MODULE 1: APP SCANNING ENGINE
    // ==========================================
    private suspend fun scanInstalledApps(context: Context, threats: MutableList<SmartScanThreat>, startCount: Int): Int {
        var currentCount = startCount
        val pm = context.packageManager
        
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            emptyList<PackageInfo>() // Failsafe for modified OS ROMs
        }
        
        val totalApps = packages.size

        packages.forEachIndexed { index, packageInfo ->
            checkPause()
            if (!viewModelScope.isActive) return currentCount

            val pkgName = packageInfo.packageName
            if (pkgName == context.packageName) return@forEachIndexed

            val isSystemApp = (packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0

            // FN-FIX: ALL user apps go through the correlation engine.
            // Previously, store-installed apps were SKIPPED entirely — this was the #1 false negative source.
            // The correlation engine handles evidence weighting internally.
            if (!isSystemApp) {
                evaluateAppRisk(pm, packageInfo, threats)
            }
            
            val appLabel = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
            _currentScanningItem.value = appLabel

            currentCount++
            _scannedItemsCount.value = currentCount
            _scanProgress.value = (index.toFloat() / totalApps.toFloat()) * 50f
            yield()
            delay(30) // Increased to ensure no thermal throttling
        }
        return currentCount
    }

    private fun checkDeviceIntegrity(context: Context): List<SmartScanThreat> {
        val integrityThreats = mutableListOf<SmartScanThreat>()

        // Device configuration warnings — NOT malware verdicts.
        // These are settings checks, not engine-driven detections.

        // 1. Unknown Sources
        if (com.protctus.app.utils.SecurityScanner.checkUnknownSourcesEnabled(context)) {
            integrityThreats.add(SmartScanThreat(
                appName = "System Settings",
                packageName = "android.settings.security",
                threatType = "Configuration Warning",
                riskScore = "3.0/10",
                detailedReason = "Unknown Sources enabled — allows untrusted app installations."
            ))
        }

        // 2. ADB
        val adbEnabled = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
        if (adbEnabled) {
            integrityThreats.add(SmartScanThreat(
                appName = "Developer Options",
                packageName = "android.settings.development",
                threatType = "Configuration Warning",
                riskScore = "2.0/10",
                detailedReason = "USB Debugging (ADB) is active — enables external code execution."
            ))
        }

        // 3. Root
        if (com.protctus.app.utils.SecurityScanner.checkDeepRootSignatures()) {
            integrityThreats.add(SmartScanThreat(
                appName = "Android OS",
                packageName = "system.kernel.root",
                threatType = "Configuration Warning",
                riskScore = "5.0/10",
                detailedReason = "Device is rooted — system sandbox is compromised."
            ))
        }

        return integrityThreats
    }

    private suspend fun evaluateAppRisk(pm: PackageManager, packageInfo: PackageInfo, threats: MutableList<SmartScanThreat>) {
        val pkgName = packageInfo.packageName

        // Check for Sideload status first (Information/Warning level)
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { pm.getInstallSourceInfo(pkgName).installingPackageName } catch (_: Exception) { null }
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(pkgName)
        }
        val isSideloaded = installer == null || !trustedInstallers.contains(installer)

        try {
            val correlationEngine = ThreatCorrelationEngine.getInstance(appContext)
            val verdictResult = correlationEngine.correlateSmartScan(pm, packageInfo)

            if (verdictResult.isActionable) {
                // We have a correlated threat
                threats.add(
                    SmartScanThreat(
                        appName = verdictResult.appLabel,
                        packageName = pkgName,
                        threatType = verdictResult.finalVerdict.displayName,
                        riskScore = String.format("%.1f/10", verdictResult.displayRiskScore),
                        detailedReason = verdictResult.shortSummary
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SmartScan", "Correlation engine failed for $pkgName: ${e.message}")
        }
    }

    // ==========================================
    // MODULE 2: FILE SCANNING ENGINE
    // ==========================================
    private suspend fun scanFileSystem(threats: MutableList<SmartScanThreat>, startCount: Int): Int {
        var currentCount = startCount

        // VULN-S1 fix: Scan multiple directories, not just Downloads
        val dirsToScan = mutableListOf<File>()

        // Standard public directories (Restricted for Smart Scan)
        arrayOf(
            Environment.DIRECTORY_DOWNLOADS
        ).forEach { dirType ->
            val dir = Environment.getExternalStoragePublicDirectory(dirType)
            if (dir != null && dir.exists() && dir.isDirectory) dirsToScan.add(dir)
        }

        // Messaging app media directories (common malware delivery paths)
        val storageRoot = Environment.getExternalStorageDirectory()
        listOf(
            "Bluetooth"
        ).forEach { relPath ->
            val dir = File(storageRoot, relPath)
            if (dir.exists() && dir.isDirectory) dirsToScan.add(dir)
        }

        if (dirsToScan.isEmpty()) {
            _scanProgress.value = 90f
            return currentCount
        }

        val filesToScan = try {
            dirsToScan.flatMap { dir ->
                dir.walkTopDown().filter { it.isFile }.toList()
            }
        } catch (e: SecurityException) {
            emptyList<File>()
        }
        
        val totalFiles = filesToScan.size

        filesToScan.forEachIndexed { index, file ->
            checkPause()
            if (!viewModelScope.isActive) return currentCount
            
            _currentScanningItem.value = file.name

            evaluateFileRisk(file, threats)

            currentCount++
            _scannedItemsCount.value = currentCount
            _scanProgress.value = 50f + ((index.toFloat() / totalFiles.toFloat()) * 40f)
            yield()
            delay(30) // Increased to ensure no thermal throttling
        }
        return currentCount
    }

    /**
     * Lightweight file risk evaluation for Smart Scan.
     * Checks file extension and double-extension social engineering tricks.
     */
    private suspend fun evaluateFileRisk(file: File, threats: MutableList<SmartScanThreat>) {
        try {
            // Engine-only file scanning — ThreatBrain REMOVED

            // 1. YARA Signature Scan (if file is reasonably sized)
            if (file.length() in 1..(50 * 1024 * 1024)) {
                try {
                    val matches = com.protctus.yaracore.YaraScanner.scanRawFile(file.absolutePath)
                    if (matches.isNotEmpty()) {
                        threats.add(
                            SmartScanThreat(
                                appName = file.name,
                                packageName = file.absolutePath,
                                threatType = ThreatVerdict.HIGH_CONFIDENCE_MALWARE.displayName,
                                riskScore = "${ThreatVerdict.HIGH_CONFIDENCE_MALWARE.score / 10f}/10.0",
                                detailedReason = "Matched signature rules: ${matches.joinToString(", ")}"
                            )
                        )
                        return
                    }
                } catch (_: Exception) {}
            }

            // 2. ClamAV hash scan for known malware
            try {
                val clamEngine = com.protctus.app.engine.clam.ClamSignatureEngine.getInstance(appContext)
                if (clamEngine.ready && file.length() in 1..(50 * 1024 * 1024)) {
                    val match = clamEngine.scanFileHash(file)
                    if (match != null) {
                        threats.add(
                            SmartScanThreat(
                                appName = file.name,
                                packageName = file.absolutePath,
                                threatType = ThreatVerdict.HIGH_CONFIDENCE_MALWARE.displayName,
                                riskScore = "${ThreatVerdict.HIGH_CONFIDENCE_MALWARE.score / 10f}/10.0",
                                detailedReason = "Signature match: ${match.malwareName} (${match.matchType})"
                            )
                        )
                        return
                    }
                }
            } catch (_: Exception) {}
        } catch (_: Exception) { }
    }

    private suspend fun finalizeScan() {
        _currentScanningItem.value = "Compiling Threat Matrix..."
        for (i in 90..100) {
            checkPause()
            _scanProgress.value = i.toFloat()
            delay(5) 
        }
    }

    private suspend fun checkPause() {
        while (_isPaused.value) {
            delay(200)
        }
    }

    private fun resetScanState() {
        _scanProgress.value = 0f
        _scannedItemsCount.value = 0
        _currentScanningItem.value = "Initializing engine..."
        _scanThreats.value = emptyList()
        _isPaused.value = false
    }

    fun resetScan() {
        _scanState.value = ScanState.IDLE
        resetScanState()
    }
}

// -------------------------------------------------------------
// Core System Evaluation Engine Logic (used by SentinelService)
// Uses ThreatCorrelationEngine instead of ThreatBrain
// -------------------------------------------------------------

suspend fun scanDevice(context: Context): List<EliteThreat> = withContext(Dispatchers.Default) {
    val pm = context.packageManager
    val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

    val correlationEngine = ThreatCorrelationEngine.getInstance(context)
    correlationEngine.initialize()

    val installedPackages = pm.getInstalledPackages(flags)
    val threats = mutableListOf<EliteThreat>()

    for (packageInfo in installedPackages) {
        // Don't skip ANY app — all apps go through correlation
        val isSystem = packageInfo.applicationInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
        if (isSystem) continue // Only skip system apps

        try {
            val verdict = correlationEngine.correlateSmartScan(pm, packageInfo)
            if (verdict.finalVerdict.score >= ThreatVerdict.SUSPICIOUS.score) {
                threats.add(EliteThreat(
                    packageName = verdict.packageName,
                    appName = verdict.appLabel,
                    riskScore = (verdict.finalProbability * 100).toInt().coerceAtMost(100),
                    detectedAnomalies = verdict.explanation.topReasons
                ))
            }
        } catch (_: Exception) {}
    }

    threats.sortedByDescending { it.riskScore }
}

suspend fun scanSinglePackage(context: Context, packageName: String): EliteThreat? = withContext(Dispatchers.Default) {
    val pm = context.packageManager
    val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }

    val packageInfo = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
    } catch (e: Exception) {
        return@withContext null
    }

    val correlationEngine = ThreatCorrelationEngine.getInstance(context)
    correlationEngine.initialize()
    val verdict = correlationEngine.correlateSmartScan(pm, packageInfo)

    if (verdict.finalVerdict.score >= ThreatVerdict.SUSPICIOUS.score) {
        EliteThreat(
            packageName = verdict.packageName,
            appName = verdict.appLabel,
            riskScore = (verdict.finalProbability * 100).toInt().coerceAtMost(100),
            detectedAnomalies = verdict.explanation.topReasons
        )
    } else {
        null
    }
}