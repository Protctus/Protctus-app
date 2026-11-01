package com.protctus.app.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.BitSet


class SignatureDatabaseTest {

    @Before
    fun setup() {
        // Reset bloom filter state before each test via reflection
        val field = SignatureDatabase::class.java.getDeclaredField("bloomFilter")
        field.isAccessible = true
        field.set(null, BitSet(524288))

        val loadedField = SignatureDatabase::class.java.getDeclaredField("isLoaded")
        loadedField.isAccessible = true
        loadedField.set(null, true)
    }

    // ── Hash Correctness Tests ──

    @Test
    fun `MurmurHash3 produces consistent output for same input`() {
        val method = SignatureDatabase::class.java.getDeclaredMethod(
            "murmurHash3", ByteArray::class.java, Int::class.java
        )
        method.isAccessible = true

        val input = "test_hash_value".toByteArray()
        val hash1 = method.invoke(null, input, 0) as Int
        val hash2 = method.invoke(null, input, 0) as Int
        assertEquals("Same input+seed must produce identical hash", hash1, hash2)
    }

    @Test
    fun `MurmurHash3 produces different output for different seeds`() {
        val method = SignatureDatabase::class.java.getDeclaredMethod(
            "murmurHash3", ByteArray::class.java, Int::class.java
        )
        method.isAccessible = true

        val input = "test_hash_value".toByteArray()
        val hash0 = method.invoke(null, input, 0) as Int
        val hash1 = method.invoke(null, input, 1) as Int
        val hash2 = method.invoke(null, input, 2) as Int
        assertNotEquals("Different seeds should produce different hashes", hash0, hash1)
        assertNotEquals("Different seeds should produce different hashes", hash1, hash2)
    }

    @Test
    fun `MurmurHash3 handles empty byte array`() {
        val method = SignatureDatabase::class.java.getDeclaredMethod(
            "murmurHash3", ByteArray::class.java, Int::class.java
        )
        method.isAccessible = true

        // Should not throw
        val hash = method.invoke(null, ByteArray(0), 0) as Int
        assertNotNull(hash)
    }

    @Test
    fun `MurmurHash3 handles single byte`() {
        val method = SignatureDatabase::class.java.getDeclaredMethod(
            "murmurHash3", ByteArray::class.java, Int::class.java
        )
        method.isAccessible = true

        val hash = method.invoke(null, byteArrayOf(0x41), 0) as Int
        assertNotNull(hash)
    }

    @Test
    fun `MurmurHash3 handles exact 4-byte aligned input`() {
        val method = SignatureDatabase::class.java.getDeclaredMethod(
            "murmurHash3", ByteArray::class.java, Int::class.java
        )
        method.isAccessible = true

        // Exactly 4 bytes = 1 full block, no tail
        val hash = method.invoke(null, "ABCD".toByteArray(), 0) as Int
        assertNotNull(hash)
    }

    // ── Bloom Filter Insert & Lookup Tests ──

    @Test
    fun `inserted hash is found by bloom filter`() {
        val malwareHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        SignatureDatabase.insertHash(malwareHash)

        val hashes = FileHashes(
            md5 = "d41d8cd98f00b204e9800998ecf8427e",
            sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709",
            sha256 = malwareHash
        )

        assertTrue("Inserted hash must be detected", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }

    @Test
    fun `non-inserted hash is NOT found (no false positive for known clean hash)`() {
        // Insert a specific malware hash
        SignatureDatabase.insertHash("aaaa_known_malware_sha256_hash")

        // Check a completely different hash
        val cleanHashes = FileHashes(
            md5 = "clean_md5_value_12345678901234567",
            sha1 = "clean_sha1_value_1234567890123456789",
            sha256 = "zzzz_clean_file_sha256_hash_totally_different"
        )

        assertFalse("Clean file should NOT match", SignatureDatabase.verifySigsAgainstBloomFilter(cleanHashes))
    }

    @Test
    fun `bloom filter matches on md5 when only md5 was inserted`() {
        val malwareMd5 = "abc123def456789012345678901234ff"
        SignatureDatabase.insertHash(malwareMd5)

        val hashes = FileHashes(
            md5 = malwareMd5,
            sha1 = "completely_different_sha1",
            sha256 = "completely_different_sha256"
        )

        assertTrue("Should match on MD5 alone", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }

    @Test
    fun `empty bloom filter returns false for all queries`() {
        // Fresh empty filter (from setup)
        val hashes = FileHashes(
            md5 = "anything_at_all",
            sha1 = "literally_anything",
            sha256 = "some_random_hash"
        )

        assertFalse("Empty filter must return false", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }

    @Test
    fun `null bloom filter returns false`() {
        val field = SignatureDatabase::class.java.getDeclaredField("bloomFilter")
        field.isAccessible = true
        field.set(null, null)

        val hashes = FileHashes(
            md5 = "test",
            sha1 = "test",
            sha256 = "test"
        )

        assertFalse("Null filter must return false safely", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }

    // ── Performance Tests ──

    @Test
    fun `bloom filter handles 10000 insertions without errors`() {
        for (i in 0 until 10000) {
            SignatureDatabase.insertHash("malware_hash_sample_$i")
        }
        // Verify at least one inserted hash is found
        val hashes = FileHashes("x", "y", "malware_hash_sample_5000")
        assertTrue("Inserted hash must be found after bulk insert", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }

    @Test
    fun `bloom filter false positive rate below 5 percent after 1000 insertions`() {
        // Insert 1000 known malware hashes
        for (i in 0 until 1000) {
            SignatureDatabase.insertHash("known_malware_$i")
        }

        // Test 1000 completely different hashes and count false positives
        var falsePositives = 0
        for (i in 0 until 1000) {
            val hashes = FileHashes(
                md5 = "clean_file_unique_md5_$i",
                sha1 = "clean_file_unique_sha1_$i",
                sha256 = "clean_file_unique_sha256_$i"
            )
            if (SignatureDatabase.verifySigsAgainstBloomFilter(hashes)) {
                falsePositives++
            }
        }

        val fpRate = falsePositives / 1000.0
        assertTrue(
            "False positive rate should be < 5% but was ${fpRate * 100}% ($falsePositives/1000)",
            fpRate < 0.05
        )
    }

    // ── Edge Cases ──

    @Test
    fun `hash with special characters works correctly`() {
        val weirdHash = "../../etc/passwd\u0000\n\t"
        SignatureDatabase.insertHash(weirdHash)

        val hashes = FileHashes(md5 = weirdHash, sha1 = "x", sha256 = "y")
        assertTrue("Special character hash must be retrievable", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }

    @Test
    fun `very long hash string works correctly`() {
        val longHash = "a".repeat(10000)
        SignatureDatabase.insertHash(longHash)

        val hashes = FileHashes(md5 = longHash, sha1 = "x", sha256 = "y")
        assertTrue("Long hash must be retrievable", SignatureDatabase.verifySigsAgainstBloomFilter(hashes))
    }
}
