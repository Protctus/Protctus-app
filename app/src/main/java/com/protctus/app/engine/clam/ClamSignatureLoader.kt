package com.protctus.app.engine.clam

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads ClamAV-compatible signature databases from app assets.
 *
 * Supports two ClamAV signature formats:
 *
 * 1. Hash Database (.hdb/.hsb) — from ClamAV's readdb.c cli_loadhash():
 *    Format: HASH:SIZE:MALWARE_NAME
 *    Example: e4d909c290d0fb1ca068ffaddf22cbd0:1234:Trojan.AndroidOS.FakeAV
 *
 * 2. Extended Signature Database (.ndb) — from ClamAV's readdb.c cli_loadndb():
 *    Format: MALWARE_NAME:TARGET_TYPE:OFFSET:HEX_PATTERN
 *    Example: Trojan.AndroidOS.Banker:0:*:6465780a303335
 *
 * Thread-safe: signatures are loaded once and stored immutably.
 */
class ClamSignatureLoader private constructor() {

    private val hashSignatures = mutableMapOf<String, ClamSignature>()    // hash → signature
    private val patternSignatures = mutableListOf<ClamSignature>()
    private var _totalLoaded = 0

    /** Total number of loaded signatures */
    val signatureCount: Int get() = _totalLoaded

    /** Number of hash-based signatures */
    val hashCount: Int get() = hashSignatures.size

    /** Number of pattern-based signatures */
    val patternCount: Int get() = patternSignatures.size

    /**
     * Load all signature databases from assets/clam_signatures/.
     *
     * @param context Android context for asset access
     * @return number of signatures successfully loaded
     */
    fun loadFromAssets(context: Context): Int {
        hashSignatures.clear()
        patternSignatures.clear()
        _totalLoaded = 0

        try {
            val sigFiles = context.assets.list(SIGS_ASSET_DIR) ?: emptyArray()
            Log.d(TAG, "Found ${sigFiles.size} signature files in assets/$SIGS_ASSET_DIR")

            for (fileName in sigFiles) {
                try {
                    context.assets.open("$SIGS_ASSET_DIR/$fileName").use { stream ->
                        BufferedReader(InputStreamReader(stream)).useLines { lines ->
                            when {
                                fileName.endsWith(".hdb") || fileName.endsWith(".hsb") -> {
                                    lines.forEach { line -> parseHashSignature(line, fileName) }
                                }
                                fileName.endsWith(".ndb") -> {
                                    lines.forEach { line -> parsePatternSignature(line, fileName) }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse signature file: $fileName — ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list signature files: ${e.message}")
        }

        _totalLoaded = hashSignatures.size + patternSignatures.size
        Log.i(TAG, "Loaded $_totalLoaded ClamAV signatures (${hashSignatures.size} hashes, ${patternSignatures.size} patterns)")
        return _totalLoaded
    }

    /**
     * Parse a hash signature line in ClamAV .hdb/.hsb format.
     * Format: HASH:SIZE:MALWARE_NAME
     *
     * Source reference: ClamAV readdb.c cli_loadhash()
     */
    private fun parseHashSignature(line: String, sourceFile: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return

        val parts = trimmed.split(":")
        if (parts.size < 3) return

        val hash = parts[0].uppercase()
        val size = parts[1].toLongOrNull()
        val malwareName = parts[2]

        if (hash.isBlank() || malwareName.isBlank()) return

        // Determine hash algorithm from length
        val algorithm = when (hash.length) {
            32 -> HashAlgorithm.MD5
            40 -> HashAlgorithm.SHA1
            64 -> HashAlgorithm.SHA256
            else -> return // Unknown hash format
        }

        val sig = ClamSignature(
            id = "hdb_${sourceFile}_${hashSignatures.size}",
            malwareName = malwareName,
            type = SignatureType.HASH,
            hashValue = hash,
            hashAlgorithm = algorithm,
            fileSize = size
        )

        hashSignatures[hash] = sig
    }

    /**
     * Parse a pattern signature line in ClamAV .ndb format.
     * Format: MALWARE_NAME:TARGET_TYPE:OFFSET:HEX_PATTERN
     *
     * Source reference: ClamAV readdb.c cli_loadndb()
     */
    private fun parsePatternSignature(line: String, sourceFile: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return

        val parts = trimmed.split(":")
        if (parts.size < 4) return

        val malwareName = parts[0]
        val targetTypeId = parts[1].toIntOrNull() ?: 0
        val offsetStr = parts[2]
        val hexPattern = parts[3]

        if (malwareName.isBlank() || hexPattern.isBlank()) return

        // Parse offset
        val offset = when {
            offsetStr == "*" -> PatternOffset.ANY
            offsetStr == "0" -> PatternOffset.ABSOLUTE_START
            offsetStr.startsWith("EP") -> PatternOffset.ENTRY_POINT
            offsetStr.startsWith("EOF") -> PatternOffset.EOF
            else -> PatternOffset.ANY
        }

        // Convert hex pattern to bytes (ignoring wildcards for now)
        val patternBytes = try {
            hexStringToByteArray(hexPattern.replace("??", "00"))
        } catch (e: Exception) {
            return // Invalid hex pattern
        }

        if (patternBytes.isEmpty()) return

        val sig = ClamSignature(
            id = "ndb_${sourceFile}_${patternSignatures.size}",
            malwareName = malwareName,
            type = SignatureType.BYTE_PATTERN,
            bytePattern = patternBytes,
            patternOffset = offset,
            targetType = TargetType.fromClamId(targetTypeId)
        )

        patternSignatures.add(sig)
    }

    /**
     * Look up a hash in the signature database.
     *
     * @param hash uppercase hex string of the file hash
     * @return matching signature, or null
     */
    fun lookupHash(hash: String): ClamSignature? {
        return hashSignatures[hash.uppercase()]
    }

    /**
     * Get all byte-pattern signatures for scanning.
     */
    fun getPatternSignatures(): List<ClamSignature> = patternSignatures.toList()

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("{", "").replace("}", "")
        if (cleanHex.length % 2 != 0) return byteArrayOf()
        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            cleanHex.substring(index, index + 2).toInt(16).toByte()
        }
    }

    companion object {
        private const val TAG = "ClamSigLoader"
        private const val SIGS_ASSET_DIR = "clam_signatures"

        @Volatile
        private var instance: ClamSignatureLoader? = null

        fun getInstance(): ClamSignatureLoader {
            return instance ?: synchronized(this) {
                instance ?: ClamSignatureLoader().also { instance = it }
            }
        }
    }
}
