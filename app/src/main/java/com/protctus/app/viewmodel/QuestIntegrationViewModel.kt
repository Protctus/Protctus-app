package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.dao.QuestDao
import com.protctus.app.utils.GlobalQuestTracker
import com.protctus.app.utils.QuestTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Background ViewModel that bridges the GlobalQuestTracker event bus
 * to the local Room database for offline XP persistence.
 *
 * This ViewModel should be created at the Activity level (shared scope)
 * so it is always alive and collecting events.
 */
@HiltViewModel
class QuestIntegrationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val questDao: QuestDao,
    private val globalQuestTracker: GlobalQuestTracker
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            globalQuestTracker.questEvents.collect { event ->
                processEvent(event)
            }
        }
    }

    private suspend fun processEvent(event: String) {
        // Persist to legacy SharedPrefs as backup (idempotent)
        QuestTracker.markActionCompleted(appContext, event)

        // Attempt the strict Room transaction for local XP
        // The QuestDao's @Transaction ensures no double-awarding
        try {
            questDao.completeQuestAndAwardXp(event, 5)
        } catch (_: Exception) {
            // Graceful: quest may not exist in Room yet (fetched from API only)
            // The legacy QuestTracker + DailyQuestsViewModel Firestore flow handles this
        }
    }
}
