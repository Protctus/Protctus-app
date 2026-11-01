package com.protctus.yaracore

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * YaraScanner — Kotlin wrapper for the native YARA scanning engine.
 *
 * NOTE: The bundled yara-src/libyara/ native C sources are absent from this
 * repository, so the native `yara-core` library cannot be compiled. This
 * class therefore degrades gracefully:
 *   - It attempts to load `yara-core` in its init block.
 *   - If the library is unavailable, every public scan method returns an
 *     empty result and logs a warning. No detection is claimed, no fake
 *     matches are produced, and the caller's workflow is not interrupted.
 *
 * This is a fail-safe degradation, not mock detection: an empty result
 * means "YARA native engine unavailable — no match data", not "clean".
 */
object YaraScanner {

    private const val TAG = "YaraScanner"

    // Strict Memory Threshold (25 MB)
    private const val MAX_IN_MEMORY_SIZE = 25 * 1024 * 1024L

    /** Whether the native yara-core library was successfully loaded. */
    @Volatile
    private var nativeAvailable = false

    init {
        try {
            System.loadLibrary("yara-core")
            nativeAvailable = true
            Log.i(TAG, "Native yara-core library loaded.")
        } catch (e: UnsatisfiedLinkError) {
            nativeAvailable = false
            Log.w(TAG, "Native yara-core library not available — YARA scanning degraded (empty results).", e)
        } catch (e: Exception) {
            nativeAvailable = false
            Log.w(TAG, "Failed to initialize YaraScanner — YARA scanning degraded (empty results).", e)
        }
    }

    /** Returns true if the native YARA engine is available for scanning. */
    fun isNativeAvailable(): Boolean = nativeAvailable

    private external fun initYaraEngine(compiledRulesPath: String): Boolean
    private external fun destroyYaraEngine()
    private external fun scanMemory(buffer: ByteArray, size: Int): Array<String>
    private external fun scanFile(filePath: String): Array<String>

    private fun requireNative(): Boolean {
        if (!nativeAvailable) {
            Log.w(TAG, "YARA native engine unavailable; returning empty scan result.")
        }
        return nativeAvailable
    }

    suspend fun scanRawFile(filePath: String): List<String> = withContext(Dispatchers.IO) {
        if (!requireNative()) return@withContext emptyList()
        try {
            return@withContext scanFile(filePath).toList()
        } catch (e: UnsatisfiedLinkError) {
            nativeAvailable = false
            Log.w(TAG, "Native scan failed — YARA engine unavailable.", e)
            return@withContext emptyList()
        }
    }

    suspend fun scanApkInMemory(context: Context, apkPath: String): List<String> = withContext(Dispatchers.IO) {
        if (!requireNative()) return@withContext emptyList()
        val matches = mutableSetOf<String>()
        try {
            ZipFile(apkPath).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    if (entry.name.startsWith("classes") && entry.name.endsWith(".dex") ||
                        entry.name == "AndroidManifest.xml" ||
                        (entry.name.startsWith("lib/") && entry.name.endsWith(".so"))
                    ) {
                        // Hybrid Memory-Disk Escalation Protocol
                        if (entry.size < MAX_IN_MEMORY_SIZE) {
                            // Path A: Zero-Copy Fast Path
                            zip.getInputStream(entry).use { inputStream ->
                                val bytes = inputStream.readBytes()
                                if (requireNative()) {
                                    matches.addAll(scanMemory(bytes, bytes.size).toList())
                                }
                            }
                        } else {
                            // Path B: Safe Disk-Streaming Path
                            var tempFile: File? = null
                            try {
                                tempFile = File.createTempFile("yara_chunk_", ".tmp", context.cacheDir)
                                zip.getInputStream(entry).use { inputStream ->
                                    tempFile.outputStream().use { outputStream ->
                                        inputStream.copyTo(outputStream, 8192) // 8KB buffer
                                    }
                                }
                                if (requireNative()) {
                                    matches.addAll(scanFile(tempFile.absolutePath).toList())
                                }
                            } finally {
                                // CRITICAL: Strict deletion
                                tempFile?.delete()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore corrupted zip
            Log.w(TAG, "YARA APK in-memory scan encountered an error.", e)
        }
        return@withContext matches.toList()
    }

    suspend fun scanAllInstalledApps(context: Context): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (!requireNative()) return@withContext emptyMap()
        val results = mutableMapOf<String, List<String>>()
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            val apkPath = appInfo.publicSourceDir
            if (apkPath != null) {
                val matches = scanApkInMemory(context, apkPath)
                if (matches.isNotEmpty()) {
                    results[pkg.packageName] = matches
                }
            }
        }
        return@withContext results
    }
}
