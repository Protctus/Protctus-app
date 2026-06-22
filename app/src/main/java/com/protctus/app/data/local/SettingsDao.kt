package com.protctus.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    // ── UserSettings ──

    @Query("SELECT * FROM user_settings WHERE id = 1")
    fun getSettings(): Flow<UserSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: UserSettings)

    // ── UserProfile ──

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileSync(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfile)

    @Query("UPDATE user_profile SET profileImageUri = NULL WHERE id = 1")
    suspend fun removeProfileImage()

    @Query("DELETE FROM user_settings")
    suspend fun clearSettings()

    @Query("DELETE FROM user_profile")
    suspend fun clearProfile()

    // ── OTP Security ──

    @Query("SELECT * FROM otp_security_state WHERE id = 1")
    fun getOtpSecurityState(): Flow<OtpSecurityState?>

    @Query("SELECT * FROM otp_security_state WHERE id = 1")
    suspend fun getOtpSecurityStateSync(): OtpSecurityState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOtpSecurityState(state: OtpSecurityState)

    @Query("DELETE FROM otp_security_state")
    suspend fun clearOtpSecurityState()
}
