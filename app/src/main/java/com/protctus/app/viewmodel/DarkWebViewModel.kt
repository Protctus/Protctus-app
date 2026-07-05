package com.protctus.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.ThreatAlert
import com.protctus.app.data.local.ThreatDao
import com.protctus.app.network.DarkWebApiClient
import com.protctus.app.network.DarkWebScanResult
import com.protctus.app.utils.QuestTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DarkWebScanState { IDLE, VALIDATING, SCANNING, PAUSED, COMPLETED }

@HiltViewModel
class DarkWebViewModel @Inject constructor(
    private val threatDao: ThreatDao
) : ViewModel() {

    private val _scanState = MutableStateFlow(DarkWebScanState.IDLE)
    val scanState: StateFlow<DarkWebScanState> = _scanState.asStateFlow()

    private val _scanResult = MutableStateFlow<DarkWebScanResult?>(null)
    val scanResult: StateFlow<DarkWebScanResult?> = _scanResult.asStateFlow()

    fun togglePause() {
        when (_scanState.value) {
            DarkWebScanState.SCANNING -> _scanState.value = DarkWebScanState.PAUSED
            DarkWebScanState.PAUSED -> _scanState.value = DarkWebScanState.SCANNING
            else -> {}
        }
    }

    fun startScan(context: Context, email: String) {
        if (_scanState.value == DarkWebScanState.SCANNING) return

        _scanState.value = DarkWebScanState.VALIDATING
        _scanResult.value = null

        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _scanResult.value = DarkWebScanResult(
                isSafe = false,
                isError = true,
                errorMessage = "Invalid email address configured in profile.",
                scannedEmail = email
            )
            _scanState.value = DarkWebScanState.COMPLETED
            return
        }

        _scanState.value = DarkWebScanState.SCANNING

        viewModelScope.launch(Dispatchers.IO) {
            val result = DarkWebApiClient.scanEmail(email)
            
            // Check if paused during scan (though network call is synchronous, we respect state after)
            if (_scanState.value == DarkWebScanState.PAUSED) {
                // Wait until unpaused
                while (_scanState.value == DarkWebScanState.PAUSED) {
                    kotlinx.coroutines.delay(200)
                }
            }

            _scanResult.value = result
            _scanState.value = DarkWebScanState.COMPLETED

            if (!result.isError) {
                persistThreatIfFound(result)
                QuestTracker.markTaskCompleted(context, "dark_web_scan")
            }
        }
    }

    private suspend fun persistThreatIfFound(result: DarkWebScanResult) {
        // Privacy Constraint: Do not persist "SAFE" results to minimize stored data footprint.
        // Only persist actual threats to the ThreatFeed.
        if (!result.isSafe && result.breachCount > 0) {
            val detailsSafe = result.details
            val alert = ThreatAlert(
                title = "Dark Web Breach Detected",
                description = "${result.breachCount} breach(es) found for your email. ${detailsSafe.take(2).joinToString(", ")}",
                priority = "High",
                threatType = "Dark Web Scan",
                priorityColorHex = "#EF4444",
                timestamp = System.currentTimeMillis()
            )
            threatDao.insertThreat(alert)
        }
    }

    fun resetState() {
        _scanState.value = DarkWebScanState.IDLE
        _scanResult.value = null
    }
}
