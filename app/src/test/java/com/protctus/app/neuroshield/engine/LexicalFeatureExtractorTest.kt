package com.protctus.app.neuroshield.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max

/**
 * Unit tests for [LexicalFeatureExtractor].
 *
 * These are pure JVM tests (no Android APIs) verifying the feature-extraction
 * contract used by the NeuroShield ONNX model:
 *   - Always returns exactly FEATURE_COUNT (53) features.
 *   - Deterministic: same input → same output.
 *   - Handles empty / malformed / punycode / IP / long inputs safely (no crash).
 *   - Feature ordering is stable (indices match the documented pipeline).
 */
class LexicalFeatureExtractorTest {

    private val extractor = LexicalFeatureExtractor()

    @Test
    fun `returns exactly 53 features for every input`() {
        val inputs = listOf(
            "google.com",
            "login-paypal.suspicious-site.tk",
            "xn--pple-43d.com",
            "192.168.1.1",
            "",
            "   ",
            "http://www.example.com/path?q=1#frag",
            "a".repeat(5000),
        )
        for (input in inputs) {
            val features = extractor.extract(input)
            assertEquals("Feature count must be ${LexicalFeatureExtractor.FEATURE_COUNT}",
                LexicalFeatureExtractor.FEATURE_COUNT, features.size)
            // All features must be finite and non-negative.
            for (i in features.indices) {
                assertTrue("Feature[$i] must be finite for input '$input'", features[i].isFinite())
                assertTrue("Feature[$i] must be >= 0 for input '$input'", features[i] >= 0f)
            }
        }
    }

    @Test
    fun `is deterministic`() {
        val input = "secure-bank-verify.xyz"
        val a = extractor.extract(input)
        val b = extractor.extract(input)
        assertContentEquals(a, b)
    }

    @Test
    fun `empty input returns all-zero features`() {
        val features = extractor.extract("")
        for (i in features.indices) {
            assertEquals(0f, features[i], 0f)
        }
    }

    @Test
    fun `punycode flag is set for xn-- domains`() {
        val normal = extractor.extract("google.com")
        val puny = extractor.extract("xn--pple-43d.com")
        assertEquals(0f, normal[11], 0f)   // has_punycode index 11
        assertEquals(1f, puny[11], 0f)
    }

    @Test
    fun `raw IP flag is set for IPv4 addresses`() {
        val domain = extractor.extract("example.com")
        val ip = extractor.extract("192.168.1.1")
        assertEquals(0f, domain[12], 0f)   // is_raw_ip index 12
        assertEquals(1f, ip[12], 0f)
    }

    @Test
    fun `scheme www port and path are stripped before feature extraction`() {
        val raw = extractor.extract("HTTP://WWW.Example.COM:8080/some/path?x=1#frag")
        val clean = extractor.extract("example.com")
        // After sanitization both should resolve to the same host features.
        assertContentEquals(raw, clean)
    }

    @Test
    fun `subdomain count reflects nesting`() {
        val shallow = extractor.extract("example.com")
        val deep = extractor.extract("a.b.c.d.example.com")
        assertTrue("Deeply nested domain should have more subdomains",
            deep[1] > shallow[1]) // subdomain_count index 1
    }

    @Test
    fun `known feature indices are within bounds`() {
        // Index 0 = domain_length, 11 = has_punycode, 12 = is_raw_ip, 45 = domain_depth.
        // These are documented in the Kotlin extractor; assert they map correctly.
        val google = extractor.extract("google.com")
        val puny = extractor.extract("xn--pple-43d.com")
        val ip = extractor.extract("192.168.1.1")
        assertEquals(10f, google[0], 0f)    // domain_length
        assertEquals(0f, google[11], 0f)   // has_punycode
        assertEquals(1f, puny[11], 0f)
        assertEquals(0f, google[12], 0f)   // is_raw_ip
        assertEquals(1f, ip[12], 0f)
        assertEquals(2f, google[45], 0f)   // domain_depth (google.com = 2 labels)
    }

    @Test
    fun `maxConsonantRun helper handles edge cases`() {
        // Exercise private helper indirectly via public extract on consonant-heavy input.
        val consonants = extractor.extract("bcdfghjk.com")
        val vowels = extractor.extract("aeiou.com")
        assertTrue("Consonant-heavy domain should have >= max consonant run",
            consonants[10] >= vowels[10]) // max_consonant_run index 10
    }

    @Test
    fun `long domain is truncated to MAX_DOMAIN_LENGTH without crashing`() {
        val long = "a".repeat(100000) + ".com"
        val features = extractor.extract(long)
        assertEquals(LexicalFeatureExtractor.FEATURE_COUNT, features.size)
        assertTrue(features.all { it.isFinite() })
    }

    // ════════════════════════════════════════════════════════════════════
    //  Adversarial input handling
    // ════════════════════════════════════════════════════════════════════

    @Test
    fun `percent-encoded dot is decoded before domain parsing`() {
        // "example%2Ecom" should resolve to the same features as "example.com"
        val encoded = extractor.extract("example%2Ecom")
        val clean = extractor.extract("example.com")
        assertContentEquals(encoded, clean)
    }

    @Test
    fun `percent-encoded slash does not create path traversal`() {
        // "example.com%2Fevil.com" should resolve to "example.com" (the %2F is decoded
        // to '/' but it appears after the host, so it's treated as path separator)
        val encoded = extractor.extract("example.com%2Fevil.com")
        val clean = extractor.extract("example.com")
        assertContentEquals(encoded, clean)
    }

    @Test
    fun `ipv4 leading zeros are not treated as raw IP`() {
        // "192.168.001.001" has leading zeros — must NOT be flagged as raw IP
        // because leading-zero octets are an evasion technique.
        val ip = extractor.extract("192.168.001.001")
        assertEquals(0f, ip[12], 0f)   // is_raw_ip index 12 must be 0

        // Normal IPv4 without leading zeros IS flagged as raw IP.
        val normalIp = extractor.extract("192.168.1.1")
        assertEquals(1f, normalIp[12], 0f)
    }

    @Test
    fun `ipv6 bracketed literal is stripped before feature extraction`() {
        // "[::1]" should resolve to the same features as "::1"
        val bracketed = extractor.extract("[::1]")
        val unbracketed = extractor.extract("::1")
        assertContentEquals(bracketed, unbracketed)
    }

    @Test
    fun `ipv6 bracketed literal with port is handled`() {
        // "[2001:db8::1]:443" should resolve to "2001:db8::1"
        val bracketed = extractor.extract("[2001:db8::1]:443")
        val unbracketed = extractor.extract("2001:db8::1")
        assertContentEquals(bracketed, unbracketed)
    }

    @Test
    fun `all extracted features are finite for adversarial inputs`() {
        val adversarial = listOf(
            "example%2Ecom",
            "192.168.001.001",
            "[::1]",
            "[2001:db8::1]:443",
            "%2E%2E%2E",
            "a%00b.com",
            "x".repeat(10000),
        )
        for (input in adversarial) {
            val features = extractor.extract(input)
            assertEquals(LexicalFeatureExtractor.FEATURE_COUNT, features.size)
            for (i in features.indices) {
                assertTrue("Feature[$i] must be finite for input '$input'", features[i].isFinite())
                assertTrue("Feature[$i] must be non-negative for input '$input'", features[i] >= 0f)
            }
        }
    }
}
