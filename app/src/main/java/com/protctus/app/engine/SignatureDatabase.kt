package com.protctus.app.engine

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.BitSet

data class FileHashes(
    val md5: String,
    val sha1: String,
    val sha256: String
)

object SignatureDatabase {

    // Bloom Filter Parameters
    // 64KB bit array (~524,288 bits) with 3 hash functions
    // Supports ~36,000 entries at <1% false positive rate
    private const val BLOOM_FILTER_SIZE = 524288
    private const val NUM_HASH_FUNCTIONS = 3
    private var bloomFilter: BitSet? = null
    private var isLoaded = false

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    /**
     * MurmurHash3 (32-bit) — fast, non-cryptographic hash ideal for Bloom Filters.
     */
    private fun murmurHash3(data: ByteArray, seed: Int): Int {
        val c1 = -0x3361d2af
        val c2 = 0x1b873593
        var h1 = seed
        val len = data.size
        val nblocks = len / 4

        for (i in 0 until nblocks) {
            val i4 = i * 4
            var k1 = (data[i4].toInt() and 0xFF) or
                    ((data[i4 + 1].toInt() and 0xFF) shl 8) or
                    ((data[i4 + 2].toInt() and 0xFF) shl 16) or
                    ((data[i4 + 3].toInt() and 0xFF) shl 24)

            k1 = k1 * c1
            k1 = Integer.rotateLeft(k1, 15)
            k1 = k1 * c2

            h1 = h1 xor k1
            h1 = Integer.rotateLeft(h1, 13)
            h1 = h1 * 5 + -0x19ab949c
        }

        val tail = nblocks * 4
        var k1 = 0
        when (len and 3) {
            3 -> {
                k1 = k1 xor ((data[tail + 2].toInt() and 0xFF) shl 16)
                k1 = k1 xor ((data[tail + 1].toInt() and 0xFF) shl 8)
                k1 = k1 xor (data[tail].toInt() and 0xFF)
                k1 = k1 * c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 = k1 * c2
                h1 = h1 xor k1
            }
            2 -> {
                k1 = k1 xor ((data[tail + 1].toInt() and 0xFF) shl 8)
                k1 = k1 xor (data[tail].toInt() and 0xFF)
                k1 = k1 * c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 = k1 * c2
                h1 = h1 xor k1
            }
            1 -> {
                k1 = k1 xor (data[tail].toInt() and 0xFF)
                k1 = k1 * c1
                k1 = Integer.rotateLeft(k1, 15)
                k1 = k1 * c2
                h1 = h1 xor k1
            }
        }

        h1 = h1 xor len
        h1 = h1 xor (h1 ushr 16)
        h1 = h1 * -0x7a143595
        h1 = h1 xor (h1 ushr 13)
        h1 = h1 * -0x3d4d51cb
        h1 = h1 xor (h1 ushr 16)

        return h1
    }

    /**
     * Initialize the Bloom Filter from the bundled signature database binary.
     * The binary is a raw BitSet serialized to `assets/malware_signatures.bin`.
     * If the file doesn't exist, initializes an empty filter (no false positives).
     */
    fun initialize(context: Context) {
        if (isLoaded) return
        bloomFilter = try {
            val assetStream = context.assets.open("malware_signatures.bin")
            val bytes = assetStream.readBytes()
            assetStream.close()
            BitSet.valueOf(bytes)
        } catch (e: Exception) {
            // No signature DB bundled yet — return empty filter (zero false positives)
            BitSet(BLOOM_FILTER_SIZE)
        }
        isLoaded = true
    }

    suspend fun calculateConcurrentHashes(file: File): FileHashes? = withContext(Dispatchers.IO) {
        try {
            val digestMD5 = MessageDigest.getInstance("MD5")
            val digestSHA1 = MessageDigest.getInstance("SHA-1")
            val digestSHA256 = MessageDigest.getInstance("SHA-256")

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digestMD5.update(buffer, 0, bytesRead)
                    digestSHA1.update(buffer, 0, bytesRead)
                    digestSHA256.update(buffer, 0, bytesRead)
                }
            }

            FileHashes(
                md5 = digestMD5.digest().toHex(),
                sha1 = digestSHA1.digest().toHex(),
                sha256 = digestSHA256.digest().toHex()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if ANY of the file's hashes exist in the Bloom Filter.
     * Uses MurmurHash3 with 3 different seeds per hash string.
     *
     * @return true if the hash is PROBABLY in the malware database
     */
    fun verifySigsAgainstBloomFilter(hashes: FileHashes): Boolean {
        val filter = bloomFilter ?: return false

        val hashStrings = listOf(hashes.md5, hashes.sha1, hashes.sha256)
        for (hashString in hashStrings) {
            val hashBytes = hashString.toByteArray(Charsets.UTF_8)
            var allBitsSet = true
            for (seed in 0 until NUM_HASH_FUNCTIONS) {
                val index = (murmurHash3(hashBytes, seed) and Int.MAX_VALUE) % BLOOM_FILTER_SIZE
                if (!filter.get(index)) {
                    allBitsSet = false
                    break
                }
            }
            if (allBitsSet) return true
        }
        return false
    }

    /**
     * Utility: Insert a known malware hash into the filter (for building the DB offline).
     */
    fun insertHash(hashString: String) {
        val filter = bloomFilter ?: return
        val hashBytes = hashString.toByteArray(Charsets.UTF_8)
        for (seed in 0 until NUM_HASH_FUNCTIONS) {
            val index = (murmurHash3(hashBytes, seed) and Int.MAX_VALUE) % BLOOM_FILTER_SIZE
            filter.set(index)
        }
    }
}
