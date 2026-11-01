package com.protctus.edge.phishing

/**
 * Memory-efficient probabilistic set membership test.
 * Uses ~1.2MB for 500K domains at 0.01% FP rate vs ~40MB for a HashSet.
 *
 * A false positive here means a safe domain bypasses the whitelist and enters
 * the heuristic/ML pipeline — it does NOT get blocked (it just scores low and passes).
 * Therefore a tiny FP rate is acceptable and dramatically reduces RAM usage.
 */
class BloomFilter(expectedItems: Int, fpRate: Double = 0.0001) {

    private val numBits: Int
    private val numHashes: Int
    private val bits: LongArray

    init {
        // Optimal sizing: m = -n*ln(p) / (ln2)^2, k = (m/n) * ln2
        val m = (-expectedItems * Math.log(fpRate) / (Math.log(2.0) * Math.log(2.0))).toInt()
        numBits = m
        numHashes = ((m.toDouble() / expectedItems) * Math.log(2.0)).toInt().coerceAtLeast(1)
        bits = LongArray((numBits + 63) / 64)
    }

    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        for (i in 0 until numHashes) {
            val hash = murmurHash(bytes, i) and Int.MAX_VALUE
            val idx = hash % numBits
            bits[idx / 64] = bits[idx / 64] or (1L shl (idx % 64))
        }
    }

    fun mightContain(value: String): Boolean {
        val bytes = value.toByteArray(Charsets.UTF_8)
        for (i in 0 until numHashes) {
            val hash = murmurHash(bytes, i) and Int.MAX_VALUE
            val idx = hash % numBits
            if (bits[idx / 64] and (1L shl (idx % 64)) == 0L) return false
        }
        return true
    }

    private fun murmurHash(data: ByteArray, seed: Int): Int {
        var h = seed xor data.size
        var i = 0
        while (i + 4 <= data.size) {
            var k = (data[i].toInt() and 0xFF) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    ((data[i + 2].toInt() and 0xFF) shl 16) or
                    ((data[i + 3].toInt() and 0xFF) shl 24)
            k = k * 0x5bd1e995.toInt()
            k = k xor (k ushr 24)
            k = k * 0x5bd1e995.toInt()
            h = h * 0x5bd1e995.toInt()
            h = h xor k
            i += 4
        }
        when (data.size - i) {
            3 -> { h = h xor ((data[i + 2].toInt() and 0xFF) shl 16); h = h xor ((data[i + 1].toInt() and 0xFF) shl 8); h = h xor (data[i].toInt() and 0xFF); h = h * 0x5bd1e995.toInt() }
            2 -> { h = h xor ((data[i + 1].toInt() and 0xFF) shl 8); h = h xor (data[i].toInt() and 0xFF); h = h * 0x5bd1e995.toInt() }
            1 -> { h = h xor (data[i].toInt() and 0xFF); h = h * 0x5bd1e995.toInt() }
        }
        h = h xor (h ushr 13)
        h = h * 0x5bd1e995.toInt()
        h = h xor (h ushr 15)
        return h
    }
}
