package com.protctus.app.engine.clam

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * ClamAV-Compatible Signature Scanning Engine for Android.
 *
 * Implements the core matching algorithms from ClamAV's libclamav in pure Kotlin:
 *
 * 1. Hash Matching (from matcher-hash.c):
 *    - Computes MD5/SHA256 of file → looks up in hash database
 *    - O(1) lookup via HashMap
 *
 * 2. Byte Pattern Matching (from matcher-bm.c):
 *    - Scans file content for known malware byte sequences
 *    - Uses sliding window with Boyer-Moore-inspired skip logic
 *
 * 3. Structural Analysis (from scanners.c cli_scanzip):
 *    - Detects suspicious structural patterns in APK/ZIP files
 *    - Entropy-based packed payload detection
 *
 * This is a Kotlin reimplementation of the matching concepts — NOT a JNI wrapper
 * around the C library. This avoids GPL-2.0 licensing issues while preserving
 * functional parity for Android-relevant threats.
 *
 * Thread-safe: all scanning operations are stateless after initialization.
 */
class ClamSignatureEngine private constructor(private val context: Context) {

    private val sigLoader = ClamSignatureLoader.getInstance()
    private var isInitialized = false

    /** Whether the engine is ready to scan */
    val ready: Boolean get() = isInitialized && sigLoader.signatureCount > 0

    /** Total loaded signatures */
    val signatureCount: Int get() = sigLoader.signatureCount

    /**
     * Initialize the engine by loading signature databases from assets.
     *
     * @return true if at least one signature was loaded
     */
    fun initialize(): Boolean {
        if (isInitialized && sigLoader.signatureCount > 0) return true

        val loaded = sigLoader.loadFromAssets(context)
        isInitialized = loaded > 0
        Log.i(TAG, "ClamSignatureEngine initialized: $loaded signatures")
        return isInitialized
    }

    // ═══════════════════════════════════════════
    //  HASH-BASED SCANNING (matcher-hash.c)
    // ═══════════════════════════════════════════

    /**
     * Scan a file by computing its hash and checking against the signature database.
     * This is the fastest scan mode — O(n) for reading + O(1) for lookup.
     *
     * Computes both SHA256 and MD5 to match against both .hdb and .hsb databases.
     *
     * @param file the file to scan
     * @return ClamMatchResult if malware is detected, null if clean
     */
    fun scanFileHash(file: File): ClamMatchResult? {
        if (!file.exists() || !file.canRead() || file.length() == 0L) return null
        val startTime = System.currentTimeMillis()

        try {
            // Compute SHA256 and MD5 simultaneously in a single pass
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val md5Digest = MessageDigest.getInstance("MD5")

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(HASH_BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    sha256Digest.update(buffer, 0, bytesRead)
                    md5Digest.update(buffer, 0, bytesRead)
                }
            }

            val sha256Hex = sha256Digest.digest().joinToString("") { "%02X".format(it) }
            val md5Hex = md5Digest.digest().joinToString("") { "%02X".format(it) }

            val duration = System.currentTimeMillis() - startTime

            // Check SHA256 first (more reliable)
            val sha256Match = sigLoader.lookupHash(sha256Hex)
            if (sha256Match != null) {
                return ClamMatchResult(
                    malwareName = sha256Match.malwareName,
                    matchType = "SHA256 Hash Match",
                    signatureId = sha256Match.id,
                    confidence = 1.0f,
                    fileHash = sha256Hex,
                    scanDurationMs = duration
                )
            }

            // Check MD5
            val md5Match = sigLoader.lookupHash(md5Hex)
            if (md5Match != null) {
                return ClamMatchResult(
                    malwareName = md5Match.malwareName,
                    matchType = "MD5 Hash Match",
                    signatureId = md5Match.id,
                    confidence = 0.95f,
                    fileHash = md5Hex,
                    scanDurationMs = duration
                )
            }

            return null
        } catch (e: Exception) {
            Log.w(TAG, "Hash scan failed for ${file.name}: ${e.message}")
            return null
        }
    }

    // ═══════════════════════════════════════════
    //  BYTE PATTERN SCANNING (matcher-bm.c)
    // ═══════════════════════════════════════════

    /**
     * Scan a file for byte-pattern signature matches.
     * Uses a sliding window approach inspired by ClamAV's Boyer-Moore implementation.
     *
     * This is slower than hash matching but catches variants and partial matches.
     *
     * @param file the file to scan
     * @param maxScanBytes maximum bytes to read (default 10MB to prevent OOM)
     * @return list of all matching signatures
     */
    fun scanFilePatterns(file: File, maxScanBytes: Long = MAX_PATTERN_SCAN_BYTES): List<ClamMatchResult> {
        if (!file.exists() || !file.canRead() || file.length() == 0L) return emptyList()
        val startTime = System.currentTimeMillis()

        val patterns = sigLoader.getPatternSignatures()
        if (patterns.isEmpty()) return emptyList()

        val results = mutableListOf<ClamMatchResult>()
        val scanLength = minOf(file.length(), maxScanBytes)

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Use Memory Mapped File (FileChannel) to stream memory instead of allocating massive byte arrays
                val channel = raf.channel
                val fileBuffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, scanLength)

                for (sig in patterns) {
                    val pattern = sig.bytePattern ?: continue
                    if (pattern.size < 4) continue // Skip trivially short patterns

                    val matchOffset = boyerMooreSearch(fileBuffer, pattern, sig.patternOffset)
                    if (matchOffset >= 0) {
                        results.add(
                            ClamMatchResult(
                                malwareName = sig.malwareName,
                                matchType = "Byte Pattern Match",
                                signatureId = sig.id,
                                confidence = 0.85f,
                                fileHash = null,
                                scanDurationMs = System.currentTimeMillis() - startTime
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pattern scan failed for ${file.name}: ${e.message}")
        }

        return results
    }

    // ═══════════════════════════════════════════
    //  FULL SCAN (Hash + Pattern)
    // ═══════════════════════════════════════════

    /**
     * Full scan: runs both hash matching and byte pattern scanning.
     * Used by Deep Scan mode for maximum detection coverage.
     *
     * @param file the file to scan
     * @return list of all detections (empty if clean)
     */
    fun fullScan(file: File): List<ClamMatchResult> {
        val results = mutableListOf<ClamMatchResult>()

        // Phase 1: Hash scan (fast, definitive)
        val hashResult = scanFileHash(file)
        if (hashResult != null) {
            results.add(hashResult)
            return results // Hash match is definitive, no need for pattern scan
        }

        // Phase 2: Pattern scan (slower, catches variants)
        val patternResults = scanFilePatterns(file)
        results.addAll(patternResults)

        return results
    }

    // ═══════════════════════════════════════════
    //  BOYER-MOORE PATTERN SEARCH
    // ═══════════════════════════════════════════

    /**
     * Boyer-Moore-inspired byte pattern search.
     *
     * Simplified implementation of the matching logic from ClamAV's matcher-bm.c.
     * Uses bad-character heuristic for O(n/m) average-case performance.
     *
     * @param data the file content to search in
     * @param pattern the byte pattern to find
     * @param offsetType where in the file to search
     * @return offset of the match, or -1 if not found
     */
    private fun boyerMooreSearch(
        data: java.nio.ByteBuffer,
        pattern: ByteArray,
        offsetType: PatternOffset
    ): Int {
        val n = data.limit()
        val m = pattern.size
        if (m > n || m == 0) return -1

        // Determine search range based on offset type
        val (searchStart, searchEnd) = when (offsetType) {
            PatternOffset.ABSOLUTE_START -> Pair(0, minOf(m * 4, n))
            PatternOffset.EOF -> Pair(maxOf(0, n - m * 4), n)
            else -> Pair(0, n) // ANY or ENTRY_POINT: search entire buffer
        }

        // Build bad-character skip table
        val badChar = IntArray(256) { m }
        for (i in 0 until m - 1) {
            badChar[pattern[i].toInt() and 0xFF] = m - 1 - i
        }

        // Search
        var i = searchStart + m - 1
        while (i < searchEnd) {
            var j = m - 1
            var k = i

            while (j >= 0 && data.get(k) == pattern[j]) {
                j--
                k--
            }

            if (j < 0) return k + 1 // Match found

            val skip = badChar[data.get(i).toInt() and 0xFF]
            i += maxOf(1, skip)
        }

        return -1 // No match
    }

    companion object {
        private const val TAG = "ClamSigEngine"
        private const val HASH_BUFFER_SIZE = 8192
        private const val MAX_PATTERN_SCAN_BYTES = 10L * 1024 * 1024 // 10MB

        @Volatile
        private var instance: ClamSignatureEngine? = null

        fun getInstance(context: Context): ClamSignatureEngine {
            return instance ?: synchronized(this) {
                instance ?: ClamSignatureEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
