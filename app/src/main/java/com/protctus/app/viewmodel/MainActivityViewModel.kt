package com.protctus.app.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns all side-effect logic that was previously inlined in MainActivity's setContent{}.
 * This eliminates the "God Activity" anti-pattern and prevents lifecycle-scoped leaks.
 *
 * Responsibilities:
 * - Observe DataStore settings flows (real-time protection, phishing shield, etc.)
 * - Trigger engine initializations reactively
 * - Provide settings state to the Compose UI tree without manual SettingsManager instantiation
 */
@HiltViewModel
class MainActivityViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsManager: SettingsManager
) : ViewModel() {

    // ── Reactive Settings Flows (single source of truth) ──

    val isRealTimeProtectionEnabled: StateFlow<Boolean> = settingsManager.isRealTimeProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPhishingShieldEnabled: StateFlow<Boolean> = settingsManager.isPhishingShieldEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isJuiceJackingEnabled: StateFlow<Boolean> = settingsManager.isJuiceJackingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isUsbProtectionEnabled: StateFlow<Boolean> = settingsManager.isUsbProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGhostAppDetectorEnabled: StateFlow<Boolean> = settingsManager.isGhostAppDetectorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isHeuristicsEnabled: StateFlow<Boolean> = settingsManager.isHeuristicsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isInvisibleWatcherEnabled: StateFlow<Boolean> = settingsManager.isInvisibleWatcherEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLanguage: StateFlow<String> = settingsManager.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    private val _manualUpdateCheck = MutableSharedFlow<Unit>(replay = 0)
    val manualUpdateCheck: SharedFlow<Unit> = _manualUpdateCheck.asSharedFlow()

    fun triggerUpdateCheck() {
        viewModelScope.launch {
            _manualUpdateCheck.emit(Unit)
        }
    }

    // ── Engine Control Methods (scoped to ViewModel lifecycle, no leaks) ──

    fun startRealTimeProtection(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, com.protctus.app.services.SentinelService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startPhishingVpn(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, com.protctus.edge.phishing.PhishingVpnService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun runStalkerwareScan(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.protctus.app.engine.StalkerwareEngine.executeBatchScan(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun initializeUsbEngine(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            com.protctus.app.engine.UsbThreatEngine.initialize(context.applicationContext)
        }
    }

    fun deinitializeUsbEngine(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            com.protctus.app.engine.UsbThreatEngine.deinitialize(context.applicationContext)
        }
    }

    fun runNativeSecurityCheck(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            com.protctus.app.security.ChakravyuhNative.runSilentCheck(context)
        }
    }
}
