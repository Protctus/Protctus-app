package com.protctus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.protctus.app.data.local.entity.MicrolearningCardData
import com.protctus.app.data.local.entity.StringListConverter
import com.protctus.app.data.local.entity.UserStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(StringListConverter::class)
interface MicrolearningCardDao {
    @Query("SELECT * FROM microlearning_cards ORDER BY priority ASC")
    fun getAllCardsFlow(): Flow<List<MicrolearningCardData>>
    
    @Query("SELECT category FROM microlearning_cards GROUP BY category ORDER BY MIN(priority) ASC")
    fun getDistinctCategories(): Flow<List<String>>
    
    // CS-3.5 fix: Removed isCompleted = 0 filter to allow 4-state architecture
    @Query("SELECT * FROM microlearning_cards WHERE category = :category ORDER BY orderIndex ASC")
    fun getCardsByCategory(category: String): Flow<List<MicrolearningCardData>>

    @Query("SELECT COUNT(*) FROM microlearning_cards WHERE category = :categoryId AND isCompleted = 0")
    suspend fun getUncompletedCountByCategory(categoryId: String): Int

    @Query("UPDATE microlearning_cards SET isCompleted = 1 WHERE id = :id")
    suspend fun markCardCompleted(id: String)

    @Query("SELECT * FROM user_stats WHERE uid = 'local_user'")
    fun getUserStatsFlow(): Flow<UserStatsEntity?>

    @Query("UPDATE microlearning_cards SET isUnlocked = 1 WHERE category = :categoryId AND orderIndex = :nextOrderIndex")
    suspend fun unlockSpecificTopic(categoryId: String, nextOrderIndex: Int)

    @Query("INSERT OR REPLACE INTO user_stats (uid, total_xp) VALUES ('local_user', COALESCE((SELECT total_xp FROM user_stats WHERE uid = 'local_user'), 0) + :xpGained)")
    suspend fun addXp(xpGained: Int)

    @Transaction
    suspend fun completeAndUnlockNext(topicId: String, categoryId: String, currentOrderIndex: Int, xpReward: Int) {
        markCardCompleted(topicId)
        unlockSpecificTopic(categoryId, currentOrderIndex + 1)
        if (xpReward > 0) {
            addXp(xpReward)
        }
    }

    @Query("SELECT * FROM microlearning_cards")
    suspend fun getAllCards(): List<MicrolearningCardData>

    // CS-4.5 fix: Changed from REPLACE to IGNORE — new cards only, never overwrites existing
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCards(cards: List<MicrolearningCardData>)

    // CS-4.5: Safe content update that preserves user progress fields
    @Query("""
        UPDATE microlearning_cards 
        SET type = :type, category = :category, title = :title, description = :description,
            visualUrl = :visualUrl, options = :options, correctAnswer = :correctAnswer, priority = :priority
        WHERE id = :id
    """)
    suspend fun updateCardContent(
        id: String, type: String, category: String, title: String,
        description: String, visualUrl: String, options: List<String>,
        correctAnswer: String, priority: Int
    )

    /**
     * CS-4.5 fix: Smart upsert that inserts new cards and updates existing card content
     * without ever touching isCompleted, isUnlocked, or orderIndex.
     */
    @Transaction
    suspend fun smartUpsert(cards: List<MicrolearningCardData>) {
        // 1. Attempt insert — IGNORE silently skips existing IDs
        insertCards(cards)
        // 2. Update content fields for cards that already existed (preserving progress)
        cards.forEach { card ->
            updateCardContent(
                id = card.id,
                type = card.type,
                category = card.category,
                title = card.title,
                description = card.description,
                visualUrl = card.visualUrl,
                options = card.options,
                correctAnswer = card.correctAnswer,
                priority = card.priority
            )
        }
    }

    @Query("DELETE FROM microlearning_cards")
    suspend fun deleteAllCards()
    
    @Transaction
    suspend fun replaceAll(cards: List<MicrolearningCardData>) {
        deleteAllCards()
        insertCards(cards)
    }
}

