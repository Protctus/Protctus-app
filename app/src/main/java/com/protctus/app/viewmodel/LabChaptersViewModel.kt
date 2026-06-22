package com.protctus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.repository.MicrolearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight ViewModel for LabChaptersScreen.
 * Only fetches the category list — no quiz state, no session management.
 * The heavy SecurityQuizViewModel is scoped to MicrolearningScreen only.
 */
@HiltViewModel
class LabChaptersViewModel @Inject constructor(
    repository: MicrolearningRepository
) : ViewModel() {

    val categories: StateFlow<List<String>> = repository.getDistinctCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
