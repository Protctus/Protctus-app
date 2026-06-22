package com.protctus.app.repository

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String,
    val username: String,
    val totalXp: Int
)

class GamificationRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "protctus")
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    init {
        // Law 3: Explicitly enable offline persistence
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
            firestore.firestoreSettings = settings
        } catch (e: Exception) {
            Log.w("GamificationRepo", "Firestore settings already initialized or failed: ${e.message}")
        }
    }

    /**
     * Law 1: Single Source of Truth Cache.
     * Fetches user profile down once, caching it perfectly inside the MutableStateFlow.
     */
    suspend fun fetchUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("GamificationRepo", "Cannot fetch profile: User not logged in.")
            return
        }

        try {
            val documentSnapshot = firestore.collection("users").document(currentUser.uid).get().await()

            if (documentSnapshot.exists()) {
                val username = documentSnapshot.getString("username") ?: documentSnapshot.getString("name") ?: "Agent"
                val totalXp = documentSnapshot.getLong("total_xp")?.toInt() ?: documentSnapshot.getLong("xp")?.toInt() ?: 0

                _userProfile.value = UserProfile(
                    uid = currentUser.uid,
                    username = username,
                    totalXp = totalXp
                )
                Log.d("GamificationRepo", "Successfully cached user profile from Firestore.")
            } else {
                Log.w("GamificationRepo", "User document does not exist.")
            }
        } catch (e: FirebaseFirestoreException) {
            Log.e("GamificationRepo", "Firestore error fetching profile: ${e.code} - ${e.message}")
        } catch (e: Exception) {
            Log.e("GamificationRepo", "General Error fetching profile: ${e.message}")
        }
    }

    /**
     * Law 2: Atomic XP Increments (CRITICAL)
     */
    suspend fun addXp(amount: Int) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("GamificationRepo", "Cannot add XP: User not logged in.")
            return
        }

        if (amount <= 0) return

        try {
            // 1. Strictly bypass local arrays and write securely via FieldValue to Cloud
            firestore.collection("users").document(currentUser.uid)
                .update("total_xp", FieldValue.increment(amount.toLong()))
                .await()
            
            Log.d("GamificationRepo", "Atomically incremented $amount XP in Firestore.")

            // 2. Safely mutate the SSOT StateFlow eliminating an unnecessary cloud read penalty!
            _userProfile.update { currentProfile ->
                currentProfile?.copy(totalXp = currentProfile.totalXp + amount)
            }
            
        } catch (e: FirebaseFirestoreException) {
            Log.e("GamificationRepo", "Firestore error adding XP: ${e.code} - ${e.message}")
        } catch (e: Exception) {
            Log.e("GamificationRepo", "General Error adding XP: ${e.message}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: GamificationRepository? = null

        fun getInstance(): GamificationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GamificationRepository().also { INSTANCE = it }
            }
        }
    }
}
