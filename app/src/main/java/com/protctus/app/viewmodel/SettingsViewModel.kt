package com.protctus.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.protctus.app.data.local.SettingsDao
import com.protctus.app.data.local.SettingsManager
import com.protctus.app.data.local.UserProfile
import com.protctus.app.data.local.UserSettings
import com.protctus.app.data.model.SubscriptionTier
import com.protctus.app.data.repository.EntitlementManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dao: SettingsDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val settingsManager: SettingsManager,
    private val entitlementManager: EntitlementManager
) : ViewModel() {

    val settings: StateFlow<UserSettings> = dao.getSettings()
        .map { it ?: UserSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val profile: StateFlow<UserProfile> = dao.getProfile()
        .map { it ?: UserProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    // Firebase-fetched rank — dynamically computed from XP
    private val _userRank = MutableStateFlow("Cyber Novice")
    val userRank: StateFlow<String> = _userRank.asStateFlow()

    val currentTier: StateFlow<SubscriptionTier> = entitlementManager.currentTier

    val isBiometricEnabled: StateFlow<Boolean> = settingsManager.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true) // Default ON
        
    val isRealTimeProtectionEnabled: StateFlow<Boolean> = settingsManager.isRealTimeProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        
    val isPhishingShieldEnabled: StateFlow<Boolean> = settingsManager.isPhishingShieldEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLanguage: StateFlow<String> = settingsManager.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")


    // ── Settings Toggles ──

    fun toggleNotifications() = viewModelScope.launch {
        val current = settings.value
        dao.upsertSettings(current.copy(notificationsEnabled = !current.notificationsEnabled))
    }

    fun toggleBiometricLogin() = viewModelScope.launch {
        val current = isBiometricEnabled.value
        settingsManager.setBiometricEnabled(!current)
    }

    fun toggleRealTimeProtection(context: android.content.Context) = viewModelScope.launch {
        val current = isRealTimeProtectionEnabled.value
        settingsManager.setRealTimeProtectionEnabled(!current)
        
        val serviceIntent = android.content.Intent(context, com.protctus.app.services.SentinelService::class.java)
        if (!current) {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.stopService(serviceIntent)
        }
    }
    
    fun setPhishingShieldEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsManager.setPhishingShieldEnabled(enabled)
    }

    fun updateLanguage(code: String) = viewModelScope.launch {
        settingsManager.setAppLanguage(code)
        
        // Apply immediately
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(code)
        )
        
        // Sync to Firestore
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .set(mapOf("language" to code), com.google.firebase.firestore.SetOptions.merge())
        }
    }



    fun toggleDataSharing() = viewModelScope.launch {
        val current = settings.value
        dao.upsertSettings(current.copy(dataSharingEnabled = !current.dataSharingEnabled))
    }

    fun toggleAdTracking() = viewModelScope.launch {
        val current = settings.value
        dao.upsertSettings(current.copy(adTrackingEnabled = !current.adTrackingEnabled))
    }

    // ── Profile ──

    fun updateProfile(name: String) = viewModelScope.launch {
        val current = profile.value
        dao.upsertProfile(current.copy(displayName = name))
        // Sync to Firestore to prevent data loss on logout/reinstall
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid)
                .set(mapOf("displayName" to name), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    fun updateProfileImage(localPath: String) = viewModelScope.launch {
        val current = profile.value
        // Save locally immediately for instant UI feedback
        dao.upsertProfile(current.copy(profileImageUri = localPath))
        
        // Upload to Firebase Storage and save download URL to Firestore
        val uid = auth.currentUser?.uid ?: return@launch
        try {
            val file = File(localPath)
            val storageRef = FirebaseStorage.getInstance().reference
                .child("profile_images/$uid/profile_pic.jpg")
            storageRef.putFile(Uri.fromFile(file)).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            firestore.collection("users").document(uid)
                .set(mapOf("profileImageUrl" to downloadUrl), com.google.firebase.firestore.SetOptions.merge())
                .await()
        } catch (_: Exception) {
            // Local save succeeded; cloud upload is best-effort
        }
    }

    fun removeProfileImage() = viewModelScope.launch {
        dao.removeProfileImage()
        
        // Remove from Firestore and Storage
        val uid = auth.currentUser?.uid ?: return@launch
        try {
            firestore.collection("users").document(uid)
                .update("profileImageUrl", com.google.firebase.firestore.FieldValue.delete())
                .await()
            FirebaseStorage.getInstance().reference
                .child("profile_images/$uid/profile_pic.jpg")
                .delete().await()
        } catch (_: Exception) { }
    }

    // ── Account Actions ──

    fun deleteAccount() = viewModelScope.launch {
        dao.clearSettings()
        dao.clearProfile()
    }

    fun logout() = viewModelScope.launch {
        dao.clearSettings()
        dao.clearProfile()
        auth.signOut()
    }

    // ── XP-based Rank Thresholds (mirrors RewardsViewModel) ──
    
    private val rankThresholds = listOf(
        0 to "Cyber Novice",
        50 to "Fortress Builder",
        250 to "White Hat Hero",
        500 to "Ghost Mode",
        750 to "Privacy Shield",
        900 to "Virus Slayer",
        1200 to "Data Guardian",
        1500 to "Network Warden",
        2000 to "Anonymity Ace"
    )
    
    private fun computeRankFromXp(xp: Int): String {
        var rank = rankThresholds.first().second
        for ((threshold, title) in rankThresholds) {
            if (xp >= threshold) rank = title
        }
        return rank
    }

    // ── Firebase Rank Fetch ──

    private var rankListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun fetchUserRank() {
        val uid = auth.currentUser?.uid ?: return
        // Cancel any pre-existing listener to prevent duplicates
        rankListener?.remove()
        rankListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                // Compute rank dynamically from XP
                val xp = snapshot?.getLong("xp")?.toInt() ?: 0
                _userRank.value = computeRankFromXp(xp)
                
                val cloudUsername = snapshot?.getString("username")
                val cloudDisplayName = snapshot?.getString("displayName")
                val cloudProfileImageUrl = snapshot?.getString("profileImageUrl")
                val cloudLanguage = snapshot?.getString("language")

                viewModelScope.launch {
                    val currentProfile = dao.getProfileSync() ?: UserProfile()
                    var updated = currentProfile
                    
                    val currentLanguage = settingsManager.appLanguage.first()
                    if (!cloudLanguage.isNullOrBlank() && currentLanguage != cloudLanguage) {
                        settingsManager.setAppLanguage(cloudLanguage)
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.forLanguageTags(cloudLanguage)
                        )
                    }

                    if (!cloudUsername.isNullOrBlank() && updated.username != cloudUsername) {
                        updated = updated.copy(username = cloudUsername)
                    }
                    if (!cloudDisplayName.isNullOrBlank() && updated.displayName != cloudDisplayName) {
                        updated = updated.copy(displayName = cloudDisplayName)
                    }
                    // Restore profile image URL from cloud if local is missing
                    if (!cloudProfileImageUrl.isNullOrBlank() && updated.profileImageUri != cloudProfileImageUrl) {
                        updated = updated.copy(profileImageUri = cloudProfileImageUrl)
                    }
                    
                    // Sync email from FirebaseAuth
                    val firebaseEmail = auth.currentUser?.email ?: ""
                    if (firebaseEmail.isNotBlank() && updated.email != firebaseEmail) {
                        updated = updated.copy(email = firebaseEmail)
                    }
                    
                    if (updated != currentProfile) {
                        dao.upsertProfile(updated)
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        rankListener?.remove()
    }

    // Initialize
    init {
        fetchUserRank()
    }
}
