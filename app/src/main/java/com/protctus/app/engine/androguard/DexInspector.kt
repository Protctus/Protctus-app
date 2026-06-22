package com.protctus.app.engine.androguard

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * Lightweight DEX metadata inspector for Android.
 *
 * Implements DEX analysis concepts from Androguard's core/dex/__init__.py:
 *   - DEX header parsing (magic, checksum, file size, string/class/method counts)
 *   - String pool scanning for suspicious API references
 *   - Namespace analysis for obfuscation detection
 *   - Native method and reflection indicators
 *
 * This is NOT a full DEX disassembler — it reads metadata from the DEX header
 * and string pool only, making it suitable for on-device execution.
 *
 * DEX Format Reference (from Androguard source):
 *   Offset 0x00: magic[8]          "dex\n035\0" or "dex\n039\0"
 *   Offset 0x08: checksum[4]       Adler32
 *   Offset 0x0C: signature[20]     SHA-1
 *   Offset 0x20: file_size[4]      Total file size
 *   Offset 0x24: header_size[4]    Header size (0x70)
 *   Offset 0x38: string_ids_size[4]
 *   Offset 0x3C: string_ids_off[4]
 *   Offset 0x40: type_ids_size[4]
 *   Offset 0x44: type_ids_off[4]
 *   Offset 0x50: method_ids_size[4]
 *   Offset 0x54: method_ids_off[4]
 *   Offset 0x58: class_defs_size[4]
 *   Offset 0x5C: class_defs_off[4]
 */
class DexInspector {

    /**
     * Inspect all DEX files inside an APK.
     *
     * @param apkFile path to the APK file
     * @return DexFindings with all detected indicators
     */
    fun inspectApk(apkFile: File): DexFindings {
        val findings = mutableListOf<AnalysisFinding>()
        var totalClasses = 0
        var totalMethods = 0
        var dexCount = 0
        val allStrings = mutableSetOf<String>()

        try {
            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") && !it.isDirectory }
                    .toList()

                dexCount = dexEntries.size

                for (dexEntry in dexEntries) {
                    try {
                        val dexBytes = zip.getInputStream(dexEntry).use { it.readBytes() }
                        if (dexBytes.size < 0x70) continue

                        // Validate DEX magic
                        if (dexBytes[0] != 'd'.code.toByte() || dexBytes[1] != 'e'.code.toByte() ||
                            dexBytes[2] != 'x'.code.toByte()) continue

                        // Parse header metadata
                        val classCount = readUint32(dexBytes, 0x58).toInt()
                        val methodCount = readUint32(dexBytes, 0x50).toInt()
                        totalClasses += classCount
                        totalMethods += methodCount

                        // Extract string pool for analysis
                        val strings = extractStringPool(dexBytes)
                        allStrings.addAll(strings)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse DEX ${dexEntry.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open APK: ${e.message}")
        }

        // Multidex analysis
        if (dexCount > 1) {
            findings.add(AnalysisFinding(
                FindingCategory.DEX_STRUCTURE, Severity.INFO,
                "Multi-DEX Application",
                "APK contains $dexCount DEX files with $totalClasses classes and $totalMethods methods.",
                "classes.dex through classes${dexCount}.dex"
            ))
        }

        // Method count analysis (65K limit per DEX indicates complexity)
        if (totalMethods > 100_000) {
            findings.add(AnalysisFinding(
                FindingCategory.DEX_STRUCTURE, Severity.INFO,
                "Very Large Application",
                "App has $totalMethods methods across $dexCount DEX files.",
                null
            ))
        }

        // ── String Pool Analysis ──
        val suspiciousNamespaces = detectSuspiciousNamespaces(allStrings, findings)
        val reflectionIndicators = detectReflection(allStrings, findings)
        val dynamicLoadIndicators = detectDynamicLoading(allStrings, findings)
        val nativeIndicators = detectNativeMethods(allStrings, findings)
        val cryptoIndicators = detectCryptoUsage(allStrings, findings)
        val networkIndicators = detectNetworkUsage(allStrings, findings)
        val obfuscationScore = calculateObfuscationScore(allStrings)

        if (obfuscationScore >= 70) {
            findings.add(AnalysisFinding(
                FindingCategory.OBFUSCATION, Severity.MEDIUM,
                "Heavy Obfuscation Detected",
                "Obfuscation score: $obfuscationScore/100. Class names are heavily shortened/randomized.",
                "Common in malware to evade signature detection"
            ))
        } else if (obfuscationScore >= 40) {
            findings.add(AnalysisFinding(
                FindingCategory.OBFUSCATION, Severity.LOW,
                "Moderate Obfuscation",
                "Obfuscation score: $obfuscationScore/100. App uses ProGuard/R8 style obfuscation.",
                null
            ))
        }

        return DexFindings(
            dexFileCount = dexCount,
            totalClassCount = totalClasses,
            totalMethodCount = totalMethods,
            suspiciousNamespaces = suspiciousNamespaces,
            reflectionIndicators = reflectionIndicators,
            dynamicLoadIndicators = dynamicLoadIndicators,
            nativeMethodIndicators = nativeIndicators,
            cryptoIndicators = cryptoIndicators,
            networkIndicators = networkIndicators,
            obfuscationScore = obfuscationScore,
            findings = findings
        )
    }

    /**
     * Lightweight DEX inspection using only the string pool (for Smart Scan).
     * Faster than full inspection — doesn't need to parse all DEX headers.
     */
    fun quickInspect(apkFile: File): DexFindings {
        val findings = mutableListOf<AnalysisFinding>()
        val allStrings = mutableSetOf<String>()
        var dexCount = 0

        try {
            ZipFile(apkFile).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") && !it.isDirectory }
                    .toList()
                dexCount = dexEntries.size

                // Only inspect the primary DEX for speed
                val mainDex = dexEntries.firstOrNull() ?: return DexFindings(
                    0, 0, 0, emptyList(), emptyList(), emptyList(),
                    emptyList(), emptyList(), emptyList(), 0, findings
                )

                val dexBytes = zip.getInputStream(mainDex).use { it.readBytes() }
                if (dexBytes.size >= 0x70) {
                    val strings = extractStringPool(dexBytes, maxStrings = 10_000)
                    allStrings.addAll(strings)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Quick inspect failed: ${e.message}")
        }

        val suspiciousNamespaces = detectSuspiciousNamespaces(allStrings, findings)
        val reflectionIndicators = detectReflection(allStrings, findings)
        val dynamicLoadIndicators = detectDynamicLoading(allStrings, findings)

        return DexFindings(
            dexFileCount = dexCount,
            totalClassCount = 0,
            totalMethodCount = 0,
            suspiciousNamespaces = suspiciousNamespaces,
            reflectionIndicators = reflectionIndicators,
            dynamicLoadIndicators = dynamicLoadIndicators,
            nativeMethodIndicators = emptyList(),
            cryptoIndicators = emptyList(),
            networkIndicators = emptyList(),
            obfuscationScore = 0,
            findings = findings
        )
    }

    // ═══════════════════════════════════════════
    //  STRING POOL EXTRACTION
    // ═══════════════════════════════════════════

    private fun extractStringPool(dexBytes: ByteArray, maxStrings: Int = 30_000): Set<String> {
        val strings = mutableSetOf<String>()
        val stringIdsSize = readUint32(dexBytes, 0x38).toInt()
        val stringIdsOff = readUint32(dexBytes, 0x3C).toInt()
        val limit = minOf(stringIdsSize, maxStrings)

        for (i in 0 until limit) {
            val offset = readUint32(dexBytes, stringIdsOff + i * 4).toInt()
            if (offset >= dexBytes.size) continue
            val str = readMutf8(dexBytes, offset)
            if (str != null && str.length >= 4) {
                strings.add(str)
            }
        }
        return strings
    }

    // ═══════════════════════════════════════════
    //  DETECTION HEURISTICS
    // ═══════════════════════════════════════════

    private fun detectSuspiciousNamespaces(strings: Set<String>, findings: MutableList<AnalysisFinding>): List<String> {
        val found = mutableListOf<String>()
        SUSPICIOUS_NAMESPACES.forEach { (namespace, reason) ->
            if (strings.any { it.contains(namespace) }) {
                found.add(namespace)
                findings.add(AnalysisFinding(
                    FindingCategory.DEX_STRUCTURE, Severity.HIGH,
                    "Suspicious Namespace: $namespace",
                    reason,
                    "Found in DEX string pool"
                ))
            }
        }
        return found
    }

    private fun detectReflection(strings: Set<String>, findings: MutableList<AnalysisFinding>): List<String> {
        val found = mutableListOf<String>()
        REFLECTION_INDICATORS.forEach { indicator ->
            if (strings.any { it.contains(indicator) }) {
                found.add(indicator)
            }
        }
        if (found.size >= 3) {
            findings.add(AnalysisFinding(
                FindingCategory.REFLECTION, Severity.MEDIUM,
                "Heavy Reflection Usage",
                "App uses ${found.size} reflection APIs. May be hiding true behavior.",
                found.joinToString(", ")
            ))
        }
        return found
    }

    private fun detectDynamicLoading(strings: Set<String>, findings: MutableList<AnalysisFinding>): List<String> {
        val found = mutableListOf<String>()
        DYNAMIC_LOAD_INDICATORS.forEach { (indicator, reason) ->
            if (strings.any { it.contains(indicator) }) {
                found.add(indicator)
                findings.add(AnalysisFinding(
                    FindingCategory.DYNAMIC_LOADING, Severity.HIGH,
                    "Dynamic Code Loading: $indicator",
                    reason,
                    "Found in DEX string pool"
                ))
            }
        }
        return found
    }

    private fun detectNativeMethods(strings: Set<String>, findings: MutableList<AnalysisFinding>): List<String> {
        val found = mutableListOf<String>()
        NATIVE_INDICATORS.forEach { indicator ->
            if (strings.any { it.contains(indicator) }) {
                found.add(indicator)
            }
        }
        if (found.size >= 2) {
            findings.add(AnalysisFinding(
                FindingCategory.NATIVE_CODE, Severity.MEDIUM,
                "Native Code Execution",
                "App loads native libraries and executes native code.",
                found.joinToString(", ")
            ))
        }
        return found
    }

    private fun detectCryptoUsage(strings: Set<String>, findings: MutableList<AnalysisFinding>): List<String> {
        val found = mutableListOf<String>()
        CRYPTO_INDICATORS.forEach { indicator ->
            if (strings.any { it.contains(indicator) }) found.add(indicator)
        }
        return found
    }

    private fun detectNetworkUsage(strings: Set<String>, findings: MutableList<AnalysisFinding>): List<String> {
        val found = mutableListOf<String>()
        NETWORK_INDICATORS.forEach { indicator ->
            if (strings.any { it.contains(indicator) }) found.add(indicator)
        }
        return found
    }

    /**
     * Calculate obfuscation score based on class name patterns.
     * ProGuard/R8 renames classes to single-letter names (a, b, c, a.a, a.b.c).
     * Higher score = more obfuscated.
     */
    private fun calculateObfuscationScore(strings: Set<String>): Int {
        val classNames = strings.filter {
            it.startsWith("L") && it.endsWith(";") && it.contains("/")
        }
        if (classNames.size < 10) return 0

        val shortNames = classNames.count { name ->
            val simpleName = name.substringAfterLast('/').removeSuffix(";")
            simpleName.length <= 2
        }

        return ((shortNames.toFloat() / classNames.size) * 100).toInt().coerceIn(0, 100)
    }

    // ═══════════════════════════════════════════
    //  DEX BYTE READING UTILITIES
    // ═══════════════════════════════════════════

    private fun readUint32(data: ByteArray, offset: Int): Long {
        if (offset + 4 > data.size) return 0L
        return (data[offset].toLong() and 0xFF) or
               ((data[offset + 1].toLong() and 0xFF) shl 8) or
               ((data[offset + 2].toLong() and 0xFF) shl 16) or
               ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun readMutf8(data: ByteArray, offset: Int): String? {
        if (offset >= data.size) return null
        try {
            var pos = offset
            // Read ULEB128 length
            var size = 0; var shift = 0
            while (pos < data.size) {
                val b = data[pos].toInt() and 0xFF; pos++
                size = size or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7; if (shift > 35) return null
            }
            if (size > 2048 || pos + size > data.size) return null

            val sb = StringBuilder(size)
            var count = 0
            while (count < size && pos < data.size) {
                val b = data[pos].toInt() and 0xFF
                if (b == 0) break
                if (b < 0x80) { sb.append(b.toChar()); pos++ }
                else if (b and 0xE0 == 0xC0) {
                    if (pos + 1 >= data.size) break
                    sb.append(((b and 0x1F shl 6) or (data[pos + 1].toInt() and 0x3F)).toChar()); pos += 2
                } else if (b and 0xF0 == 0xE0) {
                    if (pos + 2 >= data.size) break
                    sb.append(((b and 0x0F shl 12) or (data[pos + 1].toInt() and 0x3F shl 6) or (data[pos + 2].toInt() and 0x3F)).toChar()); pos += 3
                } else { pos++ }
                count++
            }
            return sb.toString()
        } catch (e: Exception) { return null }
    }

    companion object {
        private const val TAG = "DexInspector"

        val SUSPICIOUS_NAMESPACES = mapOf(
            "com.metasploit" to "Metasploit penetration testing framework",
            "net.droidjack" to "DroidJack Remote Access Trojan",
            "ahmyth.server" to "AhMyth Remote Access Trojan",
            "com.spynote" to "SpyNote surveillance malware",
            "com.androrat" to "AndroRAT remote administration tool",
            "de.robv.android.xposed" to "Xposed framework (root exploit toolkit)",
            "com.saurik.substrate" to "Cydia Substrate (runtime hooking framework)",
            "com.topjohnwu.magisk" to "Magisk root framework",
            "com.koushikdutta.superuser" to "SuperUser root management",
            "eu.chainfire.supersu" to "SuperSU root management"
        )

        val REFLECTION_INDICATORS = listOf(
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "java.lang.reflect.Constructor",
            "getDeclaredMethod",
            "getDeclaredField",
            "setAccessible",
            "java.lang.reflect.Proxy"
        )

        val DYNAMIC_LOAD_INDICATORS = mapOf(
            "DexClassLoader" to "Dynamic DEX loading — can execute downloaded code",
            "PathClassLoader" to "Custom class loading from external path",
            "InMemoryDexClassLoader" to "In-memory DEX execution — evasion technique",
            "dalvik.system.DexFile" to "Direct DEX file manipulation",
            "Runtime.getRuntime().exec" to "Shell command execution",
            "ProcessBuilder" to "Process execution capability"
        )

        val NATIVE_INDICATORS = listOf(
            "System.loadLibrary",
            "System.load",
            "Runtime.loadLibrary",
            "dlopen",
            "JNI_OnLoad"
        )

        val CRYPTO_INDICATORS = listOf(
            "javax.crypto.Cipher",
            "javax.crypto.spec.SecretKeySpec",
            "java.security.KeyPairGenerator",
            "javax.crypto.spec.IvParameterSpec",
            "AES/CBC/PKCS5Padding",
            "RSA/ECB/PKCS1Padding"
        )

        val NETWORK_INDICATORS = listOf(
            "java.net.HttpURLConnection",
            "java.net.Socket",
            "java.net.ServerSocket",
            "javax.net.ssl.HttpsURLConnection",
            "okhttp3.OkHttpClient",
            "retrofit2.Retrofit"
        )
    }
}
