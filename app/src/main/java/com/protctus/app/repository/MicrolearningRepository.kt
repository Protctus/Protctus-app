package com.protctus.app.repository

import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.protctus.app.data.local.dao.MicrolearningCardDao
import com.protctus.app.data.local.entity.MicrolearningCardData
import com.protctus.app.network.SecureHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CS-3.1 fix: Single source of truth for all Microlearning data operations.
 * 
 * - No Context dependency (CS-1.2 fix)
 * - All DB/network interactions live here, not in the ViewModel
 * - Sync has a TTL to prevent hammering on every init (CS-3.3 fix)
 */
@Singleton
class MicrolearningRepository @Inject constructor(
    private val dao: MicrolearningCardDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "MicrolearningRepo"
        private const val SYNC_TTL_MS = 3_600_000L // 1 hour
        private const val PREF_LAST_SYNC = "microlearning_last_sync_epoch"
        private const val FEED_URL = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev/lab/feed"
    }

    // ── Flow Queries (exposed to ViewModel) ──

    fun getAllCardsFlow(): Flow<List<MicrolearningCardData>> = dao.getAllCardsFlow()

    fun getCardsByCategory(category: String): Flow<List<MicrolearningCardData>> = dao.getCardsByCategory(category)

    fun getDistinctCategories(): Flow<List<String>> = dao.getDistinctCategories()

    fun getUserStatsFlow() = dao.getUserStatsFlow()

    // ── Card Completion (Offline-First + Cloud Sync) ──

    /**
     * Marks a card completed locally (Room), unlocks the next card in sequence,
     * and pushes the completion event to Firestore in the background.
     * Returns the remaining uncompleted count for the card's category.
     */
    suspend fun completeCard(card: MicrolearningCardData, xpReward: Int): Int = withContext(Dispatchers.IO) {
        // 1. Local Room update (atomic transaction)
        dao.completeAndUnlockNext(
            topicId = card.id,
            categoryId = card.category,
            currentOrderIndex = card.orderIndex,
            xpReward = xpReward
        )

        // 2. Cloud sync (best-effort, does not block UX)
        try {
            auth.currentUser?.uid?.let { uid ->
                firestore.collection("users").document(uid)
                    .update(
                        "completed_topics", FieldValue.arrayUnion(card.id),
                        "total_xp", FieldValue.increment(xpReward.toLong())
                    )
                    .await()
                Log.d(TAG, "Card ${card.id} synced to cloud with +$xpReward XP.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync failed for card ${card.id}, will retry on next login.", e)
        }

        // 3. Return remaining count for quota/category-completion checks
        dao.getUncompletedCountByCategory(card.category)
    }

    suspend fun addXp(amount: Int) = withContext(Dispatchers.IO) {
        dao.addXp(amount)
        try {
            auth.currentUser?.uid?.let { uid ->
                firestore.collection("users").document(uid)
                    .update("total_xp", FieldValue.increment(amount.toLong()))
                    .await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud sync failed for addXp", e)
        }
    }

    // ── Remote Sync with TTL (CS-3.3 fix) ──

    /**
     * Syncs microlearning cards from the remote API.
     * Skips the network call if the last successful sync was within [SYNC_TTL_MS].
     * Uses [smartUpsert] to never overwrite local completion state (CS-4.5 fix).
     */
    suspend fun syncFeedIfStale() = withContext(Dispatchers.IO) {
        val lastSync = prefs.getLong(PREF_LAST_SYNC, 0L)
        val now = System.currentTimeMillis()

        if (now - lastSync < SYNC_TTL_MS) {
            Log.d(TAG, "Sync skipped — last sync ${(now - lastSync) / 1000}s ago (TTL: ${SYNC_TTL_MS / 1000}s)")
            return@withContext
        }

        try {
            val request = Request.Builder().url(FEED_URL).get().build()
            val response = SecureHttpClient.default.newCall(request).execute()

            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                if (bodyStr.length > 20) {
                    val type = object : TypeToken<List<MicrolearningCardData>>() {}.type
                    val remoteCards: List<MicrolearningCardData> = Gson().fromJson(bodyStr, type)
                    if (remoteCards.isNotEmpty()) {
                        // CS-4.5 fix: smartUpsert never overwrites isCompleted/isUnlocked
                        dao.smartUpsert(remoteCards)
                        prefs.edit().putLong(PREF_LAST_SYNC, now).apply()
                        Log.d(TAG, "Feed synced: ${remoteCards.size} cards upserted.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Feed sync failed (offline or server error). Local data unaffected.", e)
        }
    }

    /**
     * Restores completion progress from Firestore on login/reinstall.
     */
    suspend fun syncProgressFromCloud() = withContext(Dispatchers.IO) {
        try {
            val uid = auth.currentUser?.uid ?: return@withContext
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val completedTopics = userDoc.get("completed_topics") as? List<String> ?: emptyList()
                completedTopics.forEach { topicId ->
                    dao.markCardCompleted(topicId)
                }
                Log.d(TAG, "Restored ${completedTopics.size} completed topics from cloud.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud progress sync failed.", e)
        }
    }
}
