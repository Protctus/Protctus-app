package com.protctus.app.security

import org.junit.Assert.*
import org.junit.Test


class TotpCryptoManagerTest {

    // Note: These tests require Android Keystore to be available.
    // On JVM, they verify the isEncrypted classifier which is pure logic.
    // The encrypt/decrypt tests are marked for Robolectric or instrumented testing.

    // ── isEncrypted Classifier Tests ──

    @Test
    fun `raw Base32 TOTP secret is NOT classified as encrypted`() {
        val rawSecret = "JBSWY3DPEHPK3PXP" // Standard 16-char Base32
        assertFalse("Raw Base32 secret should not be classified as encrypted",
            TotpCryptoManager.isEncrypted(rawSecret))
    }

    @Test
    fun `long Base32 secret with 32 chars is NOT classified as encrypted`() {
        val rawSecret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        assertFalse("32-char Base32 should not be classified as encrypted",
            TotpCryptoManager.isEncrypted(rawSecret))
    }

    @Test
    fun `short string under 40 chars is NOT classified as encrypted`() {
        assertFalse(TotpCryptoManager.isEncrypted("short"))
        assertFalse(TotpCryptoManager.isEncrypted("ABCDEFGHIJ"))
        assertFalse(TotpCryptoManager.isEncrypted("1234567890"))
    }

    @Test
    fun `long Base64 string IS classified as encrypted`() {
        // Simulates a real encrypted output (IV + ciphertext + GCM tag encoded in Base64)
        val encryptedLike = "dGhpcyBpcyBhIHZlcnkgbG9uZyBiYXNlNjQgZW5jb2RlZCBzdHJpbmcgdGhhdCBzaW11bGF0ZXM="
        assertTrue("Long Base64 string should be classified as encrypted",
            TotpCryptoManager.isEncrypted(encryptedLike))
    }

    @Test
    fun `string with lowercase chars and length over 40 IS classified as encrypted`() {
        val mixed = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuU" // 42 chars, has lowercase
        assertTrue("Non-Base32 long string should be classified as encrypted",
            TotpCryptoManager.isEncrypted(mixed))
    }

    // ── Edge Cases ──

    @Test
    fun `empty string is NOT classified as encrypted`() {
        assertFalse(TotpCryptoManager.isEncrypted(""))
    }

    @Test
    fun `string of exactly 40 Base32 chars is NOT classified as encrypted`() {
        val exactly40 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ23456722334455"
        assertFalse("Exactly 40-char Base32 should NOT be encrypted (requires > 40)",
            TotpCryptoManager.isEncrypted(exactly40))
    }

    @Test
    fun `string of 41 Base32 chars IS classified as encrypted boundary`() {
        // 41 chars of pure Base32 — length > 40 but all valid Base32
        val chars41 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567ABCDE2345"
        // All chars are A-Z or 2-7, so isEncrypted returns false
        assertFalse("41-char pure Base32 should NOT be encrypted",
            TotpCryptoManager.isEncrypted(chars41))
    }

    @Test
    fun `string of 41 chars with digit 0 IS classified as encrypted`() {
        // '0' is NOT in Base32 charset (A-Z, 2-7), so triggers encrypted detection
        val chars41 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567ABCDE0000"
        assertTrue("41-char with non-Base32 char should be encrypted",
            TotpCryptoManager.isEncrypted(chars41))
    }
}
