package com.protctus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.repository.MicrolearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import com.protctus.app.ui.screens.education.PathNode
import kotlinx.coroutines.flow.map

/**
 * ViewModel for LabChaptersScreen and LearningPathsScreen.
 * Calculates dynamic PathNodes from the local database.
 */
@HiltViewModel
class LabChaptersViewModel @Inject constructor(
    repository: MicrolearningRepository
) : ViewModel() {

    val nodes: StateFlow<List<PathNode>> = repository.getAllCardsFlow().map { allCards ->
        // Group by category, ordered by their minimum priority
        val categoriesWithCards = allCards.groupBy { it.category }
            .toList()
            .sortedBy { (_, cards) -> cards.minOfOrNull { it.priority } ?: 0 }
            
        var previousCategoryCompleted = true

        categoriesWithCards.mapIndexed { index, (categoryName, cards) ->
            val isCompleted = cards.isNotEmpty() && cards.all { it.isCompleted }
            
            // A node is locked if it's not the first one AND the previous category wasn't fully completed
            val isLocked = index > 0 && !previousCategoryCompleted
            
            previousCategoryCompleted = isCompleted
            
            PathNode(
                id = categoryName,
                title = categoryName,
                isCompleted = isCompleted,
                isLocked = isLocked
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
