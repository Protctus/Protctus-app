package com.protctus.app.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.protctus.app.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SyncRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "SyncRepository"
    }

    private val db = AppDatabase.getInstance(context)
    private val cardDao = db.microlearningCardDao()

    /**
     * Mark topic completed: updates local Room DB immediately (Offline-First),
     * then pushes the ID to Firestore in the background.
     */
    suspend fun markTopicCompleted(uid: String, topicId: String) = withContext(Dispatchers.IO) {
        try {
            // Update local Room database
            cardDao.markCardCompleted(topicId)
            Log.d(TAG, "Topic $topicId marked completed locally.")

            // Push to Cloud (Firestore)
            firestore.collection("users").document(uid)
                .update("completed_topics", FieldValue.arrayUnion(topicId))
                .await()
            Log.d(TAG, "Topic $topicId pushed to cloud successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark topic $topicId completed in cloud", e)
        }
    }

    /**
     * Fetch completed topics from Firestore on login/launch and update local DB.
     * Ensures progress is restored even after reinstall.
     */
    suspend fun syncProgressOnLogin(uid: String) = withContext(Dispatchers.IO) {
        try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val completedTopics = userDoc.get("completed_topics") as? List<String> ?: emptyList()
                
                completedTopics.forEach { topicId ->
                    cardDao.markCardCompleted(topicId)
                }
                Log.d(TAG, "Successfully synced ${completedTopics.size} completed topics from cloud.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync progress on login", e)
        }
    }
}
