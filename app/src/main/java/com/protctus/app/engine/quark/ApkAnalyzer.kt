package com.protctus.app.engine.quark

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts APK metadata required for Quark behavioral analysis.
 *
 * This is the Android-native equivalent of Quark's ApkInfo class
 * (quark/core/apkinfo.py). Instead of using Androguard for DEX parsing,
 * it leverages Android's PackageManager API and direct APK ZIP inspection
 * to extract:
 *   - Package identity (name, version, label)
 *   - Requested permissions
 *   - Declared components (activities, services, receivers, providers)
 *   - API usage hints from DEX string pools
 *   - Installation metadata
 *
 * This approach avoids the heavy DEX bytecode parsing that Androguard performs,
 * making it suitable for on-device execution within the Smart Scan time budget.
 */
class ApkAnalyzer(private val context: Context) {

    /**
     * Analyze an installed package by package name.
     *
     * @param packageName the package to analyze
     * @return ApkProfile with extracted metadata, or null if package not found
     */
    fun analyzeInstalledPackage(packageName: String): ApkProfile? {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_ACTIVITIES

        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Package not found: $packageName")
            return null
        }

        return buildProfile(pm, packageInfo)
    }

    /**
     * Analyze from a PackageInfo object (when already available from scan pipeline).
     */
    fun analyzePackageInfo(pm: PackageManager, packageInfo: PackageInfo): ApkProfile {
        return buildProfile(pm, packageInfo)
    }

    /**
     * Analyze an APK file on disk (for file-based scanning).
     *
     * @param apkFile the APK file to analyze
     * @return ApkProfile with extracted metadata, or null on failure
     */
    fun analyzeApkFile(apkFile: File): ApkProfile? {
        if (!apkFile.exists() || !apkFile.canRead()) return null

        val pm = context.packageManager
        val packageInfo = try {
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_ACTIVITIES
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse APK: ${apkFile.name} — ${e.message}")
            return null
        } ?: return null

        // Fix for archive info — set source dir so labels resolve correctly
        packageInfo.applicationInfo?.apply {
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }

        val profile = buildProfile(pm, packageInfo)

        // Supplement with DEX string pool analysis for API detection
        val dexStrings = extractDexStringPool(apkFile)
        return profile.copy(dexStringPool = dexStrings)
    }

    private fun buildProfile(pm: PackageManager, packageInfo: PackageInfo): ApkProfile {
        val appInfo = packageInfo.applicationInfo
        val packageName = packageInfo.packageName

        val appLabel = try {
            appInfo?.loadLabel(pm)?.toString() ?: packageName
        } catch (e: Exception) {
            packageName
        }

        val permissions = packageInfo.requestedPermissions?.toSet() ?: emptySet()

        val services = try {
            packageInfo.services?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val receivers = try {
            packageInfo.receivers?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val activities = try {
            packageInfo.activities?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val providers = try {
            packageInfo.providers?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Installer source
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { pm.getInstallSourceInfo(packageName).installingPackageName } catch (_: Exception) { null }
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
        }

        val isSystem = appInfo?.let { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false

        // Extract DEX strings from APK if source dir is available
        val apkPath = appInfo?.publicSourceDir
        val dexStrings = if (apkPath != null) {
            extractDexStringPool(File(apkPath))
        } else {
            emptySet()
        }

        return ApkProfile(
            packageName = packageName,
            appLabel = appLabel,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
            },
            permissions = permissions,
            services = services,
            receivers = receivers,
            activities = activities,
            providers = providers,
            installerPackage = installer,
            isSystemApp = isSystem,
            apkPath = apkPath,
            dexStringPool = dexStrings
        )
    }

    /**
     * Extract string references from the DEX file(s) inside an APK.
     *
     * This is a lightweight alternative to full DEX parsing — we read just
     * the string pool from the DEX header to find API class references.
     * This enables Stage 2/3 detection (API presence checking) without
     * requiring Androguard or full bytecode traversal.
     *
     * DEX string pool format:
     *   - Header at offset 0x00 (magic "dex\n035\0" or similar)
     *   - string_ids_size at offset 0x38 (uint32)
     *   - string_ids_off at offset 0x3C (uint32)
     *   - Each string_id points to a string_data_item with ULEB128 length + MUTF-8 data
     */
    private fun extractDexStringPool(apkFile: File): Set<String> {
        val strings = mutableSetOf<String>()
        try {
            ZipFile(apkFile).use { zip ->
                // Find all DEX entries (classes.dex, classes2.dex, ...)
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") && !it.isDirectory }
                    .toList()

                for (dexEntry in dexEntries) {
                    try {
                        val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
                        if (dexBytes.size < 0x70) continue // Too small for valid DEX
                        if (dexBytes[0] != 'd'.code.toByte() || dexBytes[1] != 'e'.code.toByte() ||
                            dexBytes[2] != 'x'.code.toByte()) continue // Not a DEX file

                        val stringIdsSize = readUint32(dexBytes, 0x38)
                        val stringIdsOff = readUint32(dexBytes, 0x3C)

                        // Limit to prevent OOM on huge DEX files
                        val maxStrings = minOf(stringIdsSize, MAX_DEX_STRINGS.toLong()).toInt()

                        for (i in 0 until maxStrings) {
                            val stringDataOff = readUint32(dexBytes, (stringIdsOff + i * 4).toInt())
                            if (stringDataOff >= dexBytes.size) continue

                            val str = readMutf8String(dexBytes, stringDataOff.toInt())
                            if (str != null && str.length >= MIN_STRING_LENGTH) {
                                // Only keep strings that look like API references
                                // (class names, method names with slashes or dots)
                                if (str.contains('/') || str.contains('.') ||
                                    str.startsWith("L") || str.startsWith("android.")) {
                                    strings.add(str)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse DEX entry ${dexEntry.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open APK: ${apkFile.name} — ${e.message}")
        }
        return strings
    }

    /** Read a little-endian uint32 from a byte array */
    private fun readUint32(data: ByteArray, offset: Int): Long {
        if (offset + 4 > data.size) return 0L
        return (data[offset].toLong() and 0xFF) or
               ((data[offset + 1].toLong() and 0xFF) shl 8) or
               ((data[offset + 2].toLong() and 0xFF) shl 16) or
               ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    /**
     * Read a MUTF-8 encoded string from DEX string_data_item.
     * Format: ULEB128 length prefix + MUTF-8 bytes + null terminator.
     */
    private fun readMutf8String(data: ByteArray, offset: Int): String? {
        if (offset >= data.size) return null
        try {
            var pos = offset
            // Read ULEB128 length
            var size = 0
            var shift = 0
            while (pos < data.size) {
                val b = data[pos].toInt() and 0xFF
                pos++
                size = size or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 35) return null // Malformed
            }

            if (size > MAX_STRING_SIZE || pos + size > data.size) return null

            // Read MUTF-8 bytes until null terminator
            val sb = StringBuilder(size)
            var bytesRead = 0
            while (bytesRead < size && pos < data.size) {
                val b = data[pos].toInt() and 0xFF
                if (b == 0) break
                if (b < 0x80) {
                    sb.append(b.toChar())
                    pos++
                } else if (b and 0xE0 == 0xC0) {
                    if (pos + 1 >= data.size) break
                    val b2 = data[pos + 1].toInt() and 0xFF
                    sb.append(((b and 0x1F shl 6) or (b2 and 0x3F)).toChar())
                    pos += 2
                } else if (b and 0xF0 == 0xE0) {
                    if (pos + 2 >= data.size) break
                    val b2 = data[pos + 1].toInt() and 0xFF
                    val b3 = data[pos + 2].toInt() and 0xFF
                    sb.append(((b and 0x0F shl 12) or (b2 and 0x3F shl 6) or (b3 and 0x3F)).toChar())
                    pos += 3
                } else {
                    pos++
                }
                bytesRead++
            }
            return sb.toString()
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        private const val TAG = "ApkAnalyzer"
        private const val MAX_DEX_STRINGS = 50_000
        private const val MAX_STRING_SIZE = 2048
        private const val MIN_STRING_LENGTH = 5
    }
}

/**
 * Profile of an analyzed APK — contains all metadata needed for Quark behavioral analysis.
 */
data class ApkProfile(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val permissions: Set<String>,
    val services: List<String>,
    val receivers: List<String>,
    val activities: List<String>,
    val providers: List<String>,
    val installerPackage: String?,
    val isSystemApp: Boolean,
    val apkPath: String?,
    val dexStringPool: Set<String> = emptySet()
) {
    /** Total component count */
    val componentCount: Int
        get() = services.size + receivers.size + activities.size + providers.size

    /** Check if a specific permission is requested */
    fun hasPermission(permission: String): Boolean = permissions.contains(permission)

    /** Check if a Dalvik class reference exists in the DEX string pool */
    fun containsClassReference(dalvikClassName: String): Boolean {
        // Check both Dalvik format (Landroid/...) and Java format (android....)
        val javaName = dalvikClassName.removePrefix("L").removeSuffix(";").replace('/', '.')
        return dexStringPool.any { str ->
            str.contains(dalvikClassName) || str.contains(javaName)
        }
    }

    /** Check if a method reference exists in the DEX string pool */
    fun containsMethodReference(methodName: String): Boolean {
        return dexStringPool.any { it.contains(methodName) }
    }
}
