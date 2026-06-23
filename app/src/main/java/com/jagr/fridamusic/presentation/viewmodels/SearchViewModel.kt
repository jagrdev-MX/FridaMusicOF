package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.data.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val SEARCH_HISTORY_MAX_ITEMS = 20

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _youtubeSearchResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
    val youtubeSearchResults = _youtubeSearchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(
        settingsManager.searchHistory.split("||").filter { it.isNotBlank() }.take(SEARCH_HISTORY_MAX_ITEMS)
    )
    val searchHistory = _searchHistory.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    fun getSuggestions(query: String) {
        suggestionsJob?.cancel()
        if (query.length < 2) {
            _suggestions.value = emptyList()
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(150) // Ultra-fast debounce
            val results = withContext(Dispatchers.IO) {
                YouTube.getSuggestions(query)
            }
            _suggestions.value = results
        }
    }

    fun searchYouTube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _youtubeSearchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(120)
            
            try {
                val results = withContext(Dispatchers.IO) {
                    YouTube.search(trimmed)
                }
                _youtubeSearchResults.value = results
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _youtubeSearchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addToSearchHistory(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(cleanQuery, ignoreCase = true) }
        current.add(0, cleanQuery)
        while (current.size > SEARCH_HISTORY_MAX_ITEMS) current.removeAt(current.lastIndex)

        _searchHistory.value = current
        settingsManager.searchHistory = current.joinToString("||")
    }

    fun removeFromSearchHistory(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(query)
        _searchHistory.value = current
        settingsManager.searchHistory = current.joinToString("||")
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        settingsManager.searchHistory = ""
    }

    fun clearSearch() {
        _youtubeSearchResults.value = emptyList()
        _isSearching.value = false
    }
}
