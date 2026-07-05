package com.protctus.app.engine

import android.content.Context
import com.protctus.app.engine.clam.ClamSignatureEngine
import com.protctus.yaracore.YaraScanner
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.math.ln

/**
 * File category classification for adaptive scan depth and priority routing.
 */
enum class FileCategory {
    APK,
    EXECUTABLE,
    ARCHIVE,
    DOCUMENT,
    MEDIA_IMAGE,
    MEDIA_VIDEO,
    MEDIA_AUDIO,
    UNKNOWN
}

/**
 * Scan priority determines queue ordering.
 * HIGHEST-priority files are scanned before LOWEST-priority files.
 */
enum class ScanPriority(val level: Int) {
    HIGHEST(0),
    MEDIUM(1),
    LOWEST(2)
}

object FileFeatureExtractor {

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
    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    )
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv"
    )
    private val audioExtensions = setOf(
        "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a"
    )
    // Combined set for backward-compatible checks within extraction logic
    private val mediaExtensions = imageExtensions + videoExtensions + audioExtensions

    /**
     * Categorizes a file by its extension for adaptive scanning.
     */
    fun categorizeFile(ext: String): FileCategory {
        val lower = ext.lowercase()
        return when {
            lower == "apk" -> FileCategory.APK
            executableExtensions.contains(lower) -> FileCategory.EXECUTABLE
            archiveExtensions.contains(lower) -> FileCategory.ARCHIVE
            documentExtensions.contains(lower) -> FileCategory.DOCUMENT
            imageExtensions.contains(lower) -> FileCategory.MEDIA_IMAGE
            videoExtensions.contains(lower) -> FileCategory.MEDIA_VIDEO
            audioExtensions.contains(lower) -> FileCategory.MEDIA_AUDIO
            else -> FileCategory.UNKNOWN
        }
    }

    /**
     * Maps a file category to its scan priority.
     */
    fun priorityOf(category: FileCategory): ScanPriority {
        return when (category) {
            FileCategory.APK -> ScanPriority.HIGHEST
            FileCategory.EXECUTABLE -> ScanPriority.HIGHEST
            FileCategory.ARCHIVE -> ScanPriority.HIGHEST
            FileCategory.UNKNOWN -> ScanPriority.HIGHEST
            FileCategory.DOCUMENT -> ScanPriority.MEDIUM
            FileCategory.MEDIA_IMAGE -> ScanPriority.LOWEST
            FileCategory.MEDIA_VIDEO -> ScanPriority.LOWEST
            FileCategory.MEDIA_AUDIO -> ScanPriority.LOWEST
        }
    }

    /**
     * Returns true if this category requires SHA-256 hashing for cache validation.
     */
    fun isSecuritySensitive(category: FileCategory): Boolean {
        return when (category) {
            FileCategory.APK,
            FileCategory.EXECUTABLE,
            FileCategory.ARCHIVE,
            FileCategory.UNKNOWN -> true
            else -> false
        }
    }

    /**
     * Computes SHA-256 of a file. Only called when cache validation
     * requires it or the file is security-sensitive.
     * Uses buffered streaming to avoid loading entire file into memory.
     */
    fun computeSha256(file: File): String? {
        if (!file.exists() || !file.canRead() || file.length() == 0L) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Full evidence extraction — used by DeepScannerService for complete device scan.
     * Unchanged from previous implementation to preserve backward compatibility.
     */
    suspend fun extractEvidence(context: Context, file: File): FileEvidence? {
        if (!file.exists() || !file.canRead()) return null

        val ext = file.extension.lowercase()
        val fileName = file.name.lowercase()
        val fileSize = file.length()

        val yaraMatches = mutableListOf<String>()
        val clamAvMatches = mutableListOf<String>()
        var fileEntropy: Double? = null
        var isApkInWrongPlace = false
        var hasDoubleExtension = false
        var hasRTLO = false
        var hasEOCDAtEnd = false
        var archiveRatio: Double? = null
        var archiveTraversal = false
        var hasEmbeddedExecutable = false

        // Feature Extraction Layer 1: Signatures
        val isRiskyExt = executableExtensions.contains(ext) || archiveExtensions.contains(ext) || documentExtensions.contains(ext)
        if (isRiskyExt && fileSize < 50 * 1024 * 1024) { 
            try {
                val matches = YaraScanner.scanRawFile(file.absolutePath)
                yaraMatches.addAll(matches)
            } catch (e: Exception) {}
            
            try {
                val clamEngine = ClamSignatureEngine.getInstance(context)
                if (clamEngine.ready) {
                    val clamResults = clamEngine.fullScan(file)
                    clamResults.forEach { clamAvMatches.add(it.malwareName) }
                }
            } catch (e: Exception) {}
        }

        // Feature Extraction Layer 2: File Structure & Behavioral Metadata
        when {
            executableExtensions.contains(ext) -> {
                if (ext == "apk") {
                    val parentPath = file.parent?.lowercase() ?: ""
                    if (parentPath.contains(".nomedia") || parentPath.contains(".thumbnails") || 
                        parentPath.contains("android/data") || file.parentFile?.name?.startsWith(".") == true) {
                        isApkInWrongPlace = true
                    }
                    fileEntropy = calculateFileEntropy(file, sampleSize = 8192)
                }
            }
            archiveExtensions.contains(ext) -> {
                val archiveData = scanArchiveForHiddenPayloads(file)
                archiveRatio = archiveData.ratio
                archiveTraversal = archiveData.hasTraversal
                hasEmbeddedExecutable = archiveData.hasEmbeddedExecutable
            }
            mediaExtensions.contains(ext) -> {
                hasEOCDAtEnd = checkMediaSpoofing(file, ext)
            }
        }

        // Feature Extraction Layer 3: Universal anomalies
        hasRTLO = file.name.contains("\u202E")
        if (fileName.count { it == '.' } >= 2) {
            val parts = fileName.split(".")
            val realExt = parts.last()
            val spoofedExt = parts[parts.size - 2]
            if (executableExtensions.contains(realExt) && (documentExtensions.contains(spoofedExt) || mediaExtensions.contains(spoofedExt))) {
                hasDoubleExtension = true
            }
        }

        return FileEvidence(
            fileName = file.name,
            absolutePath = file.absolutePath,
            extension = ext,
            fileSize = fileSize,
            yaraMatches = yaraMatches,
            clamAvMatches = clamAvMatches,
            entropy = fileEntropy,
            isApkInWrongPlace = isApkInWrongPlace,
            hasDoubleExtension = hasDoubleExtension,
            hasRTLO = hasRTLO,
            hasEOCDAtEnd = hasEOCDAtEnd,
            archiveRatio = archiveRatio,
            archiveTraversal = archiveTraversal,
            hasEmbeddedExecutable = hasEmbeddedExecutable
        )
    }

    /**
     * Adaptive evidence extraction — used by RealTimeFileObserver for real-time scanning.
     * Performs depth-appropriate analysis based on file category to conserve battery and CPU.
     *
     * APK/EXECUTABLE → Full analysis (YARA + ClamAV + entropy + structure)
     * ARCHIVE        → Archive inspection (bomb, traversal, embedded exec) + ClamAV
     * DOCUMENT       → Structure analysis (double ext, RTLO, ClamAV)
     * MEDIA_*        → Header-only (polyglot/spoofing check)
     * UNKNOWN        → Generic (entropy + RTLO + double ext + ClamAV)
     */
    suspend fun extractEvidenceAdaptive(context: Context, file: File, category: FileCategory): FileEvidence? {
        if (!file.exists() || !file.canRead()) return null

        val ext = file.extension.lowercase()
        val fileName = file.name.lowercase()
        val fileSize = file.length()

        val yaraMatches = mutableListOf<String>()
        val clamAvMatches = mutableListOf<String>()
        var fileEntropy: Double? = null
        var isApkInWrongPlace = false
        var hasDoubleExtension = false
        var hasRTLO = false
        var hasEOCDAtEnd = false
        var archiveRatio: Double? = null
        var archiveTraversal = false
        var hasEmbeddedExecutable = false

        when (category) {
            FileCategory.APK -> {
                // Full analysis: YARA + ClamAV + entropy + APK location check
                if (fileSize < 50 * 1024 * 1024) {
                    runYaraScan(file, yaraMatches)
                    runClamScan(context, file, clamAvMatches)
                }
                val parentPath = file.parent?.lowercase() ?: ""
                if (parentPath.contains(".nomedia") || parentPath.contains(".thumbnails") ||
                    parentPath.contains("android/data") || file.parentFile?.name?.startsWith(".") == true) {
                    isApkInWrongPlace = true
                }
                fileEntropy = calculateFileEntropy(file, sampleSize = 8192)
            }

            FileCategory.EXECUTABLE -> {
                // Full analysis: YARA + ClamAV + entropy
                if (fileSize < 50 * 1024 * 1024) {
                    runYaraScan(file, yaraMatches)
                    runClamScan(context, file, clamAvMatches)
                }
                fileEntropy = calculateFileEntropy(file, sampleSize = 8192)
            }

            FileCategory.ARCHIVE -> {
                // Archive inspection + ClamAV
                val archiveData = scanArchiveForHiddenPayloads(file)
                archiveRatio = archiveData.ratio
                archiveTraversal = archiveData.hasTraversal
                hasEmbeddedExecutable = archiveData.hasEmbeddedExecutable
                if (fileSize < 50 * 1024 * 1024) {
                    runClamScan(context, file, clamAvMatches)
                }
            }

            FileCategory.DOCUMENT -> {
                // Structure analysis + ClamAV (documents can carry macros/exploits)
                if (fileSize < 50 * 1024 * 1024) {
                    runClamScan(context, file, clamAvMatches)
                }
            }

            FileCategory.MEDIA_IMAGE,
            FileCategory.MEDIA_VIDEO,
            FileCategory.MEDIA_AUDIO -> {
                // Header-only: polyglot/spoofing check — no YARA, no ClamAV
                hasEOCDAtEnd = checkMediaSpoofing(file, ext)
            }

            FileCategory.UNKNOWN -> {
                // Generic analysis: entropy + ClamAV
                fileEntropy = calculateFileEntropy(file, sampleSize = 8192)
                if (fileSize < 50 * 1024 * 1024) {
                    runClamScan(context, file, clamAvMatches)
                }
            }
        }

        // Universal anomaly checks (cheap — always run)
        hasRTLO = file.name.contains("\u202E")
        if (fileName.count { it == '.' } >= 2) {
            val parts = fileName.split(".")
            val realExt = parts.last()
            val spoofedExt = parts[parts.size - 2]
            if (executableExtensions.contains(realExt) && (documentExtensions.contains(spoofedExt) || mediaExtensions.contains(spoofedExt))) {
                hasDoubleExtension = true
            }
        }

        return FileEvidence(
            fileName = file.name,
            absolutePath = file.absolutePath,
            extension = ext,
            fileSize = fileSize,
            yaraMatches = yaraMatches,
            clamAvMatches = clamAvMatches,
            entropy = fileEntropy,
            isApkInWrongPlace = isApkInWrongPlace,
            hasDoubleExtension = hasDoubleExtension,
            hasRTLO = hasRTLO,
            hasEOCDAtEnd = hasEOCDAtEnd,
            archiveRatio = archiveRatio,
            archiveTraversal = archiveTraversal,
            hasEmbeddedExecutable = hasEmbeddedExecutable
        )
    }

    // ─── Internal helpers ────────────────────────────────────────────

    private suspend fun runYaraScan(file: File, results: MutableList<String>) {
        try {
            val matches = YaraScanner.scanRawFile(file.absolutePath)
            results.addAll(matches)
        } catch (e: Exception) {}
    }

    private fun runClamScan(context: Context, file: File, results: MutableList<String>) {
        try {
            val clamEngine = ClamSignatureEngine.getInstance(context)
            if (clamEngine.ready) {
                val clamResults = clamEngine.fullScan(file)
                clamResults.forEach { results.add(it.malwareName) }
            }
        } catch (e: Exception) {}
    }

    private data class ArchiveData(
        val ratio: Double, 
        val hasTraversal: Boolean, 
        val hasEmbeddedExecutable: Boolean
    )

    private fun scanArchiveForHiddenPayloads(file: File): ArchiveData {
        var finalRatio = 0.0
        var hasTraversal = false
        var hasExecutable = false

        try {
            val startTime = System.currentTimeMillis()
            val timeBudgetMs = 5000L

            var uncompressedBytes = 0L
            val compressedBytes = file.length()
            if (compressedBytes == 0L) return ArchiveData(0.0, false, false)

            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    if (System.currentTimeMillis() - startTime > timeBudgetMs) {
                        break
                    }

                    var bytesRead: Int
                    while (zis.read(buffer).also { bytesRead = it } != -1) {
                        uncompressedBytes += bytesRead
                        
                        val ratio = if (compressedBytes > 0) uncompressedBytes.toDouble() / compressedBytes else 0.0
                        if ((ratio > 100.0 && uncompressedBytes > 100_000_000L) || uncompressedBytes > 250_000_000L) { 
                            finalRatio = ratio
                        }

                        if (entry.name.contains("../") || entry.name.contains("..\\")) {
                            hasTraversal = true
                        }

                        if (!hasExecutable && bytesRead >= 4) {
                            for (i in 0 until bytesRead - 3) {
                                // MZ (Windows PE)
                                if (buffer[i] == 0x4D.toByte() && buffer[i+1] == 0x5A.toByte()) {
                                    hasExecutable = true
                                    break
                                }
                                // ELF (Linux/Android)
                                if (buffer[i] == 0x7F.toByte() && buffer[i+1] == 0x45.toByte() && 
                                    buffer[i+2] == 0x4C.toByte() && buffer[i+3] == 0x46.toByte()) {
                                    hasExecutable = true
                                    break
                                }
                                // DEX (Android)
                                if (buffer[i] == 0x64.toByte() && buffer[i+1] == 0x65.toByte() && 
                                    buffer[i+2] == 0x78.toByte() && buffer[i+3] == 0x0A.toByte()) {
                                    hasExecutable = true
                                    break
                                }
                            }
                        }
                    }
                    
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {}

        return ArchiveData(finalRatio, hasTraversal, hasExecutable)
    }

    private fun checkMediaSpoofing(file: File, ext: String): Boolean {
        if (file.length() < 128) return false
        try {
            val raf = java.io.RandomAccessFile(file, "r")
            raf.seek(file.length() - 128)
            val footer = ByteArray(128)
            raf.readFully(footer)
            raf.close()

            for (i in 0 until 128 - 3) {
                if (footer[i] == 0x50.toByte() && footer[i+1] == 0x4B.toByte() && 
                    footer[i+2] == 0x05.toByte() && footer[i+3] == 0x06.toByte()) {
                    return true
                }
            }
        } catch (e: Exception) {}
        return false
    }

    private fun calculateFileEntropy(file: File, sampleSize: Int = 8192): Double {
        val freq = IntArray(256)
        var totalBytes = 0
        try {
            FileInputStream(file).use { fis ->
                val buf = ByteArray(sampleSize)
                val read = fis.read(buf)
                if (read <= 0) return 0.0
                for (i in 0 until read) {
                    freq[buf[i].toInt() and 0xFF]++
                }
                totalBytes = read
            }
        } catch (e: Exception) { return 0.0 }
        
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
}
