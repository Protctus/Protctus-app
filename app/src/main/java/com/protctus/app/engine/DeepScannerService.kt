package com.protctus.app.engine

import android.util.Log
import com.protctus.app.engine.clam.ClamSignatureEngine
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.protctus.app.MainActivity
import com.protctus.app.utils.SecurityScanner
import com.protctus.app.viewmodel.DeepScanState
import com.protctus.yaracore.YaraScanner
import com.protctus.app.viewmodel.SystemProfile
import com.protctus.app.engine.correlation.DeviceCapabilityAnalyzer
import com.protctus.app.engine.correlation.AdaptiveScanConfig
import com.protctus.app.engine.correlation.ThreatCorrelationEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.ln

class DeepScannerService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "deep_scanner_service_channel"
        private const val NOTIFICATION_ID = 5002

        val globalScanState = MutableStateFlow<DeepScanState>(DeepScanState.Idle)
        val globalIsPaused = MutableStateFlow(false)
        var cachedProfile: SystemProfile? = null

        fun startScan(context: Context, profile: SystemProfile) {
            cachedProfile = profile
            val intent = Intent(context, DeepScannerService::class.java).apply {
                action = "START_DEEP_SCAN"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopScan(context: Context) {
            val intent = Intent(context, DeepScannerService::class.java).apply {
                action = "STOP_DEEP_SCAN"
            }
            context.stopService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val scanThreats = mutableListOf<ScanThreat>()
    private var isCancelled = false
    private var scannedCount = 0
    private var scanStartTime = 0L

    external fun checkSlidingWindowEntropyNative(path: String): Boolean

    init {
        try {
            System.loadLibrary("native_scanner")
        } catch (e: UnsatisfiedLinkError) {}
    }

    // Dangerous file extensions that warrant deeper inspection removed (moved to FileFeatureExtractor)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            "START_DEEP_SCAN" -> {
                isCancelled = false
                val notification = buildNotification("Initializing absolute device traversal...").build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                executeExtremeScan()
            }
            "STOP_DEEP_SCAN" -> {
                isCancelled = true
                globalScanState.value = DeepScanState.Idle
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun executeExtremeScan() {
        val profile = cachedProfile ?: return
        val totalItems = profile.totalAppsToScan + profile.totalFilesToScan
        globalScanState.value = DeepScanState.Scanning(0f, "Analyzing System Settings...", 0)
        scanThreats.clear()
        scannedCount = 0

        lifecycleScope.launch(Dispatchers.IO) {
            Thread.currentThread().priority = Thread.MIN_PRIORITY
            scanStartTime = System.currentTimeMillis()

            // Initialize YARA Engine
            val rulesFile = File(cacheDir, "rules.yarc")
            if (!rulesFile.exists()) {
                try {
                    assets.open("rules.yarc").use { input ->
                        rulesFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {}
            }
            try { YaraScanner.initYaraEngine(rulesFile.absolutePath) } catch (_: Exception) {}

            // Initialize Threat Correlation Engine
            try {
                val correlationEngine = ThreatCorrelationEngine.getInstance(this@DeepScannerService)
                correlationEngine.initialize()
            } catch (_: Exception) {}

            // Device Capability Analysis
            val deviceProfile = DeviceCapabilityAnalyzer(this@DeepScannerService).analyze()
            val scanConfig = deviceProfile.scanConfig
            Log.i("DeepScan", "Starting Deep Scan with Adaptive Config: tier=${deviceProfile.tier}, concurrent=${scanConfig.maxConcurrentScans}, maxDuration=${scanConfig.maxScanDurationMinutes}m")

            if (isCancelled) return@launch

            // ==========================================
            // PHASE 1: Installed Application Deep Scan
            // ==========================================
            runAppScan(totalItems, scanConfig)

            if (isCancelled) return@launch

            // ==========================================
            // PHASE 2: Complete Device File Traversal
            // ==========================================
            runFullFileSystemScan(totalItems, scanConfig)

            // ==========================================
            // PHASE 3: Completion
            // ==========================================
            try { YaraScanner.destroyYaraEngine() } catch (_: Exception) {}

            if (!isCancelled) {
                withContext(Dispatchers.Main) {
                    globalScanState.value = DeepScanState.Completed(scanThreats.toList(), scannedCount)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun runAppScan(totalItems: Int, config: AdaptiveScanConfig) {
        val pm = packageManager
        val pkgFlags = PackageManager.GET_PERMISSIONS or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
                else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)

        try {
            val installedPackages = pm.getInstalledPackages(pkgFlags)
            val safeInstallSources = listOf(
                "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia",
                "com.huawei.appmarket", "com.xiaomi.mipicks", "com.bbk.appstore", "com.heytap.market",
                "com.oppo.market", "com.vivo.appstore", "com.coloros.appmanager"
            )

            val batchSize = config.scanBatchSize.coerceAtLeast(1)
            installedPackages.chunked(batchSize).forEach { chunk ->
                if (isCancelled) return
                checkPauseStatus()

                chunk.forEach { pkg ->
                    if (pkg.packageName == packageName) {
                        scannedCount++
                        return@forEach
                    }
                    val appName = pkg.applicationInfo?.let {
                        try { pm.getApplicationLabel(it).toString() } catch (_: Exception) { pkg.packageName }
                    } ?: pkg.packageName

                    updateStateAndNotification(scannedCount, totalItems, "App: $appName")

                    try {
                        val correlationEngine = ThreatCorrelationEngine.getInstance(this@DeepScannerService)

                        val isSystemApp = (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0 || 
                                          (pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) ?: 0) != 0

                        // System apps must use Smart Scan (lightweight) to avoid engine noise
                        // causing false positives. Deep Scan engines (Quark deep, ClamAV full,
                        // YARA, Androguard deep) produce signals even for benign system apps
                        // due to elevated permissions and native code, which bypasses SafeScreener's
                        // system app fast-path and triggers the reasoning pipeline incorrectly.
                        val correlationVerdict = if (isSystemApp) {
                            correlationEngine.correlateSmartScan(pm, pkg)
                        } else {
                            correlationEngine.correlateDeepScan(pm, pkg, config)
                        }

                        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try { pm.getInstallSourceInfo(pkg.packageName).installingPackageName } catch (_: Exception) { null }
                        } else {
                            @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg.packageName)
                        }
                        val isSideloaded = if (isSystemApp) false else (installer == null || !safeInstallSources.contains(installer))

                        val evidence = AppEvidence(
                            packageName = pkg.packageName,
                            appLabel = appName,
                            isSystemApp = isSystemApp,
                            isSideloaded = isSideloaded,
                            correlationVerdict = correlationVerdict
                        )

                        // Centralized verdict logic
                        val engineVerdict = ProcMalwareEngine.evaluateApp(evidence)

                        if (engineVerdict.decision.isActionable) {
                            addThreat(
                                appName, pkg.packageName,
                                engineVerdict.decision.displayName,
                                "${engineVerdict.finalScore}/10.0",
                                engineVerdict.explanation
                            )
                        }
                    } catch (_: Exception) {}

                    scannedCount++
                }
                if (scannedCount % 10 == 0) {
                    kotlinx.coroutines.delay(config.scanDelayMs.toLong())
                }
                yield()
            }
        } catch (_: Exception) {}
    }

    private suspend fun runFullFileSystemScan(totalItems: Int, config: AdaptiveScanConfig) {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager.storageVolumes.mapNotNull { it.directory }
        } else {
            listOf(Environment.getExternalStorageDirectory())
        }

        for (volume in volumes) {
            if (isCancelled) break
            crawlAndScanAll(volume, totalItems, config)
        }
    }

    private suspend fun crawlAndScanAll(root: File, total: Int, config: AdaptiveScanConfig) {
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.add(root to 0)
        val batchSize = config.scanBatchSize.coerceAtLeast(1)
        val maxDepth = config.maxFileSystemDepth.coerceAtLeast(0)
        val maxDurationMs = config.maxScanDurationMinutes.coerceAtLeast(1) * 60L * 1000L
        val scanParallelism = config.maxConcurrentScans.coerceAtLeast(1)
        val scanSemaphore = Semaphore(scanParallelism)

        while (queue.isNotEmpty() && !isCancelled) {
            if (System.currentTimeMillis() - scanStartTime > maxDurationMs) {
                isCancelled = true
                break
            }
            checkPauseStatus()
            val (currentDir, depth) = queue.removeFirst()
            val entries = try { currentDir.listFiles() } catch (_: Exception) { null } ?: continue

            val filesToScan = mutableListOf<File>()
            for (entry in entries) {
                if (entry.isDirectory) {
                    val dirName = entry.name
                    if (depth < maxDepth && dirName != "." && dirName != ".." && !dirName.equals("Android", ignoreCase = true)) {
                        queue.add(entry to depth + 1)
                    }
                } else if (entry.isFile && entry.canRead() && entry.length() > 0) {
                    filesToScan.add(entry)
                }
            }

            if (filesToScan.isNotEmpty()) {
                filesToScan.chunked(batchSize).forEach { batch ->
                    if (isCancelled) return
                    coroutineScope {
                        batch.map { file ->
                            async {
                                scanSemaphore.withPermit {
                                    scanSingleFile(file, total)
                                }
                            }
                        }.awaitAll()
                    }
                    scannedCount += batch.size
                    kotlinx.coroutines.delay(config.scanDelayMs.toLong())
                }
            }
        }
    }

    private suspend fun scanSingleFile(file: File, total: Int) {
        updateStateAndNotification(scannedCount, total, file.absolutePath)

        try {
            val evidence = FileFeatureExtractor.extractEvidence(this@DeepScannerService, file)
            if (evidence != null) {
                // Submit evidence to centralized decision engine
                val verdict = ProcMalwareEngine.evaluateFile(evidence)

                // Act on Engine Verdict only
                if (verdict.decision.isActionable) {
                    addThreat(
                        file.name, file.absolutePath, 
                        verdict.decision.displayName, 
                        "${verdict.finalScore}/10.0",
                        verdict.explanation
                    )
                }
            }
        } catch (_: Exception) {}
    }

    // Extraction helpers moved to FileFeatureExtractor

    private fun addThreat(appName: String, packageName: String, threatType: String, riskScore: String, reason: String) {
        synchronized(scanThreats) {
            scanThreats.add(ScanThreat(appName, packageName, threatType, riskScore, reason))
        }
    }

    private suspend fun checkPauseStatus() {
        while (globalIsPaused.value) {
            if (isCancelled) return
            delay(200)
        }
    }

    private fun updateStateAndNotification(current: Int, total: Int, itemName: String) {
        val progress = if (total > 0) (current.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f) else 0f
        globalScanState.value = DeepScanState.Scanning(progress, itemName, scanThreats.size)

        if (current % 50 == 0) {
            val shortName = if (itemName.length > 30) "..." + itemName.takeLast(27) else itemName
            val notification = buildNotification("Scanning: $shortName")
                .setProgress(100, progress.toInt(), false)
                .build()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Deep Scan Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent execution state for scanning millions of files offline."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): NotificationCompat.Builder {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Protctus Extreme Deep Scan")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_search_category_default)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Protctus::DeepScannerEngineWakeLock"
        ).apply {
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isCancelled = true
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }
}
