package com.protctus.app.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ReferralStats(
    val referralCode: String = "",
    val totalReferrals: Int = 0,
    val xpEarned: Int = 0
)

@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _stats = MutableStateFlow(ReferralStats())
    val stats: StateFlow<ReferralStats> = _stats.asStateFlow()

    init {
        loadReferralData()
    }

    private fun generateReferralCode(uid: String): String {
        // SHA-256 based — collision-resistant unique code per Firebase UID
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 31 chars (no I/O/0/1 to avoid confusion)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(uid.toByteArray(Charsets.UTF_8))
        return buildString {
            repeat(8) { i ->
                // Use each hash byte to pick a character (mod 31)
                val value = (hashBytes[i].toInt() and 0xFF)
                append(chars[value % chars.length])
            }
        }
    }

    private fun loadReferralData() {
        val uid = auth.currentUser?.uid ?: return
        val code = generateReferralCode(uid)

        viewModelScope.launch {
            try {
                val doc = firestore.collection("referrals").document(uid).get().await()
                val totalReferrals = doc.getLong("totalReferrals")?.toInt() ?: 0
                val xpEarned = doc.getLong("xpEarned")?.toInt() ?: 0

                _stats.value = ReferralStats(
                    referralCode = code,
                    totalReferrals = totalReferrals,
                    xpEarned = xpEarned
                )
            } catch (e: Exception) {
                _stats.value = ReferralStats(referralCode = code)
            }
        }
    }

    fun applyReferralCode(code: String, onResult: (Boolean, String) -> Unit) {
        val currentUid = auth.currentUser?.uid ?: return onResult(false, "Not logged in")

        viewModelScope.launch {
            try {
                // Find the owner of the referral code
                val query = firestore.collection("users").get().await()
                val ownerDoc = query.documents.find { doc ->
                    doc.id.let { generateReferralCode(it) } == code.uppercase()
                }

                if (ownerDoc == null) {
                    onResult(false, "Invalid referral code")
                    return@launch
                }

                if (ownerDoc.id == currentUid) {
                    onResult(false, "You can't use your own code!")
                    return@launch
                }

                // Check if already used a referral
                val myDoc = firestore.collection("users").document(currentUid).get().await()
                if (myDoc.getString("appliedReferral") != null) {
                    onResult(false, "You've already used a referral code")
                    return@launch
                }

                val XP_PER_REFERRAL = 10

                // Batch: mark current user as referred, award XP to BOTH parties
                firestore.runBatch { batch ->
                    // Mark current user + give them 10 XP
                    batch.update(
                        firestore.collection("users").document(currentUid),
                        mapOf(
                            "appliedReferral" to code,
                            "xp" to com.google.firebase.firestore.FieldValue.increment(XP_PER_REFERRAL.toLong())
                        )
                    )
                    // Award the referrer 10 XP
                    batch.update(
                        firestore.collection("users").document(ownerDoc.id),
                        "xp", com.google.firebase.firestore.FieldValue.increment(XP_PER_REFERRAL.toLong())
                    )
                    // Track referral stats
                    batch.set(
                        firestore.collection("referrals").document(ownerDoc.id),
                        mapOf(
                            "totalReferrals" to com.google.firebase.firestore.FieldValue.increment(1),
                            "xpEarned" to com.google.firebase.firestore.FieldValue.increment(XP_PER_REFERRAL.toLong())
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }.await()

                onResult(true, "Referral applied! You both earned +$XP_PER_REFERRAL XP 🎉")
            } catch (e: Exception) {
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    fun getShareIntent(context: android.content.Context): Intent {
        val code = _stats.value.referralCode
        val text = "Join me on Protctus — the #1 AI Cybersecurity App for Android!\n\n" +
                "Use my referral code: $code\n\n" +
                "Download the app and keep your digital life secure."
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
}
