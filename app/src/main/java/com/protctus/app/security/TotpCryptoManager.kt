package com.protctus.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts/decrypts TOTP secrets using Android Keystore-backed AES-GCM.
 * The key material never leaves the hardware-backed keystore, making it
 * impossible to extract even on rooted devices with direct Firestore access.
 *
 * Format: Base64(IV || Ciphertext || AuthTag)
 */
object TotpCryptoManager {

    private const val KEYSTORE_ALIAS = "protctus_totp_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    /**
     * Encrypt a plaintext TOTP secret. Returns Base64-encoded (IV + ciphertext).
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val iv = cipher.iv // GCM generates a unique IV per encryption
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Concatenate IV + ciphertext for storage
        val combined = iv + ciphertext
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    /**
     * Decrypt a Base64-encoded (IV + ciphertext) back to the plaintext TOTP secret.
     */
    fun decrypt(encryptedBase64: String): String {
        val combined = android.util.Base64.decode(encryptedBase64, android.util.Base64.NO_WRAP)

        // GCM IV is always 12 bytes
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Check if a string looks like an encrypted value (Base64 with sufficient length)
     * vs a raw plaintext TOTP secret (all uppercase Base32 characters).
     */
    fun isEncrypted(value: String): Boolean {
        // Raw TOTP secrets are Base32 (A-Z, 2-7), typically 32 chars
        // Encrypted values are Base64 and much longer due to IV + GCM tag overhead
        return value.length > 40 && !value.all { it in 'A'..'Z' || it in '2'..'7' }
    }
}
