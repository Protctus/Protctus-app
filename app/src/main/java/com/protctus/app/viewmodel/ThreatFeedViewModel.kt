package com.protctus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.ThreatAlert
import com.protctus.app.data.local.ThreatDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreatFeedViewModel @Inject constructor(
    private val threatDao: ThreatDao
) : ViewModel() {

    // Expose all threats directly from Room DB as a StateFlow
    val threats: StateFlow<List<ThreatAlert>> = threatDao.getAllThreats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteThreat(id: Int) {
        viewModelScope.launch {
            threatDao.deleteThreat(id)
        }
    }

    fun clearAllThreats() {
        viewModelScope.launch {
            threatDao.clearAllThreats()
        }
    }
}
