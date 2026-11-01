package com.protctus.app.utils

import com.protctus.app.viewmodel.DailyQuest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton state manager that holds the currently active quest for the Dynamic Guide Overlay.
 */
object GlobalQuestManager {
    private val _activeQuest = MutableStateFlow<DailyQuest?>(null)
    val activeQuest: StateFlow<DailyQuest?> = _activeQuest.asStateFlow()

    fun setActiveQuest(quest: DailyQuest?) {
        _activeQuest.value = quest
    }
}
