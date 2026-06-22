package com.protctus.app.engine.correlation

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

/**
 * Runtime device capability analyzer for adaptive scan behavior.
 *
 * Determines which scan features are safe to run on the current device
 * based on real hardware measurements — NOT hardcoded device lists.
 *
 * Measurements:
 *   - Available RAM (ActivityManager.MemoryInfo)
 *   - CPU core count (Runtime.availableProcessors)
 *   - CPU max frequency (/sys/devices/system/cpu/)
 *   - Storage availability (StatFs)
 *   - Battery level (BatteryManager)
 *   - Android version
 */
class DeviceCapabilityAnalyzer(private val context: Context) {

    /**
     * Analyze current device capabilities and return a profile.
     */
    fun analyze(): DeviceProfile {
        val memInfo = getMemoryInfo()
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuMaxFreqMhz = getCpuMaxFrequency()
        val availableStorageMb = getAvailableStorageMb()
        val batteryPct = getBatteryLevel()
        val isLowRamDevice = isLowRamDevice()

        val tier = calculateDeviceTier(
            totalRamMb = memInfo.totalRamMb,
            availableRamMb = memInfo.availableRamMb,
            cpuCores = cpuCores,
            cpuMaxFreqMhz = cpuMaxFreqMhz,
            isLowRam = isLowRamDevice
        )

        val scanConfig = buildScanConfig(tier, memInfo, batteryPct, availableStorageMb)

        Log.i(TAG, "Device: tier=$tier, RAM=${memInfo.totalRamMb}MB (${memInfo.availableRamMb}MB free), " +
                "CPU=${cpuCores}x${cpuMaxFreqMhz}MHz, battery=$batteryPct%, storage=${availableStorageMb}MB")

        return DeviceProfile(
            tier = tier,
            totalRamMb = memInfo.totalRamMb,
            availableRamMb = memInfo.availableRamMb,
            cpuCores = cpuCores,
            cpuMaxFreqMhz = cpuMaxFreqMhz,
            availableStorageMb = availableStorageMb,
            batteryPercent = batteryPct,
            androidVersion = Build.VERSION.SDK_INT,
            isLowRamDevice = isLowRamDevice,
            cpuArchitecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            scanConfig = scanConfig
        )
    }

    private fun getMemoryInfo(): MemInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return MemInfo(
            totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt(),
            availableRamMb = (memInfo.availMem / (1024 * 1024)).toInt(),
            isLowMemory = memInfo.lowMemory,
            threshold = (memInfo.threshold / (1024 * 1024)).toInt()
        )
    }

    private fun getCpuMaxFrequency(): Int {
        return try {
            val path = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
            val file = File(path)
            if (file.exists()) {
                (file.readText().trim().toLongOrNull() ?: 0L).toInt() / 1000 // KHz → MHz
            } else 0
        } catch (e: Exception) { 0 }
    }

    private fun getAvailableStorageMb(): Long {
        return try {
            val stat = StatFs(context.cacheDir.absolutePath)
            stat.availableBytes / (1024 * 1024)
        } catch (e: Exception) { 0L }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { 100 }
    }

    private fun isLowRamDevice(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.isLowRamDevice
    }

    /**
     * Calculate device tier based on real hardware measurements.
     * NOT based on device model names — purely on measured capabilities.
     */
    private fun calculateDeviceTier(
        totalRamMb: Int, availableRamMb: Int,
        cpuCores: Int, cpuMaxFreqMhz: Int,
        isLowRam: Boolean
    ): DeviceTier {
        if (isLowRam || totalRamMb < 2048) return DeviceTier.LOW
        if (totalRamMb < 3072 || cpuCores < 4) return DeviceTier.LOW

        var score = 0
        // RAM scoring
        score += when {
            totalRamMb >= 8192 -> 3
            totalRamMb >= 6144 -> 2
            totalRamMb >= 4096 -> 1
            else -> 0
        }
        // CPU scoring
        score += when {
            cpuCores >= 8 && cpuMaxFreqMhz >= 2400 -> 3
            cpuCores >= 6 && cpuMaxFreqMhz >= 1800 -> 2
            cpuCores >= 4 -> 1
            else -> 0
        }
        // Available RAM pressure
        if (availableRamMb < 512) score -= 2
        else if (availableRamMb < 1024) score -= 1

        return when {
            score >= 5 -> DeviceTier.HIGH
            score >= 3 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    /**
     * Build adaptive scan configuration based on device tier,
     * current memory pressure, battery level, and storage.
     */
    private fun buildScanConfig(
        tier: DeviceTier, memInfo: MemInfo,
        batteryPct: Int, storageMb: Long
    ): AdaptiveScanConfig {
        val isMemoryPressure = memInfo.isLowMemory || memInfo.availableRamMb < 300
        val isLowBattery = batteryPct < 15
        val isCriticalBattery = batteryPct < 5

        // Base config per tier
        val base = when (tier) {
            DeviceTier.HIGH -> AdaptiveScanConfig(
                maxConcurrentScans = 8,
                dexStringPoolLimit = 50_000,
                maxPatternScanBytes = 15L * 1024 * 1024,
                enableDeepDexInspection = true,
                enableBytePatternScan = true,
                enableApkStructureAnalysis = true,
                scanBatchSize = 12,
                scanDelayMs = 3,
                maxScanDurationMinutes = 60,
                maxFileSystemDepth = 15,
                enableQuarkDeepAnalysis = true,
                skipSystemApps = false
            )
            DeviceTier.MID -> AdaptiveScanConfig(
                maxConcurrentScans = 4,
                dexStringPoolLimit = 25_000,
                maxPatternScanBytes = 8L * 1024 * 1024,
                enableDeepDexInspection = true,
                enableBytePatternScan = true,
                enableApkStructureAnalysis = true,
                scanBatchSize = 6,
                scanDelayMs = 8,
                maxScanDurationMinutes = 45,
                maxFileSystemDepth = 10,
                enableQuarkDeepAnalysis = true,
                skipSystemApps = true
            )
            DeviceTier.LOW -> AdaptiveScanConfig(
                maxConcurrentScans = 2,
                dexStringPoolLimit = 10_000,
                maxPatternScanBytes = 3L * 1024 * 1024,
                enableDeepDexInspection = false,
                enableBytePatternScan = false,
                enableApkStructureAnalysis = false,
                scanBatchSize = 3,
                scanDelayMs = 20,
                maxScanDurationMinutes = 30,
                maxFileSystemDepth = 5,
                enableQuarkDeepAnalysis = false,
                skipSystemApps = true
            )
        }

        // Dynamic overrides for current conditions
        return if (isMemoryPressure || isCriticalBattery) {
            base.copy(
                maxConcurrentScans = 1,
                dexStringPoolLimit = 5_000,
                enableDeepDexInspection = false,
                enableBytePatternScan = false,
                enableApkStructureAnalysis = false,
                scanBatchSize = 2,
                scanDelayMs = 50,
                enableQuarkDeepAnalysis = false
            )
        } else if (isLowBattery) {
            base.copy(
                maxConcurrentScans = (base.maxConcurrentScans / 2).coerceAtLeast(1),
                scanDelayMs = base.scanDelayMs * 2,
                enableDeepDexInspection = false,
                enableQuarkDeepAnalysis = false
            )
        } else {
            base
        }
    }

    companion object {
        private const val TAG = "DeviceCapability"
    }
}

data class MemInfo(val totalRamMb: Int, val availableRamMb: Int, val isLowMemory: Boolean, val threshold: Int)

enum class DeviceTier { LOW, MID, HIGH }

data class DeviceProfile(
    val tier: DeviceTier,
    val totalRamMb: Int,
    val availableRamMb: Int,
    val cpuCores: Int,
    val cpuMaxFreqMhz: Int,
    val availableStorageMb: Long,
    val batteryPercent: Int,
    val androidVersion: Int,
    val isLowRamDevice: Boolean,
    val cpuArchitecture: String,
    val scanConfig: AdaptiveScanConfig
)

data class AdaptiveScanConfig(
    val maxConcurrentScans: Int,
    val dexStringPoolLimit: Int,
    val maxPatternScanBytes: Long,
    val enableDeepDexInspection: Boolean,
    val enableBytePatternScan: Boolean,
    val enableApkStructureAnalysis: Boolean,
    val scanBatchSize: Int,
    val scanDelayMs: Int,
    val maxScanDurationMinutes: Int,
    val maxFileSystemDepth: Int,
    val enableQuarkDeepAnalysis: Boolean,
    val skipSystemApps: Boolean
)
