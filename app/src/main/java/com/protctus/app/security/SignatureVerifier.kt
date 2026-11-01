package com.protctus.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Security utility to prevent APK repackaging.
 * Calculates the current APK's signature hash at runtime and compares it
 * against a hardcoded expected hash to ensure the app hasn't been modified.
 */
object SignatureVerifier {

    private const val TAG = "SignatureVerifier"
    
    // Replace this with the actual SHA-256 hash of your release keystore.
    // For now, we use a placeholder that you MUST update before production.
    private const val EXPECTED_SIGNATURE_HASH = "UPDATE_ME_WITH_REAL_SHA256_HASH"

    fun verifySignature(context: Context) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            
            val signatures = packageInfo.signatures
            if (signatures.isNullOrEmpty()) {
                Log.e(TAG, "No signatures found!")
                crashApp()
                return
            }

            // Calculate SHA-256 hash of the active signature
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signatures[0].toByteArray())
            val currentSignatureHash = bytesToHex(md.digest())

            Log.d(TAG, "Current APK Signature Hash: $currentSignatureHash")

            if (EXPECTED_SIGNATURE_HASH != "UPDATE_ME_WITH_REAL_SHA256_HASH" && 
                currentSignatureHash != EXPECTED_SIGNATURE_HASH) {
                Log.e(TAG, "SIGNATURE MISMATCH! APK HAS BEEN REPACKAGED!")
                crashApp()
            } else {
                Log.d(TAG, "Signature verification passed.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            crashApp()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun crashApp() {
        // Hard crash to prevent tampered app execution
        Log.e(TAG, "Terminating process due to security violation.")
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }
}
