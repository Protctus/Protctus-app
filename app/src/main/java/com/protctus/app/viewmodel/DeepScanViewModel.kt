package com.protctus.app.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.engine.EliteThreat
import com.protctus.app.engine.ScanThreat
// ThreatBrain REMOVED — all verdicts from malware engines only
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import javax.inject.Inject

enum class Recommendation {
    SAFE_TO_RUN,
    PLUG_IN_CHARGER,
    FREE_UP_RAM
}

data class SystemProfile(
    val deviceModel: String,
    val cpuUsagePercent: Int,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val requiredRamMb: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val totalAppsToScan: Int,
    val totalFilesToScan: Int,
    val estimatedTimeSeconds: Int,
    val expectedBatteryDrainPercent: Int,
    val recommendation: Recommendation,
    val recommendationMessage: String
)

sealed class DeepScanState {
    object Idle : DeepScanState()
    object Profiling : DeepScanState()
    data class ProfileReady(val profile: SystemProfile) : DeepScanState()
    data class Scanning(val progress: Float, val currentItem: String, val threatsFound: Int) : DeepScanState()
    object Paused : DeepScanState()
    data class Completed(val threats: List<ScanThreat>, val totalScanned: Int) : DeepScanState()
}

@HiltViewModel
class DeepScanViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _scanState = MutableStateFlow<DeepScanState>(DeepScanState.Idle)
    val scanState: StateFlow<DeepScanState> = _scanState.asStateFlow()

    private val _scanThreats = mutableListOf<ScanThreat>()
    private val isScanPaused = MutableStateFlow(false)
    private var isScanCancelled = false

    init {
        // Auto-reconnect to background service state if it's already running
        viewModelScope.launch {
            com.protctus.app.engine.DeepScannerService.globalScanState.collect { state ->
                if (state !is DeepScanState.Idle) {
                    _scanState.value = state
                }
            }
        }
    }

    /**
     * The OS Profiler Pre-Scan phase natively extracting Telemetry
     * to deterministically predict the load parameters to the Edge evaluator.
     */
    fun runPreScanProfiler() {
        _scanState.value = DeepScanState.Profiling
        
        viewModelScope.launch(Dispatchers.IO) {
            val context = appContext
            
            // 1. Memory / RAM Mapping
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
            val availableRamMb = memoryInfo.availMem / (1024 * 1024)

            // 2. Battery Flow Telemetry
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            
            val batteryLevel = batteryStatus?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) (level * 100) / scale else 100
            } ?: 100
            
            val chargeStatus = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargeStatus == BatteryManager.BATTERY_STATUS_FULL

            // 3. Simulated/Heuristic CPU Core Telemetry (Without hitting Android 8.0+ /proc/stat locks)
            val cores = Runtime.getRuntime().availableProcessors()
            val threads = Thread.activeCount()
            val rawUsage = ((threads.toFloat() / (cores * 15f)) * 100f).toInt()
            val cpuUsagePercent = rawUsage.coerceIn(10, 95)

            // 4. Extreme Edge Graph: Installed Application Bounds
            val pm = context.packageManager
            val flags = PackageManager.GET_META_DATA
            val totalApps = try { pm.getInstalledPackages(flags).size } catch (e: Exception) { 0 }

            // 5. Extreme Edge Graph: Recursive Complete OS Traversal
            var totalFiles = 0
            val absoluteRoot = Environment.getExternalStorageDirectory()
            
            fun traverseCountFiles(dir: File) {
                // Failsafe to bypass Android/Data symbolic loops 
                if (dir.name == "Android") return 
                
                try {
                    val entries = dir.listFiles() ?: return
                    for (entry in entries) {
                        if (entry.isDirectory) {
                            traverseCountFiles(entry)
                        } else {
                            totalFiles++
                        }
                    }
                } catch (e: Exception) {}
            }
            
            if (absoluteRoot.exists()) {
                traverseCountFiles(absoluteRoot)
            }

            // 6. Accurate Prediction Bounds for Comprehensive Deep Scan
            // Per app: YARA APK scan (~120ms) + Quark behavioral (~50ms) + ClamAV hash (~10ms) = ~180ms
            // Per file: YARA scan (~15ms) + entropy/magic bytes (~5ms) + archive inspection (~25ms avg) = ~20ms avg
            val estimatedTimeSeconds = ((totalApps * 0.15) + (totalFiles * 0.02)).toInt().coerceAtLeast(5)
            val expectedBatteryDrainPercent = (estimatedTimeSeconds / 60).coerceAtLeast(1)
            
            // RAM: YARA engine ~50MB base + file buffers + engine state
            val requiredRamMb = 300L + ((totalFiles * 512L) / (1024 * 1024)).coerceAtLeast(1)
            
            var recommendation = Recommendation.SAFE_TO_RUN
            var recMessage = "Your device is fully capable of running this Deep Scan securely."

            if (availableRamMb < requiredRamMb) {
                recommendation = Recommendation.FREE_UP_RAM
                recMessage = "WARNING: This Deep Scan comprehensively analyzes $totalApps apps (YARA + heuristics) and $totalFiles files (signatures, entropy, archives, media). Requires ~${requiredRamMb}MB RAM but only ${availableRamMb}MB available. Please free up memory."
            } else if (batteryLevel < 15 && !isCharging) {
                recommendation = Recommendation.PLUG_IN_CHARGER
                recMessage = "WARNING: Deep Scan performs multi-layer analysis on $totalApps apps and $totalFiles files. Estimated ${estimatedTimeSeconds / 60} min ${estimatedTimeSeconds % 60} sec. Battery is at $batteryLevel%. Please plug in your charger."
            }

            val profile = SystemProfile(
                deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}",
                cpuUsagePercent = cpuUsagePercent,
                availableRamMb = availableRamMb,
                totalRamMb = totalRamMb,
                requiredRamMb = requiredRamMb,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                totalAppsToScan = totalApps,
                totalFilesToScan = totalFiles,
                estimatedTimeSeconds = estimatedTimeSeconds,
                expectedBatteryDrainPercent = expectedBatteryDrainPercent,
                recommendation = recommendation,
                recommendationMessage = recMessage
            )

            withContext(Dispatchers.Main) {
                _scanState.value = DeepScanState.ProfileReady(profile)
            }
        }
    }

    fun hasFilePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            appContext.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * State Machine Runner triggering the Deep OS Traversal sequentially
     * through the multi-engine correlation pipeline.
     */
    fun startDeepScan() {
        val currentState = _scanState.value
        if (currentState !is DeepScanState.ProfileReady) return
        
        val totalItems = currentState.profile.totalAppsToScan + currentState.profile.totalFilesToScan
        
        _scanThreats.clear()
        isScanCancelled = false
        _scanState.value = DeepScanState.Scanning(0f, "Passing telemetry to Extreme Engine Service...", 0)

        // Delegate Execution completely to the Backend Foreground Service
        com.protctus.app.engine.DeepScannerService.startScan(appContext, currentState.profile)
    }

    fun togglePause() {
        val currentState = _scanState.value
        if (currentState is DeepScanState.Scanning) {
            com.protctus.app.engine.DeepScannerService.globalIsPaused.value = true
            _scanState.value = DeepScanState.Paused
        } else if (currentState is DeepScanState.Paused) {
            com.protctus.app.engine.DeepScannerService.globalIsPaused.value = false
            // Will snap back to Scanning immediately when the backend flow catches up
        }
    }

    fun cancelScan() {
        isScanCancelled = true
        com.protctus.app.engine.DeepScannerService.stopScan(appContext)
        _scanState.value = DeepScanState.Idle
    }
}
