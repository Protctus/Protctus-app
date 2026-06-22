package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.protctus.app.BuildConfig
import com.protctus.app.data.local.SettingsDao
import com.protctus.app.network.OtpApiClient
import com.protctus.app.security.OtpRequestResult
import com.protctus.app.security.OtpSecurityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data object SavingProfile : AuthState()
    data object OtpSent : AuthState()
    data class OtpCooldown(val secondsLeft: Int) : AuthState()
    data class OtpVerificationFailed(val message: String) : AuthState()
    data object TwoFactorRequired : AuthState()
    data object Verified : AuthState()
    data object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class UsernameState {
    data object Idle : UsernameState()
    data object Checking : UsernameState()
    data object Available : UsernameState()
    data object Taken : UsernameState()
    data object Invalid : UsernameState()
    data object Saved : UsernameState()
    data class Error(val message: String) : UsernameState()
}

sealed class SplashDestination {
    data object Idle : SplashDestination()
    data object Onboarding : SplashDestination()
    data object Username : SplashDestination()
    data object BiometricUnlock : SplashDestination()
    data object Dashboard : SplashDestination()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val settingsDao: SettingsDao
) : ViewModel() {

    private val securityManager = OtpSecurityManager(appContext, settingsDao)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val isLoading = MutableStateFlow(false)

    private val _usernameState = MutableStateFlow<UsernameState>(UsernameState.Idle)
    val usernameState: StateFlow<UsernameState> = _usernameState.asStateFlow()

    private val _splashDestination = MutableStateFlow<SplashDestination>(SplashDestination.Idle)
    val splashDestination: StateFlow<SplashDestination> = _splashDestination.asStateFlow()

    val isSessionActive: Boolean
        get() = firebaseAuth.currentUser != null

    // Tracking for delayed verification
    var pendingSignUpEmail: String = ""
        private set

    fun setPendingSignUpEmail(email: String) {
        pendingSignUpEmail = email
    }

    // Referral code captured at signup, applied after account creation completes
    var pendingReferralCode: String = ""
        private set

    fun setPendingReferralCode(code: String) {
        pendingReferralCode = code.uppercase().trim()
    }

    private var targetEmail: String? = null
    private var targetPassword: String? = null
    private var cooldownJob: Job? = null

    fun signInWithEmail(email: String, password: String) {
        _authState.value = AuthState.Loading
        
        viewModelScope.launch {
            try {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                // Restore username + displayName from Firestore into local Room DB
                syncProfile()
                // Register device & sync email to Firestore
                com.protctus.app.utils.DeviceRegistrar.registerDevice(appContext)
                
                
                _authState.value = AuthState.Verified
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun initiateSignUp(email: String, password: String) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                // 1. Check all 6 Layers via Manager before sending
                when (val result = securityManager.canRequestOtp()) {
                    is OtpRequestResult.Blocked -> {
                        _authState.value = AuthState.Error(result.reason)
                        if (result.cooldownSeconds != null && result.cooldownSeconds > 0) {
                            // Give UI a tiny delay to collect the Error state for Toast display
                            viewModelScope.launch {
                                delay(200)
                                startCooldown(result.cooldownSeconds)
                            }
                        }
                        return@launch
                    }
                    OtpRequestResult.Allowed -> {
                        // Proceed to send
                    }
                }

                targetEmail = email
                targetPassword = password

                // Send email
                val apiKey = BuildConfig.OTP_API_KEY 
                OtpApiClient.sendOtpEmail(email, apiKey)
                
                // The user explicitly requested: "When I click on verify , just Move to Verification screen"
                // Even if the upstream email provider suppresses the email, we proceed to Verification.
                securityManager.registerSentOtp()
                _authState.value = AuthState.OtpSent
                
                // Start cooldown UI
                val state = settingsDao.getOtpSecurityStateSync()
                val count = state?.dailyRequestCount ?: 1
                val visualCooldown = when (count) {
                    1 -> 90 // 1.5 mins
                    2 -> 1800 // 30 mins
                    3 -> 10800 // 3 hours
                    else -> 10800
                }
                
                // Delay to ensure UI collects the OtpSent state before transitioning
                delay(500)
                startCooldown(visualCooldown)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _authState.value = AuthState.OtpCooldown(i)
                delay(1000)
            }
            _authState.value = AuthState.Idle
        }
    }

    fun authenticateWithEmailOtp(code: String) {
        if (_authState.value is AuthState.Loading || _authState.value is AuthState.SavingProfile || _authState.value is AuthState.Verified) return
        
        _authState.value = AuthState.Loading
        isLoading.value = true
        
        viewModelScope.launch {
            try {
                if (targetEmail == null || targetPassword == null) {
                    _authState.value = AuthState.Error("Context lost. Resend OTP.")
                    return@launch
                }
                
                // ── Step 1: Verify OTP with backend ──
                val apiKey = BuildConfig.OTP_API_KEY
                val isVerifiedRemotely = OtpApiClient.verifyOtpEmail(targetEmail!!, code, targetPassword!!, apiKey)
                
                if (!isVerifiedRemotely) {
                    _authState.value = AuthState.OtpVerificationFailed("Invalid OTP")
                    return@launch
                }
                
                // ── Step 2: Firebase Auth (sign-in or create) ──
                val uid = authenticateFirebaseUser(targetEmail!!, targetPassword!!)
                
                // ── Step 3: Firestore sync (decoupled — handles its own errors) ──
                _authState.value = AuthState.SavingProfile
                syncUserProfileToFirestore(uid)
                
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                // Law 2: Only auth-specific exception maps to OTP failure
                _authState.value = AuthState.OtpVerificationFailed("Invalid OTP")
            } catch (e: FirebaseAuthUserCollisionException) {
                _authState.value = AuthState.Error("Account already exists. Try signing in.")
            } catch (e: Exception) {
                // Catch-all for network timeouts, null UIDs, etc.
                if (_authState.value !is AuthState.Error && _authState.value !is AuthState.Verified) {
                    _authState.value = AuthState.Error(e.message ?: "Authentication failed.")
                }
            } finally {
                // Law 3: UI button NEVER gets permanently disabled
                isLoading.value = false
            }
        }
    }

    /**
     * Pure authentication function. The OTP Worker already created the Firebase user,
     * so we ONLY sign in here — never create.
     */
    private suspend fun authenticateFirebaseUser(email: String, password: String): String {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        return result.user?.uid ?: throw Exception("User ID null after authentication")
    }

    private suspend fun syncUserProfileToFirestore(uid: String) {
        try {
            val userBase = hashMapOf(
                "email" to targetEmail,
                "createdAt" to System.currentTimeMillis()
            )
            // Fire-and-forget: Firestore offline cache will sync when connected.
            // Do NOT .await() here — it blocks if gRPC channel isn't warmed up yet.
            // Also include the Firebase Auth email explicitly
            firestore.collection("users").document(uid)
                .set(userBase, com.google.firebase.firestore.SetOptions.merge())
            
            // Also sync profile locally
            syncProfile()
            // Register device to Firestore
            com.protctus.app.utils.DeviceRegistrar.registerDevice(appContext)
            
            _authState.value = AuthState.Verified
            targetEmail = null
            targetPassword = null
            cooldownJob?.cancel()
        } catch (e: Exception) {
            // Auth already succeeded; profile sync is best-effort
            _authState.value = AuthState.Verified
            targetEmail = null
            targetPassword = null
            cooldownJob?.cancel()
        } finally {
            isLoading.value = false
        }
    }

    fun resendOtp() {
        val email = targetEmail
        val password = targetPassword
        if (email != null && password != null) {
            initiateSignUp(email, password)
        } else {
            _authState.value = AuthState.Error("Cannot resend. Context lost.")
        }
    }

    // ... Username logic remains same
    fun validateUsername(username: String) {
        val regex = Regex("^[a-zA-Z0-9._]{3,20}$")
        if (!regex.matches(username)) {
            _usernameState.value = UsernameState.Invalid
            return
        }
        _usernameState.value = UsernameState.Checking
        viewModelScope.launch {
            try {
                val app = com.google.firebase.FirebaseApp.getInstance()
                val db = FirebaseFirestore.getInstance(app, "protctus")
                val doc = db.collection("usernames").document(username.lowercase()).get().await()
                if (doc.exists()) {
                    _usernameState.value = UsernameState.Taken
                } else {
                    _usernameState.value = UsernameState.Available
                }
            } catch (e: Exception) {
                _usernameState.value = UsernameState.Error(e.message ?: "Check failed")
            }
        }
    }

    fun saveUsername(username: String) {
        _usernameState.value = UsernameState.Checking
        viewModelScope.launch {
            try {
                val uid = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
                val lowered = username.lowercase()

                val app = com.google.firebase.FirebaseApp.getInstance()
                val db = FirebaseFirestore.getInstance(app, "protctus")

                // Fire-and-forget: Firestore offline cache syncs when connected
                db.collection("users").document(uid)
                    .set(hashMapOf("username" to lowered), com.google.firebase.firestore.SetOptions.merge())

                db.collection("usernames").document(lowered)
                    .set(hashMapOf("uid" to uid))

                // Crucial: Update local profile
                val profile = settingsDao.getProfileSync() ?: com.protctus.app.data.local.UserProfile()
                settingsDao.upsertProfile(profile.copy(username = lowered))

                _usernameState.value = UsernameState.Saved
            } catch (e: Exception) {
                _usernameState.value = UsernameState.Error(e.message ?: "Save failed")
            }
        }
    }

    /**
     * Syncs the user profile (username) from Firestore to the local database.
     * Called during app startup if local profile is incomplete.
     * Now a suspended function so we can definitively await it before routing.
     */
    suspend fun syncProfile() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val firebaseEmail = firebaseAuth.currentUser?.email ?: ""
        
        try {
            val doc = firestore.collection("users").document(uid).get().await()
            val firestoreUsername = doc.getString("username")
            val firestoreDisplayName = doc.getString("displayName")
            val firestoreProfileImageUrl = doc.getString("profileImageUrl")
            
            val localProfile = settingsDao.getProfileSync() ?: com.protctus.app.data.local.UserProfile()
            var updated = localProfile
            
            // Always sync email from FirebaseAuth
            if (firebaseEmail.isNotBlank() && updated.email != firebaseEmail) {
                updated = updated.copy(email = firebaseEmail)
            }
            if (!firestoreUsername.isNullOrBlank() && updated.username != firestoreUsername) {
                updated = updated.copy(username = firestoreUsername)
            }
            if (!firestoreDisplayName.isNullOrBlank() && updated.displayName != firestoreDisplayName) {
                updated = updated.copy(displayName = firestoreDisplayName)
            }
            if (!firestoreProfileImageUrl.isNullOrBlank() && updated.profileImageUri != firestoreProfileImageUrl) {
                updated = updated.copy(profileImageUri = firestoreProfileImageUrl)
            }
            
            if (updated != localProfile) {
                settingsDao.upsertProfile(updated)
            }
        } catch (e: Exception) {
            // Silent fail for sync, we'll ask for username if still missing
            // Still try to sync email even if Firestore fails
            try {
                if (firebaseEmail.isNotBlank()) {
                    val localProfile = settingsDao.getProfileSync() ?: com.protctus.app.data.local.UserProfile()
                    if (localProfile.email != firebaseEmail) {
                        settingsDao.upsertProfile(localProfile.copy(email = firebaseEmail))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun logout() {
        viewModelScope.launch {
            securityManager.clearSecurityState()
        }
        firebaseAuth.signOut()
        _authState.value = AuthState.Idle
    }

    fun resetState() {
        if (_authState.value !is AuthState.OtpCooldown) {
            _authState.value = AuthState.Idle
        }
    }

    fun resetUsernameState() {
        _usernameState.value = UsernameState.Idle
    }

    fun determineSplashDestination() {
        viewModelScope.launch {
            val user = firebaseAuth.currentUser
            if (user == null) {
                _splashDestination.value = SplashDestination.Onboarding
                return@launch
            }

            var profile = settingsDao.getProfileSync()
            var hasUsername = !profile?.username.isNullOrBlank()

            if (!hasUsername) {
                try {
                    syncProfile()
                } catch (e: Exception) {
                    // Ignore sync errors during splash
                }
            }

            val settingsManager = com.protctus.app.data.local.SettingsManager(appContext)
            val isBiometricEnabled = settingsManager.isBiometricEnabled.first()
            
            // SECURITY: Never let nameless profiles into the dashboard
            if (!hasUsername) {
                _splashDestination.value = SplashDestination.Username
            } else if (isBiometricEnabled) {
                _splashDestination.value = SplashDestination.BiometricUnlock
            } else {
                _splashDestination.value = SplashDestination.Dashboard
            }
        }
    }
}