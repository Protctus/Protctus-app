package com.protctus.app.engine.androguard

import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * APK structure analysis engine.
 *
 * Inspects the APK ZIP structure for suspicious patterns:
 *   - Hidden DEX files (non-standard names)
 *   - Embedded APKs inside the archive
 *   - Suspicious native libraries
 *   - Malformed ZIP entries
 *   - Suspicious asset files
 *
 * Derived from Androguard's APK class structure analysis in core/apk/__init__.py.
 */
class ApkStructureAnalyzer {

    /**
     * Analyze the internal structure of an APK file.
     */
    fun analyze(apkFile: File): ApkStructureFindings {
        val findings = mutableListOf<AnalysisFinding>()
        val dexEntries = mutableListOf<String>()
        val nativeLibs = mutableListOf<String>()
        val supportedAbis = mutableSetOf<String>()
        var hasHiddenDex = false
        var hasHiddenApk = false
        var hasSuspiciousAssets = false
        var totalEntries = 0

        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    totalEntries++
                    val name = entry.name

                    // ── DEX file detection ──
                    if (name.endsWith(".dex")) {
                        dexEntries.add(name)
                        // Non-standard DEX names (hidden DEX)
                        if (!name.matches(Regex("^classes\\d*\\.dex$"))) {
                            hasHiddenDex = true
                            findings.add(AnalysisFinding(
                                FindingCategory.APK_STRUCTURE, Severity.CRITICAL,
                                "Hidden DEX File",
                                "Non-standard DEX file found: '$name'. " +
                                "Legitimate apps only have classes[N].dex files.",
                                "Possible packed malware or code injection"
                            ))
                        }
                    }

                    // ── Embedded APK detection ──
                    if (name.endsWith(".apk") && !name.startsWith("META-INF")) {
                        hasHiddenApk = true
                        findings.add(AnalysisFinding(
                            FindingCategory.APK_STRUCTURE, Severity.HIGH,
                            "Embedded APK",
                            "APK file found inside the archive: '$name'. Possible payload dropper.",
                            "Size: ${entry.size} bytes"
                        ))
                    }

                    // ── Native library analysis ──
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        nativeLibs.add(name)
                        // Extract ABI
                        val abi = name.removePrefix("lib/").substringBefore("/")
                        supportedAbis.add(abi)

                        // Check for suspicious native library names
                        val libName = name.substringAfterLast("/")
                        if (SUSPICIOUS_NATIVE_LIBS.any { libName.contains(it, ignoreCase = true) }) {
                            findings.add(AnalysisFinding(
                                FindingCategory.NATIVE_CODE, Severity.HIGH,
                                "Suspicious Native Library",
                                "Native library '$libName' matches known suspicious patterns.",
                                "Path: $name"
                            ))
                        }
                    }

                    // ── Suspicious assets ──
                    if (name.startsWith("assets/")) {
                        val assetName = name.removePrefix("assets/").lowercase()
                        // Executable files in assets
                        if (SUSPICIOUS_ASSET_EXTENSIONS.any { assetName.endsWith(it) }) {
                            hasSuspiciousAssets = true
                            findings.add(AnalysisFinding(
                                FindingCategory.APK_STRUCTURE, Severity.HIGH,
                                "Suspicious Asset File",
                                "Potentially dangerous file in assets: '$name'.",
                                "Size: ${entry.size} bytes"
                            ))
                        }
                        // Very large encrypted-looking assets
                        if (entry.size > 1_000_000 && !assetName.endsWith(".png") &&
                            !assetName.endsWith(".jpg") && !assetName.endsWith(".webp")) {
                            // Check if name looks random (obfuscated)
                            val simpleName = assetName.substringAfterLast("/").substringBeforeLast(".")
                            if (simpleName.length <= 3 || simpleName.matches(Regex("^[a-f0-9]+$"))) {
                                hasSuspiciousAssets = true
                                findings.add(AnalysisFinding(
                                    FindingCategory.APK_STRUCTURE, Severity.MEDIUM,
                                    "Large Obfuscated Asset",
                                    "Large asset with obfuscated name: '$name' (${entry.size / 1024}KB).",
                                    "May contain encrypted payload"
                                ))
                            }
                        }
                    }

                    // ── META-INF anomalies ──
                    if (name.startsWith("META-INF/") && name.endsWith(".RSA") || name.endsWith(".DSA")) {
                        // Multiple signature files could indicate tampering
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "APK structure analysis failed: ${e.message}")
            findings.add(AnalysisFinding(
                FindingCategory.APK_STRUCTURE, Severity.MEDIUM,
                "APK Parse Error",
                "Failed to fully analyze APK structure: ${e.message}",
                null
            ))
        }

        // Large APK with no native libs but excessive entry count
        if (totalEntries > 10_000) {
            findings.add(AnalysisFinding(
                FindingCategory.APK_STRUCTURE, Severity.LOW,
                "Very Large APK Structure",
                "APK contains $totalEntries entries. Unusually large for most applications.",
                null
            ))
        }

        return ApkStructureFindings(
            totalEntryCount = totalEntries,
            dexEntries = dexEntries,
            nativeLibraries = nativeLibs,
            supportedAbis = supportedAbis.toList(),
            hasHiddenDex = hasHiddenDex,
            hasHiddenApk = hasHiddenApk,
            hasSuspiciousAssets = hasSuspiciousAssets,
            apkSizeBytes = apkFile.length(),
            findings = findings
        )
    }

    companion object {
        private const val TAG = "ApkStructureAnalyzer"

        val SUSPICIOUS_NATIVE_LIBS = listOf(
            "libhack", "libexploit", "libroot", "libhook",
            "libinject", "libpatch", "libfrida", "libxposed",
            "libsubstrate", "libdump", "libspy", "librat",
            "libpayload", "libshell", "libstager",
            // Known Packer Stubs (False Negative Mitigation)
            "libsec.so", "libdexhelper.so", "libjiagu.so", 
            "libprotectclass.so", "libchaosvmp.so", "libtup.so"
        )

        val SUSPICIOUS_ASSET_EXTENSIONS = listOf(
            ".dex", ".apk", ".jar", ".so", ".elf",
            ".sh", ".bin", ".exe", ".dat", ".enc"
        )
    }
}
