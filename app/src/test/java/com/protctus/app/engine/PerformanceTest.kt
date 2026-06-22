package com.protctus.app.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis


class PerformanceTest {

    @Test
    fun `bloom filter 100k lookups complete in under 500ms`() {
        // Setup: insert 1000 signatures
        val field = SignatureDatabase::class.java.getDeclaredField("bloomFilter")
        field.isAccessible = true
        field.set(null, java.util.BitSet(524288))
        val loadedField = SignatureDatabase::class.java.getDeclaredField("isLoaded")
        loadedField.isAccessible = true
        loadedField.set(null, true)

        for (i in 0 until 1000) {
            SignatureDatabase.insertHash("perf_test_malware_$i")
        }

        val elapsed = measureTimeMillis {
            for (i in 0 until 100_000) {
                val hashes = FileHashes(
                    md5 = "perf_lookup_md5_$i",
                    sha1 = "perf_lookup_sha1_$i",
                    sha256 = "perf_lookup_sha256_$i"
                )
                SignatureDatabase.verifySigsAgainstBloomFilter(hashes)
            }
        }

        assertTrue(
            "100k lookups should complete in < 500ms but took ${elapsed}ms",
            elapsed < 500
        )
    }

    @Test
    fun `murmur hash 1M iterations complete in under 2 seconds`() {
        val method = SignatureDatabase::class.java.getDeclaredMethod(
            "murmurHash3", ByteArray::class.java, Int::class.java
        )
        method.isAccessible = true

        val testData = "benchmark_hash_input_string_12345".toByteArray()

        val elapsed = measureTimeMillis {
            for (i in 0 until 1_000_000) {
                method.invoke(null, testData, i % 3)
            }
        }

        assertTrue(
            "1M MurmurHash3 ops should complete in < 2000ms but took ${elapsed}ms",
            elapsed < 2000
        )
    }

    @Test
    fun `file risk evaluation handles 500 files in under 1 second`() {
        val threats = mutableListOf<ScanThreat>()

        // Create 500 temp files
        val files = (0 until 500).map { i ->
            val f = java.io.File.createTempFile("perf_scan_$i", ".txt")
            f.writeText("safe content $i")
            f
        }

        val elapsed = measureTimeMillis {
            for (file in files) {
                ThreatBrain.evaluateFileRisk(file, threats)
            }
        }

        // Cleanup
        files.forEach { it.delete() }

        assertTrue(
            "500 file evaluations should complete in < 1000ms but took ${elapsed}ms",
            elapsed < 1000
        )
    }

    @Test
    fun `bloom filter memory remains stable after 50k insertions`() {
        val field = SignatureDatabase::class.java.getDeclaredField("bloomFilter")
        field.isAccessible = true
        field.set(null, java.util.BitSet(524288))
        val loadedField = SignatureDatabase::class.java.getDeclaredField("isLoaded")
        loadedField.isAccessible = true
        loadedField.set(null, true)

        val memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        for (i in 0 until 50_000) {
            SignatureDatabase.insertHash("stress_hash_$i")
        }

        System.gc()
        val memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memDiff = memAfter - memBefore

        // Bloom filter is fixed 64KB, so memory growth should be minimal (< 5MB)
        assertTrue(
            "Memory growth should be < 5MB but was ${memDiff / 1024 / 1024}MB",
            memDiff < 5 * 1024 * 1024
        )
    }
}
