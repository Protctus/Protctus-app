package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface QuestDao {
    // 1. Check active state and mark completed. Returns the number of rows updated (0 or 1)
    @Query("UPDATE quests SET is_completed = 1 WHERE id = :questId AND is_active = 1 AND is_completed = 0")
    suspend fun markQuestCompleted(questId: String): Int 

    // 2. The exact query to increment total XP directly in SQL without fetching to memory
    @Query("INSERT OR REPLACE INTO user_stats (uid, total_xp) VALUES ('local_user', COALESCE((SELECT total_xp FROM user_stats WHERE uid = 'local_user'), 0) + :xpGained)")
    suspend fun addXp(xpGained: Int)

    // 3. The exact @Transaction to guarantee data integrity
    @Transaction
    suspend fun completeQuestAndAwardXp(questId: String, xpReward: Int): Boolean {
        val rowsUpdated = markQuestCompleted(questId)
        
        // Strictly add XP ONLY if the quest was active and just got marked as completed
        if (rowsUpdated > 0) {
            addXp(xpReward)
            return true
        }
        return false
    }
}
