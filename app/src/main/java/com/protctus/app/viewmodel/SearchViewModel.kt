package com.protctus.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protctus.app.data.local.RecentSearch
import com.protctus.app.data.repository.SearchRepository
import com.protctus.app.data.repository.SearchResultItemData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItemData>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItemData>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val recentSearches: StateFlow<List<RecentSearch>> = searchRepository.recentSearches
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isLoading.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(500) // Debounce delay
            performSearch(query)
        }
    }

    fun onSearchTriggered(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        
        if (query.isNotBlank()) {
            viewModelScope.launch {
                searchRepository.insertSearchQuery(query)
                performSearch(query)
            }
        } else {
            _searchResults.value = emptyList()
        }
    }

    private suspend fun performSearch(query: String) {
        _isLoading.value = true
        try {
            val results = searchRepository.searchItems(query)
            _searchResults.value = results
        } catch (e: Exception) {
            _searchResults.value = emptyList()
        } finally {
            _isLoading.value = false
        }
    }

    fun clearRecentSearch(id: Int) {
        viewModelScope.launch {
            searchRepository.deleteSearchQuery(id)
        }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            searchRepository.clearAllRecentSearches()
        }
    }
}
