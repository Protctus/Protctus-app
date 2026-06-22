package com.protctus.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.LearningSessionManager
import com.protctus.app.data.local.SessionData
import com.protctus.app.data.local.entity.MicrolearningCardData
import com.protctus.app.repository.MicrolearningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

enum class CardLockState {
    COMPLETED, ACTIVE, LOCKED_SEQUENCE, LOCKED_QUOTA
}

data class UiCardItem(
    val card: MicrolearningCardData,
    val lockState: CardLockState
)

sealed interface FeedUiState {
    object Loading : FeedUiState
    data class Success(val data: List<UiCardItem>) : FeedUiState
    object Empty : FeedUiState
}

sealed interface QuizEvent {
    object NavigateToNext : QuizEvent
}

/**
 * CS-1.1 fix: All dependencies are now injected via Hilt constructor.
 * No manual AppDatabase.getInstance(), FirebaseFirestore.getInstance(), or SyncRepository construction.
 * All data operations are delegated to [MicrolearningRepository] (CS-3.1 fix).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SecurityQuizViewModel @Inject constructor(
    private val repository: MicrolearningRepository,
    private val sessionManager: LearningSessionManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // 1. Session State (Daily Limits)
    val sessionState: StateFlow<SessionData> = sessionManager.sessionStateFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, SessionData(null, 0))

    val dailyLimitReached: StateFlow<Boolean> = sessionState
        .map { it.dailyQuota >= 10 }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    // 2. User XP State — delegated to repository
    val userXp: StateFlow<Int> = repository.getUserStatsFlow()
        .map { it?.total_xp ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // 3. Category Filter State — Read strictly from Nav Args
    private val categoryArg: String? = savedStateHandle.get<String>("categoryId")
    private val _selectedCategory = MutableStateFlow<String?>(categoryArg)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // All distinct categories from the DB — delegated to repository
    val categories: StateFlow<List<String>> = repository.getDistinctCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive feed UI state — delegated to repository
    val feedState: StateFlow<FeedUiState> = combine(
        _selectedCategory.flatMapLatest { category ->
            if (category == null) repository.getAllCardsFlow() else repository.getCardsByCategory(category)
        },
        dailyLimitReached
    ) { cards, isLimitReached ->
        if (cards.isEmpty()) FeedUiState.Empty else {
            var firstIncompleteFound = false
            val uiItems = cards.map { card ->
                val lockState = when {
                    card.isCompleted -> CardLockState.COMPLETED
                    !firstIncompleteFound -> {
                        firstIncompleteFound = true
                        if (isLimitReached) CardLockState.LOCKED_QUOTA else CardLockState.ACTIVE
                    }
                    else -> CardLockState.LOCKED_SEQUENCE
                }
                UiCardItem(card, lockState)
            }
            FeedUiState.Success(uiItems)
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeedUiState.Loading
        )

    // 4. Single Live Event Channel for UI Navigation
    private val _quizEvents = Channel<QuizEvent>()
    val quizEvents = _quizEvents.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // CS-3.3 fix: syncFeedIfStale() checks TTL internally — won't hit network on every init
            repository.syncProgressFromCloud()
            repository.syncFeedIfStale()
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun submitAnswer(cardId: String, isCorrect: Boolean) {
        if (isCorrect) {
            viewModelScope.launch {
                markCardCompleted(cardId)
                delay(600) // Delay to let UI animations (like halo) finish
                _quizEvents.send(QuizEvent.NavigateToNext)
            }
        }
    }

    private fun markCardCompleted(cardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentState = feedState.value
            if (currentState !is FeedUiState.Success) return@launch
            val currentItem = currentState.data.find { it.card.id == cardId } ?: return@launch
            
            if (currentItem.lockState != CardLockState.ACTIVE) return@launch
            
            val currentCard = currentItem.card
            
            // Lock session to this category if first attempt today
            val session = sessionState.value
            
            if (session.attemptedCategory == null) {
                sessionManager.setAttemptedCategory(currentCard.category)
            } else if (session.attemptedCategory != currentCard.category) {
                // Failsafe: Prevent completion if they try to switch categories mid-day
                return@launch 
            }

            // 1. Mark completed, unlock next, and sync to cloud — all via Repository
            repository.completeCard(currentCard, xpReward = 5)

            // 2. Increment quota
            sessionManager.incrementDailyQuota()
        }
    }
}
