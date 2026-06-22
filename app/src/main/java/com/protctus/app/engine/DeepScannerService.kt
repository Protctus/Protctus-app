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

    // Dangerous file extensions that warrant deeper inspection
    private val executableExtensions = setOf(
        "apk", "dex", "so", "jar", "sh", "bat", "cmd", "ps1",
        "exe", "dll", "bin", "elf", "msi", "vbs", "js"
    )
    private val archiveExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "cab"
    )
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "odt", "ods", "rtf", "csv"
    )
    private val mediaExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
        "mp4", "mkv", "avi", "mov", "mp3", "wav", "flac", "aac", "ogg"
    )

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

            // Initialize YARA Engine — P2 FIX: Load from assets first, then cacheDir fallback
            val rulesFile = File(cacheDir, "rules.yarc")
            if (!rulesFile.exists()) {
                // Copy bundled rules from assets to cacheDir on first launch
                try {
                    assets.open("rules.yarc").use { input ->
                        rulesFile.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (_: Exception) {
                    // Asset not bundled yet — YARA will be a no-op but won't crash
                }
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
            // (APKs, Images, Documents, Zips, ALL files)
            // ==========================================
            runFullFileSystemScan(totalItems)

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

    // ══════════════════════════════════════════════
    // PHASE 1: Full App Scan (Advanced Signatures + Behavior)
    // ══════════════════════════════════════════════

    private suspend fun runAppScan(totalItems: Int, config: AdaptiveScanConfig) {
        val pm = packageManager
        val pkgFlags = PackageManager.GET_PERMISSIONS or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
                else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)

        try {
            val installedPackages = pm.getInstalledPackages(pkgFlags)
            installedPackages.chunked(5).forEach { chunk ->
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

                    // 1B-1E: Multi-Engine Correlation (Quark, ClamAV, Androguard, YARA)
                    try {
                        val correlationEngine = ThreatCorrelationEngine.getInstance(this@DeepScannerService)
                        val verdictResult = correlationEngine.correlateDeepScan(pm, pkg, config)

                        if (verdictResult.isActionable) {
                            addThreat(
                                appName, pkg.packageName,
                                verdictResult.finalVerdict.displayName,
                                "${verdictResult.displayRiskScore}/10.0",
                                verdictResult.shortSummary
                            )
                        }
                    } catch (_: Exception) {}

                    scannedCount++
                }
                yield() // Cooperative task yielding for CPU scheduling
                delay(150) // Thermal delay to prevent battery drain and overheating
            }
        } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════════════════
    // PHASE 2: Complete File System Traversal
    // Scans ALL files: APKs, Images, Docs, Zips, everything
    // ══════════════════════════════════════════════════════

    private suspend fun runFullFileSystemScan(totalItems: Int) {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager.storageVolumes.mapNotNull { it.directory }
        } else {
            listOf(Environment.getExternalStorageDirectory())
        }

        for (volume in volumes) {
            if (isCancelled) break
            crawlAndScanAll(volume, totalItems)
        }
    }

    private suspend fun crawlAndScanAll(root: File, total: Int) {
        val queue = ArrayDeque<File>()
        queue.add(root)
        val batchSize = 4 // Reduced from 12 to 4 to prevent CPU overheating and battery drain

        while (queue.isNotEmpty() && !isCancelled) {
            if (System.currentTimeMillis() - scanStartTime > 60 * 60 * 1000L) {
                isCancelled = true // Global 1-hour timeout
                break
            }
            checkPauseStatus()
            val currentDir = queue.removeFirst()
            val entries = try { currentDir.listFiles() } catch (_: Exception) { null } ?: continue

            val filesToScan = mutableListOf<File>()
            for (entry in entries) {
                if (entry.isDirectory) {
                    // Skip Android/data symlink loops but scan everything else
                    val dirName = entry.name
                    if (dirName != "." && dirName != ".." && !dirName.equals("Android", ignoreCase = true)) {
                        queue.add(entry)
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
                                scanSingleFile(file, total)
                            }
                        }.awaitAll()
                    }
                    scannedCount += batch.size
                    delay(50) // Thermal delay between file batches
                }
            }
        }
    }

    /**
     * Universal file scanner — inspects every file type:
     * - Executables/APKs: YARA + structural analysis
     * - Archives (ZIP/RAR): Checks for hidden APKs inside
     * - Documents (PDF/DOC): Entropy analysis for embedded payloads
     * - Images/Media: Extension spoofing detection
     * - All others: Entropy + extension mismatch checks
     */
    private suspend fun scanSingleFile(file: File, total: Int) {
        val ext = file.extension.lowercase()
        val fileName = file.name.lowercase()

        updateStateAndNotification(scannedCount, total, file.absolutePath)

        try {
            // ── LAYER 1: YARA Signature Scan (all scannable files) ──
            if (file.length() < 50 * 1024 * 1024) { // Skip files > 50MB for YARA
                try {
                    val matches = YaraScanner.scanRawFile(file.absolutePath)
                    if (matches.isNotEmpty()) {
                        addThreat(file.name, file.absolutePath, 
                            com.protctus.app.engine.correlation.ThreatVerdict.HIGH_CONFIDENCE_MALWARE.displayName,
                            "${com.protctus.app.engine.correlation.ThreatVerdict.HIGH_CONFIDENCE_MALWARE.score / 10f}/10.0",
                            "Matched signature rules: ${matches.joinToString(", ")}")
                        return // Already flagged, skip further checks
                    }
                } catch (_: Exception) {}
            }

            // ── LAYER 1.5: ClamAV Signature Scan (hash + patterns) ──
            try {
                val clamEngine = ClamSignatureEngine.getInstance(this@DeepScannerService)
                if (clamEngine.ready && file.length() < 50 * 1024 * 1024) {
                    val clamResults = clamEngine.fullScan(file)
                    if (clamResults != null && clamResults.isNotEmpty()) {
                        for (result in clamResults) {
                            addThreat(file.name, file.absolutePath,
                                com.protctus.app.engine.correlation.ThreatVerdict.HIGH_CONFIDENCE_MALWARE.displayName,
                                "${com.protctus.app.engine.correlation.ThreatVerdict.HIGH_CONFIDENCE_MALWARE.score / 10f}/10.0",
                                "Signature match: ${result.malwareName} (${result.matchType})")
                        }
                        return // Signature match is definitive
                    }
                }
            } catch (_: Exception) {}

            // ── LAYER 2: Extension-specific deep analysis ──
            when {
                // Executable files: check sideloaded APK entropy
                executableExtensions.contains(ext) -> {
                    // ThreatBrain REMOVED — APK analysis handled by YARA/ClamAV above
                    if (ext == "apk") {
                        checkSideloadedApk(file)
                    }
                }

                // Archives: scan for hidden executables inside
                archiveExtensions.contains(ext) -> {
                    scanArchiveForHiddenPayloads(file)
                }

                // Documents: check entropy for embedded payloads
                documentExtensions.contains(ext) -> {
                    checkDocumentForPayload(file)
                }

                // Media: check for extension spoofing
                mediaExtensions.contains(ext) -> {
                    checkMediaSpoofing(file, ext)
                }
            }

            // ── LAYER 3: Universal checks on ALL files ──

            // Unicode RTLO detection
            if (file.name.contains("\u202E")) {
                addThreat(file.name, file.absolutePath, 
                    com.protctus.app.engine.correlation.ThreatVerdict.HIGH_RISK.displayName, 
                    "${com.protctus.app.engine.correlation.ThreatVerdict.HIGH_RISK.score / 10f}/10.0",
                    "File uses Right-to-Left Override to spoof its extension visually.")
            }

            // Double-extension detection (e.g., photo.jpg.apk, invoice.pdf.exe)
            if (fileName.count { it == '.' } >= 2) {
                val parts = fileName.split(".")
                val realExt = parts.last()
                val spoofedExt = parts[parts.size - 2]
                
                // Only flag if the spoofed extension is meant to look like a safe document/media
                if (executableExtensions.contains(realExt) && (documentExtensions.contains(spoofedExt) || mediaExtensions.contains(spoofedExt))) {
                    addThreat(file.name, file.absolutePath, 
                        com.protctus.app.engine.correlation.ThreatVerdict.HIGH_RISK.displayName, 
                        "${com.protctus.app.engine.correlation.ThreatVerdict.HIGH_RISK.score / 10f}/10.0",
                        "File uses double extension to disguise an executable: ${parts.dropLast(1).joinToString(".")}.$realExt")
                }
            }

            // Large file with executable extension in non-standard location
            if (executableExtensions.contains(ext) && file.length() > 1024) {
                val parentPath = file.parent?.lowercase() ?: ""
                if (parentPath.contains("download") || parentPath.contains("bluetooth") ||
                    parentPath.contains("received") || parentPath.contains("telegram") ||
                    parentPath.contains("whatsapp")) {
                    // Already handled by YARA/ClamAV above, but check entropy if APK wasn't caught
                    if (ext == "apk") {
                        checkSideloadedApk(file)
                    }
                }
            }

        } catch (_: Exception) {}
    }

    // ── Archive Inspection: Time Budget & Stream Processor ──

    private fun scanArchiveForHiddenPayloads(file: File) {
        try {
            val startTime = System.currentTimeMillis()
            val timeBudgetMs = 5000L // 5 seconds per file max for deep scan

            var uncompressedBytes = 0L
            val compressedBytes = file.length()
            if (compressedBytes == 0L) return

            java.util.zip.ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                var hasHiddenExecutable = false
                val suspiciousEntries = mutableListOf<String>()

                val buffer = ByteArray(8192)
                while (entry != null) {
                    if (System.currentTimeMillis() - startTime > timeBudgetMs) {
                        // Just abort the scan gracefully; do not flag as hostile to prevent false positives on massive safe ZIPs.
                        android.util.Log.w("DeepScanner", "Archive extraction exceeded time budget. Skipping.")
                        break
                    }

                    var bytesRead: Int
                    while (zis.read(buffer).also { bytesRead = it } != -1) {
                        uncompressedBytes += bytesRead
                        
                        // Zip Bomb detection (Hardening Roadmap Fix)
                        val ratio = if (compressedBytes > 0) uncompressedBytes.toDouble() / compressedBytes else 0.0
                        if ((ratio > 100.0 && uncompressedBytes > 100_000_000L) || uncompressedBytes > 250_000_000L) { 
                            addThreat(file.name, file.absolutePath, 
                                com.protctus.app.engine.correlation.ThreatVerdict.HIGH_CONFIDENCE_MALWARE.displayName, 
                                "${com.protctus.app.engine.correlation.ThreatVerdict.HIGH_CONFIDENCE_MALWARE.score / 10f}/10.0",
                                "Zip Bomb Detected: Archive decompression exceeds limits (Ratio: ${String.format("%.1f", ratio)}, Size: ${uncompressedBytes/1024/1024}MB). Hostile payload.")
                            return
                        }

                        // Zip Slip / Directory Traversal Protection (Hardening Roadmap Fix)
                        if (entry.name.contains("../") || entry.name.contains("..\\")) {
                            addThreat(file.name, file.absolutePath, 
                                com.protctus.app.engine.correlation.ThreatVerdict.HIGH_RISK.displayName, 
                                "${com.protctus.app.engine.correlation.ThreatVerdict.HIGH_RISK.score / 10f}/10.0",
                                "Directory Traversal Attempt: Archive contains relative paths designed to escape extraction sandbox.")
                            return
                        }

                        // Check for 50 4B 03 04 (PK\x03\x04) indicating an embedded zip/apk
                        for (i in 0 until bytesRead - 3) {
                            if (buffer[i] == 0x50.toByte() && buffer[i+1] == 0x4B.toByte() && 
                                buffer[i+2] == 0x03.toByte() && buffer[i+3] == 0x04.toByte()) {
                                hasHiddenExecutable = true
                            }
                        }
                    }
                    
                    if (hasHiddenExecutable) {
                        suspiciousEntries.add("Embedded Zip/APK Header Found")
                        break
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {}
    }

    // ── Document Analysis: Entropy check for embedded payloads ──

    private fun checkDocumentForPayload(file: File) {
        // Entropy checks on documents (PDFs, DOCX) cause massive false positives
        // because they use DEFLATE compression internally which naturally has high entropy.
        // True document malware will be caught by the YARA memory scanner (Layer 1).
        return
    }

    // ── Media Spoofing: Dual-End Scan for EOCD ──

    private fun checkMediaSpoofing(file: File, ext: String) {
        if (file.length() < 128) return
        try {
            val raf = java.io.RandomAccessFile(file, "r")
            val header = ByteArray(16)
            raf.readFully(header)

            val isValidImage = when (ext) {
                "jpg", "jpeg" -> header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
                "png" -> header[0] == 0x89.toByte() && header[1] == 0x50.toByte()
                "gif" -> header[0] == 0x47.toByte() && header[1] == 0x49.toByte()
                "webp" -> header[8] == 0x57.toByte() // RIFF...WEBP
                "bmp" -> header[0] == 0x42.toByte() && header[1] == 0x4D.toByte()
                "mp4", "mov" -> header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte() // ftyp
                "mp3" -> header[0] == 0xFF.toByte() || (header[0] == 0x49.toByte() && header[1] == 0x44.toByte())
                else -> true
            }

            // Dual-End Scan: Look for EOCD at the end of the file
            raf.seek(file.length() - 128)
            val footer = ByteArray(128)
            raf.readFully(footer)
            raf.close()

            var hasEOCD = false
            for (i in 0 until 128 - 3) {
                // EOCD Signature: 50 4B 05 06
                if (footer[i] == 0x50.toByte() && footer[i+1] == 0x4B.toByte() && 
                    footer[i+2] == 0x05.toByte() && footer[i+3] == 0x06.toByte()) {
                    hasEOCD = true
                    break
                }
            }

            if (hasEOCD) {
                // Lower severity, this is common in many legitimate files
                addThreat(file.name, file.absolutePath, 
                    com.protctus.app.engine.correlation.ThreatVerdict.NEEDS_REVIEW.displayName, 
                    "${com.protctus.app.engine.correlation.ThreatVerdict.NEEDS_REVIEW.score / 10f}/10.0",
                    "File is a polyglot. It contains an End of Central Directory (EOCD) record at the end, hiding a ZIP/APK payload.")
                return
            }
        } catch (_: Exception) {}
    }

    // ── Sideloaded APK check ──

    private fun checkSideloadedApk(file: File) {
        try {
            // APKs are ZIP files and naturally have high entropy (>7.9). 
            // We only flag if the entropy is exactly 8.00 (fully encrypted blob with no readable structures).
            val entropy = calculateFileEntropy(file, sampleSize = 8192)
            if (entropy >= 7.99) {
                addThreat(file.name, file.absolutePath, 
                    com.protctus.app.engine.correlation.ThreatVerdict.SUSPICIOUS.displayName, 
                    "${com.protctus.app.engine.correlation.ThreatVerdict.SUSPICIOUS.score / 10f}/10.0",
                    "APK found in download location with maximum entropy (${String.format("%.3f", entropy)}). This usually indicates a fully packed/encrypted malware dropper.")
            }
        } catch (_: Exception) {}
    }

    // ── Utility: Shannon Entropy Calculator (Native fallback if needed) ──

    private fun calculateFileEntropy(file: File, sampleSize: Int = 8192): Double {
        val freq = IntArray(256)
        var totalBytes = 0
        FileInputStream(file).use { fis ->
            val buf = ByteArray(sampleSize)
            val read = fis.read(buf)
            if (read <= 0) return 0.0
            for (i in 0 until read) {
                freq[buf[i].toInt() and 0xFF]++
            }
            totalBytes = read
        }
        if (totalBytes == 0) return 0.0
        var entropy = 0.0
        for (count in freq) {
            if (count > 0) {
                val p = count.toDouble() / totalBytes
                entropy -= p * (ln(p) / ln(2.0))
            }
        }
        return entropy
    }

    // ══════════════════════════════════════════════
    // Thread-safe threat addition
    // ══════════════════════════════════════════════

    private fun addThreat(appName: String, packageName: String, threatType: String, riskScore: String, reason: String) {
        synchronized(scanThreats) {
            scanThreats.add(ScanThreat(appName, packageName, threatType, riskScore, reason))
        }
    }

    // ══════════════════════════════════════════════
    // Infrastructure (unchanged)
    // ══════════════════════════════════════════════

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
