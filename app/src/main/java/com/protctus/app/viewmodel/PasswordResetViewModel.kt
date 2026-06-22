package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.protctus.app.BuildConfig
import com.protctus.app.data.local.SettingsDao
import com.protctus.app.network.OtpApiClient
import com.protctus.app.security.OtpRequestResult
import com.protctus.app.security.OtpSecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class PasswordResetState {
    data object Idle : PasswordResetState()
    data object Loading : PasswordResetState()
    data object OtpSent : PasswordResetState()
    data class OtpCooldown(val secondsLeft: Int) : PasswordResetState()
    data object OtpVerified : PasswordResetState()
    data object PasswordChanged : PasswordResetState()
    data class EmailChangeSuccess(val message: String) : PasswordResetState()
    data class Error(val message: String) : PasswordResetState()
}

@HiltViewModel
class PasswordResetViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val settingsDao: SettingsDao
) : ViewModel() {

    private val securityManager = OtpSecurityManager(appContext, settingsDao)

    private val _state = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val state: StateFlow<PasswordResetState> = _state.asStateFlow()

    var targetEmail: String? = null
        private set
    private var cooldownJob: Job? = null

    // ── Step 1: Send OTP to email via Resend ──

    fun sendOtpToEmail(email: String) {
        if (_state.value is PasswordResetState.Loading) return
        if (_state.value is PasswordResetState.OtpCooldown) return
        
        _state.value = PasswordResetState.Loading
        viewModelScope.launch {
            try {
                // 1. Check all 6 Layers via Manager before sending
                when (val result = securityManager.canRequestOtp()) {
                    is OtpRequestResult.Blocked -> {
                        if (result.cooldownSeconds != null && result.cooldownSeconds > 0) {
                            startCooldown(result.cooldownSeconds)
                            return@launch
                        } else {
                            _state.value = PasswordResetState.Error(result.reason)
                            return@launch
                        }
                    }
                    OtpRequestResult.Allowed -> {
                        // Proceed to send
                    }
                }

                targetEmail = email

                val apiKey = BuildConfig.OTP_API_KEY
                val success = OtpApiClient.sendOtpEmail(email, apiKey)

                if (success) {
                    securityManager.registerSentOtp()
                    _state.value = PasswordResetState.OtpSent
                    
                    val securityState = settingsDao.getOtpSecurityStateSync()
                    val count = securityState?.dailyRequestCount ?: 1
                    val visualCooldown = when (count) {
                        1 -> 90 // 1.5 mins
                        2 -> 1800 // 30 mins
                        3 -> 10800 // 3 hours
                        else -> 10800
                    }
                    startCooldown(visualCooldown)
                } else {
                    _state.value = PasswordResetState.Error("Failed to send verification email.")
                }
            } catch (e: Exception) {
                _state.value = PasswordResetState.Error(e.message ?: "Failed to send OTP.")
            }
        }
    }

    private var verifiedOtpCode: String? = null

    // ── Step 2: Verify OTP ──

    fun verifyOtp(enteredOtp: String) {
        if (_state.value is PasswordResetState.Loading || _state.value is PasswordResetState.OtpVerified) return
        
        _state.value = PasswordResetState.Loading
        viewModelScope.launch {
            try {
                if (targetEmail != null) {
                    val apiKey = BuildConfig.OTP_API_KEY
                    val isVerifiedRemotely = OtpApiClient.verifyOtpEmail(targetEmail!!, enteredOtp, "", apiKey)
                    
                    if (isVerifiedRemotely) {
                        cooldownJob?.cancel()
                        verifiedOtpCode = enteredOtp
                        _state.value = PasswordResetState.OtpVerified
                    } else {
                        _state.value = PasswordResetState.Error("Invalid OTP code.")
                    }
                } else {
                    _state.value = PasswordResetState.Error("Context lost. Resend OTP.")
                }
            } catch (e: Exception) {
                _state.value = PasswordResetState.Error("Verification failed.")
            }
        }
    }

    // ── Step 3: Set new password after OTP verified ──

    fun setNewPasswordWithOtp(newPassword: String) {
        if (_state.value is PasswordResetState.Loading) return
        if (verifiedOtpCode == null) {
            _state.value = PasswordResetState.Error("OTP not verified yet.")
            return
        }
        _state.value = PasswordResetState.Loading
        viewModelScope.launch {
            try {
                // Pass the new password to the Cloudflare Worker which holds Firebase Admin SDK privileges
                val apiKey = BuildConfig.OTP_API_KEY
                val isChanged = OtpApiClient.verifyOtpEmail(targetEmail!!, verifiedOtpCode!!, newPassword, apiKey)
                
                if (isChanged) {
                    // Success: scrub state
                    securityManager.clearSecurityState()
                    _state.value = PasswordResetState.PasswordChanged
                    targetEmail = null
                    verifiedOtpCode = null
                } else {
                    _state.value = PasswordResetState.Error("Failed to update password securely. Try again.")
                }
            } catch (e: Exception) {
                _state.value = PasswordResetState.Error(e.message ?: "Failed to set new password.")
            }
        }
    }

    // ── Email Change ──

    fun changeEmail(currentPassword: String, newEmail: String) {
        if (_state.value is PasswordResetState.Loading) return
        _state.value = PasswordResetState.Loading
        viewModelScope.launch {
            try {
                val user = auth.currentUser ?: throw Exception("Not authenticated")
                val oldEmail = user.email ?: throw Exception("No email on account")

                // Step 1: Re-authenticate to prove identity
                val credential = EmailAuthProvider.getCredential(oldEmail, currentPassword)
                user.reauthenticate(credential).await()

                // Step 2: Update Firebase Authentication seamlessly
                user.verifyBeforeUpdateEmail(newEmail).await()

                // Step 3: Sync to centralized Firestore Database
                val updates = hashMapOf<String, Any>("email" to newEmail)
                firestore.collection("users").document(user.uid).update(updates).await()

                // Step 4: Sync to offline Local SQLite Database (Room)
                val profile = settingsDao.getProfileSync()
                if (profile != null) {
                    settingsDao.upsertProfile(profile.copy(email = newEmail))
                }

                _state.value = PasswordResetState.EmailChangeSuccess("Email updated successfully!")
            } catch (e: Exception) {
                // If updateEmail is blocked by Firebase policy, fallback to verification native flow
                if (e.message?.contains("verifyBeforeUpdateEmail") == true) {
                    try {
                        val user = auth.currentUser!!
                        user.verifyBeforeUpdateEmail(newEmail).await()
                        _state.value = PasswordResetState.EmailChangeSuccess("Verification link sent to your new email. Please verify.")
                    } catch (nested: Exception) {
                        _state.value = PasswordResetState.Error(nested.message ?: "Email change failed.")
                    }
                } else {
                    _state.value = PasswordResetState.Error(e.message ?: "Authentication failed. Check your current password.")
                }
            }
        }
    }

    // ── Resend OTP ──

    fun resendOtp() {
        val email = targetEmail
        if (email != null) {
            sendOtpToEmail(email)
        } else {
            _state.value = PasswordResetState.Error("No email set. Go back and try again.")
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _state.value = PasswordResetState.OtpCooldown(i)
                delay(1000)
            }
            _state.value = PasswordResetState.OtpSent
        }
    }

    fun resetState() {
        if (_state.value !is PasswordResetState.OtpCooldown) {
            _state.value = PasswordResetState.Idle
        }
    }

    fun fullReset() {
        cooldownJob?.cancel()
        _state.value = PasswordResetState.Idle
        targetEmail = null
        // We do NOT clear SecurityManager state here, otherwise they could bypass limits
    }
}
