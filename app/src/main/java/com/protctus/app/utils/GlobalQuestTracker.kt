package com.protctus.app.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalQuestTracker @Inject constructor() {
    private val _questEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    
    // We emit pure, deterministic action_trigger strings now. Zero hardcoded classes.
    val questEvents = _questEvents.asSharedFlow()

    suspend fun emitEvent(actionTrigger: String) {
        _questEvents.emit(actionTrigger.lowercase().trim())
    }
}
