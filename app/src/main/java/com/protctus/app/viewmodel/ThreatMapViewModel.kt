package com.protctus.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.network.SecureHttpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

import java.util.UUID

data class MapCoordinates(val latitude: Double, val longitude: Double)

data class ThreatSummaryMetrics(
    val activeAttacks: Int = 0,
    val topTarget: String = "—",
    val topType: String = "—",
    val monitoredNodes: Int = 0
)

data class ThreatMarker(
    val id: String,
    val attackType: String,
    val sourceIp: String,
    val targetIp: String,
    val pos: MapCoordinates,
    val origin: MapCoordinates
)

sealed class ThreatMapUiState {
    object Loading : ThreatMapUiState()
    data class Success(val metrics: ThreatSummaryMetrics, val markers: List<ThreatMarker>) : ThreatMapUiState()
    data class Error(val message: String) : ThreatMapUiState()
}

@HiltViewModel
class ThreatMapViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val TAG = "ThreatMapVM"
        private const val API_URL = "https://live-threat-backend.onrender.com/feed"
        private const val API_KEY = "d0ab308633a94292e296152825c633027d5bc1f2dc3347045bac3481c51e0dfcbb7185fa37d8aa908637b8ecf2490372a316b07e7c10c0498aa7a2ef6804f6f0d591a6a5ecb1c09e9d006ec7f3b4efc6cc5897d3907dbba30e64e0ef01c7068e80c7578dc1e6bbdc2708baee9e2d7da2adc86adeaec456081dfc24ad030a89e9"
    }

    private val _uiState = MutableStateFlow<ThreatMapUiState>(ThreatMapUiState.Loading)
    val uiState: StateFlow<ThreatMapUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchLiveMetrics()
                delay(10_000) // Poll every 10 seconds
            }
        }
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchLiveMetrics()
            delay(400)
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchLiveMetrics() {
        withContext(Dispatchers.IO) {
            try {
                // Free-tier Render instances can take up to 50 seconds to cold-start.
                // Using a dedicated client with an extended timeout to prevent SocketTimeoutException (IOException).
                val client = SecureHttpClient.default.newBuilder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = Request.Builder()
                    .url("$API_URL?api_key=$API_KEY")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "API returned ${response.code}")
                    _uiState.value = ThreatMapUiState.Error("Failed to fetch data: HTTP ${response.code}")
                    return@withContext
                }

                val body = response.body?.string()
                if (body == null) {
                    _uiState.value = ThreatMapUiState.Error("Empty response body")
                    return@withContext
                }

                val json = JSONObject(body)
                val events = json.optJSONArray("events")
                if (events == null) {
                    _uiState.value = ThreatMapUiState.Error("Invalid response format")
                    return@withContext
                }

                val targetCounts = mutableMapOf<String, Int>()
                val typeCounts = mutableMapOf<String, Int>()
                val uniqueIps = mutableSetOf<String>()
                val parsedMarkers = mutableListOf<ThreatMarker>()

                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue

                    val targetCountry = event.optString("target_country", "Unknown")
                    targetCounts[targetCountry] = (targetCounts[targetCountry] ?: 0) + 1

                    val attackType = event.optString("attack_type", "Unknown")
                    typeCounts[attackType] = (typeCounts[attackType] ?: 0) + 1

                    val srcIp = event.optString("source_ip", "")
                    val tgtIp = event.optString("target_ip", "")
                    if (srcIp.isNotBlank()) uniqueIps.add(srcIp)
                    if (tgtIp.isNotBlank()) uniqueIps.add(tgtIp)
                    
                    val (lat, lng) = if (event.has("latitude") && event.has("longitude")) {
                        Pair(event.getDouble("latitude"), event.getDouble("longitude"))
                    } else {
                        CountryGeoMapper.getCoordinates(targetCountry)
                    }
                    
                    val originLat = (srcIp.hashCode() % 160) - 80.0
                    val originLng = (srcIp.hashCode() % 360) - 180.0
                    
                    parsedMarkers.add(
                        ThreatMarker(
                            id = UUID.randomUUID().toString(),
                            attackType = attackType,
                            sourceIp = srcIp,
                            targetIp = tgtIp,
                            pos = MapCoordinates(lat, lng),
                            origin = MapCoordinates(originLat, originLng)
                        )
                    )
                }

                val topTarget = targetCounts.maxByOrNull { it.value }?.key ?: "—"
                val topType = typeCounts.maxByOrNull { it.value }?.key ?: "—"

                _uiState.value = ThreatMapUiState.Success(
                    metrics = ThreatSummaryMetrics(
                        activeAttacks = events.length(),
                        topTarget = topTarget,
                        topType = topType,
                        monitoredNodes = uniqueIps.size
                    ),
                    markers = parsedMarkers
                )

            } catch (e: IOException) {
                Log.e(TAG, "Network error: ${e.message}")
                _uiState.value = ThreatMapUiState.Error("Network error. Please check your connection.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch live metrics: ${e.message}")
                _uiState.value = ThreatMapUiState.Error("An unexpected error occurred.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
