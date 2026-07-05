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
import com.protctus.app.engine.correlation.ThreatCorrelationEngine
import com.protctus.app.engine.ProcMalwareEngine
import com.protctus.app.engine.SystemEvidence
import com.protctus.app.engine.AppEvidence
import com.protctus.app.engine.FileEvidence
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

enum class ScanState {
    IDLE, SCANNING_APPS, SCANNING_FILES, ANALYZING, COMPLETED
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

                // Initialize Threat Correlation Engine
                val correlationEngine = ThreatCorrelationEngine.getInstance(context)
                correlationEngine.initialize()

                // --- PHASE 1: DEVICE INTEGRITY ---
                _scanState.value = ScanState.SCANNING_APPS 
                _currentScanningItem.value = "Checking Device Integrity..."
                val integrityThreats = checkDeviceIntegrity(context)
                allThreats.addAll(integrityThreats)
                _scanProgress.value = 20f
                delay(500)

                // --- PHASE 2: APP HEURISTICS ---
                totalItemsScanned = scanInstalledApps(context, allThreats, totalItemsScanned)
                if (!isActive) return@launch

                // --- PHASE 3: FILE HEURISTICS ---
                _scanState.value = ScanState.SCANNING_FILES
                totalItemsScanned = scanFileSystem(allThreats, totalItemsScanned)
                if (!isActive) return@launch

                // --- PHASE 4: COMPILING RESULTS ---
                _scanState.value = ScanState.ANALYZING
                finalizeScan()

                _scanThreats.value = allThreats
                _scanState.value = ScanState.COMPLETED

                // ── Persist results to Room DB → Threat Feed & Notifications ──
                persistScanResultsToDb(allThreats, totalItemsScanned)

            } catch (e: Exception) {
                Log.e("SmartScan", "Critical Error during scan: ${e.message}")
                _scanState.value = ScanState.COMPLETED 
            } finally {
                try { com.protctus.yaracore.YaraScanner.destroyYaraEngine() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun persistScanResultsToDb(threats: List<SmartScanThreat>, totalScanned: Int) {
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

        globalQuestTracker.emitEvent("smart_scan")
    }

    private suspend fun scanInstalledApps(context: Context, threats: MutableList<SmartScanThreat>, startCount: Int): Int {
        var currentCount = startCount
        val pm = context.packageManager
        
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            emptyList<PackageInfo>() 
        }
        
        val totalApps = packages.size
        val safeInstallSources = listOf("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket")

        packages.forEachIndexed { index, packageInfo ->
            checkPause()
            if (!viewModelScope.isActive) return currentCount

            val pkgName = packageInfo.packageName
            if (pkgName == context.packageName) return@forEachIndexed

            val isSystemApp = (packageInfo.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0
            
            val appLabel = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
            _currentScanningItem.value = appLabel

            try {
                val correlationEngine = ThreatCorrelationEngine.getInstance(appContext)
                val verdictResult = correlationEngine.correlateSmartScan(pm, packageInfo)

                val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { pm.getInstallSourceInfo(pkgName).installingPackageName } catch (_: Exception) { null }
                } else {
                    @Suppress("DEPRECATION") pm.getInstallerPackageName(pkgName)
                }
                val isSideloaded = installer == null || !safeInstallSources.contains(installer)
                
                val evidence = AppEvidence(
                    packageName = pkgName,
                    appLabel = appLabel,
                    isSystemApp = isSystemApp,
                    isSideloaded = isSideloaded,
                    correlationVerdict = verdictResult
                )

                val engineVerdict = ProcMalwareEngine.evaluateApp(evidence)

                if (engineVerdict.decision.isActionable) {
                    threats.add(
                        SmartScanThreat(
                            appName = appLabel,
                            packageName = pkgName,
                            threatType = engineVerdict.decision.displayName,
                            riskScore = String.format("%.1f/10", engineVerdict.finalScore),
                            detailedReason = engineVerdict.explanation
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SmartScan", "Correlation engine failed for $pkgName: ${e.message}")
            }

            currentCount++
            _scannedItemsCount.value = currentCount
            _scanProgress.value = (index.toFloat() / totalApps.toFloat()) * 50f
            if (index % 20 == 0) {
                kotlinx.coroutines.delay(10) 
            }
        }
        return currentCount
    }

    private fun checkDeviceIntegrity(context: Context): List<SmartScanThreat> {
        val integrityThreats = mutableListOf<SmartScanThreat>()

        val unknownSources = com.protctus.app.utils.SecurityScanner.checkUnknownSourcesEnabled(context)
        val adbEnabled = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
        val isRooted = com.protctus.app.utils.SecurityScanner.checkDeepRootSignatures()

        val evidence = SystemEvidence(
            unknownSourcesEnabled = unknownSources,
            adbEnabled = adbEnabled,
            isRooted = isRooted
        )

        val verdict = ProcMalwareEngine.evaluateSystemIntegrity(evidence)

        if (verdict.decision.isActionable) {
            integrityThreats.add(SmartScanThreat(
                appName = "System Settings",
                packageName = "android.settings.security",
                threatType = verdict.decision.displayName,
                riskScore = "${verdict.finalScore}/10.0",
                detailedReason = verdict.explanation
            ))
        }

        return integrityThreats
    }

    private suspend fun scanFileSystem(threats: MutableList<SmartScanThreat>, startCount: Int): Int {
        var currentCount = startCount
        val dirsToScan = mutableListOf<File>()

        arrayOf(Environment.DIRECTORY_DOWNLOADS).forEach { dirType ->
            val dir = Environment.getExternalStoragePublicDirectory(dirType)
            if (dir != null && dir.exists() && dir.isDirectory) dirsToScan.add(dir)
        }

        val storageRoot = Environment.getExternalStorageDirectory()
        listOf("Bluetooth").forEach { relPath ->
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
            if (index % 100 == 0) {
                kotlinx.coroutines.delay(50) 
            }
        }
        return currentCount
    }

    private suspend fun evaluateFileRisk(file: File, threats: MutableList<SmartScanThreat>) {
        try {
            val ext = file.extension.lowercase()
            val riskyExts = setOf("apk", "dex", "so", "jar", "sh", "bat", "cmd", "ps1", "exe", "dll", "bin", "elf", "msi", "vbs", "js", "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "cab", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "rtf", "csv")
            
            if (!riskyExts.contains(ext)) return

            val yaraMatches = mutableListOf<String>()
            val clamAvMatches = mutableListOf<String>()
            var fileEntropy: Double? = null
            var hasDoubleExtension = false
            var hasRTLO = false

            if (file.length() in 1..(50 * 1024 * 1024)) {
                try {
                    val matches = com.protctus.yaracore.YaraScanner.scanRawFile(file.absolutePath)
                    yaraMatches.addAll(matches)
                } catch (_: Exception) {}

                try {
                    val clamEngine = com.protctus.app.engine.clam.ClamSignatureEngine.getInstance(appContext)
                    if (clamEngine.ready) {
                        val match = clamEngine.scanFileHash(file)
                        if (match != null) clamAvMatches.add(match.malwareName)
                    }
                } catch (_: Exception) {}
            }
            
            if (ext == "apk") {
                val sampleSize = 8192
                val freq = IntArray(256)
                var totalBytes = 0
                try {
                    java.io.FileInputStream(file).use { fis ->
                        val buf = ByteArray(sampleSize)
                        val read = fis.read(buf)
                        if (read > 0) {
                            for (i in 0 until read) freq[buf[i].toInt() and 0xFF]++
                            totalBytes = read
                        }
                    }
                    if (totalBytes > 0) {
                        var entropy = 0.0
                        for (count in freq) {
                            if (count > 0) {
                                val p = count.toDouble() / totalBytes
                                entropy -= p * (kotlin.math.ln(p) / kotlin.math.ln(2.0))
                            }
                        }
                        fileEntropy = entropy
                    }
                } catch (e: Exception) {}
            }

            hasRTLO = file.name.contains("\u202E")
            if (file.name.count { it == '.' } >= 2) {
                hasDoubleExtension = true
            }
            
            val evidence = FileEvidence(
                fileName = file.name,
                absolutePath = file.absolutePath,
                extension = ext,
                fileSize = file.length(),
                yaraMatches = yaraMatches,
                clamAvMatches = clamAvMatches,
                entropy = fileEntropy,
                isApkInWrongPlace = false,
                hasDoubleExtension = hasDoubleExtension,
                hasRTLO = hasRTLO,
                hasEOCDAtEnd = false,
                archiveRatio = null,
                archiveTraversal = false,
                hasEmbeddedExecutable = false
            )

            val engineVerdict = ProcMalwareEngine.evaluateFile(evidence)

            if (engineVerdict.decision.isActionable) {
                threats.add(
                    SmartScanThreat(
                        appName = file.name,
                        packageName = file.absolutePath,
                        threatType = engineVerdict.decision.displayName,
                        riskScore = "${engineVerdict.finalScore}/10.0",
                        detailedReason = engineVerdict.explanation
                    )
                )
            }
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
// Uses ThreatCorrelationEngine -> ProcMalwareEngine
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
    val safeInstallSources = listOf("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket")

    for (packageInfo in installedPackages) {
        val isSystem = packageInfo.applicationInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
        if (isSystem) continue 

        try {
            val verdict = correlationEngine.correlateSmartScan(pm, packageInfo)
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { pm.getInstallSourceInfo(packageInfo.packageName).installingPackageName } catch (_: Exception) { null }
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(packageInfo.packageName)
            }
            val isSideloaded = installer == null || !safeInstallSources.contains(installer)
            
            val appLabel = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageInfo.packageName

            val evidence = AppEvidence(
                packageName = packageInfo.packageName,
                appLabel = appLabel,
                isSystemApp = isSystem,
                isSideloaded = isSideloaded,
                correlationVerdict = verdict
            )
            
            val engineVerdict = ProcMalwareEngine.evaluateApp(evidence)

            if (engineVerdict.decision.isActionable) {
                threats.add(EliteThreat(
                    packageName = packageInfo.packageName,
                    appName = appLabel,
                    riskScore = (engineVerdict.finalScore * 10).toInt().coerceAtMost(100),
                    detectedAnomalies = listOf(engineVerdict.explanation)
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
    
    val safeInstallSources = listOf("com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket")
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try { pm.getInstallSourceInfo(packageName).installingPackageName } catch (_: Exception) { null }
    } else {
        @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
    }
    val isSideloaded = installer == null || !safeInstallSources.contains(installer)
    val appLabel = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
    val isSystem = ((packageInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0

    val evidence = AppEvidence(
        packageName = packageName,
        appLabel = appLabel,
        isSystemApp = isSystem,
        isSideloaded = isSideloaded,
        correlationVerdict = verdict
    )

    val engineVerdict = ProcMalwareEngine.evaluateApp(evidence)

    if (engineVerdict.decision.isActionable) {
        EliteThreat(
            packageName = packageName,
            appName = appLabel,
            riskScore = (engineVerdict.finalScore * 10).toInt().coerceAtMost(100),
            detectedAnomalies = listOf(engineVerdict.explanation)
        )
    } else {
        null
    }
}