package com.protctus.chakravyuh.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

/**
 * [REAL SOURCE CODE - HIGHLY CLASSIFIED]
 * Atmanirbhar Chakravyuh Engine
 * This must be compiled into a secure AAR or JNI native library and shipped externally.
 */
object EnvGuard {
    fun isCompromised(): Boolean {
        return isRooted() || isEmulator() || isDebuggerAttached()
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/sbin/magisk",
            "/data/adb/magisk"
        )
        try {
            for (path in paths) {
                if (File(path).exists()) return true
            }
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            if (reader.readLine() != null) return true
        } catch (e: Exception) {
            // Context ignored
        }
        return false
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
}

object SignatureGuard {
    // The exact SHA-256 fingerprint generated from your release keystore.
    private const val EXPECTED_SIGNATURE_HASH = "YOUR_RELEASE_SHA256_FINGERPRINT_HERE"

    fun isSignatureValid(context: Context): Boolean {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            for (signature in signatures) {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = digest.joinToString("") { "%02x".format(it) }.uppercase()

                // Removing colons if present in EXPECTED string
                val sanitizedExpected = EXPECTED_SIGNATURE_HASH.replace(":", "").uppercase()
                
                if (hexString == sanitizedExpected) {
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
