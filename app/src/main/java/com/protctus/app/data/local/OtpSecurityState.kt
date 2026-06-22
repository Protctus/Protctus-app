package com.protctus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "otp_security_state")
data class OtpSecurityState(
    @PrimaryKey
    val id: Int = 1, // Single row entity
    val hashedOtp: String? = null,
    val otpExpiryTimeMillis: Long = 0L,
    val dailyRequestCount: Int = 0,
    val dayStartTimeMillis: Long = 0L,
    val hourlyRequestCount: Int = 0,
    val hourStartTimeMillis: Long = 0L,
    val wrongAttemptCount: Int = 0,
    val lastRequestTimeMillis: Long = 0L,
    val isTemporarilyBlockedUntilMillis: Long = 0L
)
