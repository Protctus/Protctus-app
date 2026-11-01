package com.protctus.app.security

import android.content.Context
import android.util.Log
import com.protctus.app.BuildConfig

@androidx.annotation.Keep
object ChakravyuhNative {

    init {
        try {
            System.loadLibrary("chakravyuh")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("ChakravyuhNative", "Native library failed to load.", e)
        }
    }

    @androidx.annotation.Keep
    external fun verifyEnvironment(context: Context, isDebugBuild: Boolean): Boolean

    
    fun runSilentCheck(context: Context): Boolean {
        return try {
            // P0 FIX: Pass BuildConfig.DEBUG so native code enforces root/emulator 
            // detection ONLY in release builds, preventing developer lockout.
            verifyEnvironment(context, BuildConfig.DEBUG)
        } catch (e: UnsatisfiedLinkError) {
            Log.w("ChakravyuhNative", "Skipping native security checks as lib is missing")
            false // return false so we don't block the user unnecessarily
        } catch (e: Exception) {
            false
        }
    }
}
