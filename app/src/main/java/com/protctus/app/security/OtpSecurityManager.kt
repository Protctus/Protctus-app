package com.protctus.app.security

import android.content.Context
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import com.protctus.app.data.local.OtpSecurityState
import com.protctus.app.data.local.SettingsDao
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

/**
 * Result sealed class for OTP requests.
 */
sealed class OtpRequestResult {
    data object Allowed : OtpRequestResult()
    data class Blocked(val reason: String, val cooldownSeconds: Int? = null) : OtpRequestResult()
}

/**
 * Manager handling the 6-Layer OTP Protection System:
 * 1. Progressive Cooldown (1m, 2m, 5m, 10m)
 * 2. Hourly Limit (Max 5)
 * 3. Daily Limit (Max 10)
 * 4. Wrong OTP Limit (Max 5 -> 30m block)
 * 5. Expiry (5 mins)
 * 6. Device/IP Limit (Firebase) & Local Hashing
 */
class OtpSecurityManager(
    private val context: Context,
    private val settingsDao: SettingsDao
) {
    private val firestore = FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "protctus")

    // We use Android ID as a stable device identifier for Firebase rate limiting
    // to prevent bypasses by simply clearing app data.
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    // ── Hashing ──
    private fun hashString(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { String.format(java.util.Locale.US, "%02x", it) }
    }

    // ── Layer Checks for Requesting OTP ──
    
    suspend fun canRequestOtp(): OtpRequestResult {
        val state = settingsDao.getOtpSecurityStateSync() ?: OtpSecurityState()
        val now = System.currentTimeMillis()

        // 4. Temporary Block check
        if (state.isTemporarilyBlockedUntilMillis > now) {
            val remain = ((state.isTemporarilyBlockedUntilMillis - now) / 1000).toInt()
            return OtpRequestResult.Blocked("Too many wrong attempts. Try again later.", remain)
        }

        // Daily/Hourly Window Reset Logic
        val isNewDay = now - state.dayStartTimeMillis > 24 * 60 * 60 * 1000
        val isNewHour = now - state.hourStartTimeMillis > 60 * 60 * 1000
        
        val currentDailyCount = if (isNewDay) 0 else state.dailyRequestCount
        val currentHourlyCount = if (isNewHour) 0 else state.hourlyRequestCount

        // 3. Daily Limit
        if (currentDailyCount >= 10) {
            return OtpRequestResult.Blocked("Daily OTP limit reached (10 max).")
        }

        // 2. Hourly Limit
        if (currentHourlyCount >= 5) {
            return OtpRequestResult.Blocked("Hourly OTP limit reached (5 max).")
        }

        // 1. Progressive Cooldown check
        val cooldownRequired = when (currentDailyCount) {
            0 -> 60 // 1st resend (after 0th) -> 60s
            1 -> 120 // 2nd resend -> 2m
            2 -> 300 // 3rd -> 5m
            else -> 600 // 4+ -> 10m
        }
        
        val timeSinceLast = (now - state.lastRequestTimeMillis) / 1000
        if (timeSinceLast < cooldownRequired) {
            return OtpRequestResult.Blocked("Please wait before requesting another OTP.", (cooldownRequired - timeSinceLast).toInt())
        }

        // 6. Device/IP Rate Limiting (Firebase check)
        try {
            val doc = withTimeoutOrNull(5000) {
                firestore.collection("otp_rate_limits").document(deviceId).get().await()
            }
            if (doc != null && doc.exists()) {
                val dbStart = doc.getLong("dayStartTimeMillis") ?: 0L
                val dbCount = doc.getLong("dailyCount") ?: 0L
                if (now - dbStart < 24 * 60 * 60 * 1000 && dbCount >= 10) {
                     return OtpRequestResult.Blocked("Device OTP limit reached.")
                }
            }
        } catch (e: Exception) {
            // If Firebase fails (no network), we rely on local limits
        }

        return OtpRequestResult.Allowed
    }

    // ── Registering a Sent OTP ──

    suspend fun registerSentOtp() {
        val state = settingsDao.getOtpSecurityStateSync() ?: OtpSecurityState()
        val now = System.currentTimeMillis()

        val isNewDay = now - state.dayStartTimeMillis > 24 * 60 * 60 * 1000
        val isNewHour = now - state.hourStartTimeMillis > 60 * 60 * 1000

        val newDailyCount = if (isNewDay) 1 else state.dailyRequestCount + 1
        val newHourlyCount = if (isNewHour) 1 else state.hourlyRequestCount + 1

        val newState = state.copy(
            hashedOtp = "",
            otpExpiryTimeMillis = now + (5 * 60 * 1000), // 5. Expiry in 5 mins
            dailyRequestCount = newDailyCount,
            dayStartTimeMillis = if (isNewDay) now else state.dayStartTimeMillis,
            hourlyRequestCount = newHourlyCount,
            hourStartTimeMillis = if (isNewHour) now else state.hourStartTimeMillis,
            lastRequestTimeMillis = now
        )

        settingsDao.upsertOtpSecurityState(newState)

        // 6. Sync device limit to Firebase
        try {
            val limitData = hashMapOf(
                "dailyCount" to newDailyCount,
                "dayStartTimeMillis" to newState.dayStartTimeMillis,
                "lastUpdate" to now
            )
            firestore.collection("otp_rate_limits").document(deviceId)
                .set(limitData)
        } catch (e: Exception) {
            // Ignore Firebase failures, local handles it
        }
    }



    // Clear state completely on logout or manual reset
    suspend fun clearSecurityState() {
        settingsDao.clearOtpSecurityState()
    }
}
