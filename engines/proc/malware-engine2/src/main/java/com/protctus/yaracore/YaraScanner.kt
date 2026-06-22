package com.protctus.yaracore

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object YaraScanner {

    init {
        System.loadLibrary("yara-core")
    }

    // Strict Memory Threshold (25 MB)
    private const val MAX_IN_MEMORY_SIZE = 25 * 1024 * 1024L 

    external fun initYaraEngine(compiledRulesPath: String): Boolean
    external fun destroyYaraEngine()
    
    private external fun scanMemory(buffer: ByteArray, size: Int): Array<String>
    private external fun scanFile(filePath: String): Array<String>

    suspend fun scanRawFile(filePath: String): List<String> = withContext(Dispatchers.IO) {
        return@withContext scanFile(filePath).toList()
    }

    suspend fun scanApkInMemory(context: Context, apkPath: String): List<String> = withContext(Dispatchers.IO) {
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
                                matches.addAll(scanMemory(bytes, bytes.size))
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
                                matches.addAll(scanFile(tempFile.absolutePath))
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
        }
        return@withContext matches.toList()
    }

    suspend fun scanAllInstalledApps(context: Context): Map<String, List<String>> = withContext(Dispatchers.IO) {
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
