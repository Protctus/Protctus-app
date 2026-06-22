package com.protctus.app.engine

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Decoupled communication bridge between background services (VPN) 
 * and the foreground Jetpack Compose UI.
 */
object ThreatEventBus {
    
    data class ThreatDetected(
        val url: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _threatEvents = MutableSharedFlow<ThreatDetected>(extraBufferCapacity = 1)
    val threatEvents = _threatEvents.asSharedFlow()

    /**
     * Emits a threat event to all subscribers.
     * Thread-safe and non-blocking.
     */
    fun emitThreat(url: String) {
        _threatEvents.tryEmit(ThreatDetected(url))
    }
}
