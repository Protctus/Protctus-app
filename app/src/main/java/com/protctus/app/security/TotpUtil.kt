package com.protctus.app.security

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Pure-Kotlin TOTP (Time-based One-Time Password) utility.
 * Compatible with Google Authenticator, Authy, Microsoft Authenticator, etc.
 * Implements RFC 6238 (TOTP) and RFC 4226 (HOTP).
 */
object TotpUtil {

    private const val TOTP_PERIOD = 30L   // seconds
    private const val TOTP_DIGITS = 6
    private const val SECRET_LENGTH = 20  // 160-bit secret (standard)
    private const val ISSUER = "Protctus"

    // Base32 alphabet (RFC 4648)
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Generate a cryptographically secure random secret key, returned as Base32 string.
     */
    fun generateSecret(): String {
        val random = SecureRandom()
        val bytes = ByteArray(SECRET_LENGTH)
        random.nextBytes(bytes)
        return base32Encode(bytes)
    }

    /**
     * Generate a 6-digit TOTP code for the given secret at the current time.
     */
    fun generateCode(base32Secret: String, timeMillis: Long = System.currentTimeMillis()): String {
        val key = base32Decode(base32Secret)
        val counter = timeMillis / 1000 / TOTP_PERIOD
        return hotp(key, counter)
    }

    /**
     * Verify a user-provided TOTP code against the secret.
     * Allows ±1 time step window to handle clock skew.
     */
    fun verifyCode(base32Secret: String, code: String, timeMillis: Long = System.currentTimeMillis()): Boolean {
        if (code.length != TOTP_DIGITS) return false

        val key = base32Decode(base32Secret)
        val counter = timeMillis / 1000 / TOTP_PERIOD

        // Check current and ±1 window
        for (offset in -1..1) {
            val generated = hotp(key, counter + offset)
            if (generated == code) return true
        }
        return false
    }

    /**
     * Build the otpauth:// URI for QR code scanning.
     */
    fun buildOtpAuthUri(email: String, base32Secret: String): String {
        val encodedEmail = java.net.URLEncoder.encode(email, "UTF-8")
        val encodedIssuer = java.net.URLEncoder.encode(ISSUER, "UTF-8")
        return "otpauth://totp/$encodedIssuer:$encodedEmail?secret=$base32Secret&issuer=$encodedIssuer&digits=$TOTP_DIGITS&period=$TOTP_PERIOD"
    }

    /**
     * Generate a QR code Bitmap from the otpauth:// URI.
     */
    fun generateQrBitmap(otpAuthUri: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = writer.encode(otpAuthUri, BarcodeFormat.QR_CODE, size, size, hints)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    // ── RFC 4226 HOTP Implementation ──

    private fun hotp(key: ByteArray, counter: Long): String {
        val counterBytes = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 10.0.pow(TOTP_DIGITS).toInt()
        return otp.toString().padStart(TOTP_DIGITS, '0')
    }

    // ── Base32 Encoding/Decoding ──

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0

        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_CHARS[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_CHARS[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    private fun base32Decode(base32: String): ByteArray {
        val cleaned = base32.uppercase().replace(" ", "").replace("-", "")
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = BASE32_CHARS.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add((buffer shr bitsLeft).toByte())
            }
        }
        return output.toByteArray()
    }
}
