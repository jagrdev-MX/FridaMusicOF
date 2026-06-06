package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.data.repository.*
import com.jagr.fridamusic.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val settingsManager: SettingsManager,
    private val youtubeRepository: YouTubeRepository
) : AndroidViewModel(application) {

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val searchCache = ConcurrentHashMap<String, List<YouTubeResult>>()
    private val searchCacheAtMs = ConcurrentHashMap<String, Long>()
    private val CACHE_EXPIRY_MS = 1000 * 60 * 10 // 10 minutes

    private val _youtubeSearchResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
    val youtubeSearchResults = _youtubeSearchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(
        settingsManager.searchHistory.split("||").filter { it.isNotBlank() }
    )
    val searchHistory = _searchHistory.asStateFlow()

    private var searchJob: Job? = null

    fun searchYouTube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _youtubeSearchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        // 1. Instant Cache Check
        val cached = getCachedResults(trimmed)
        if (cached != null) {
            _youtubeSearchResults.value = cached
            _isSearching.value = false
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // 2. Debounce: wait for user to stop typing (300ms)
            delay(300)
            
            _isSearching.value = true
            try {
                val results = withContext(Dispatchers.IO) {
                    YouTube.search(trimmed)
                }
                
                // 3. Update flows and Cache
                if (results.isNotEmpty()) {
                    putInCache(trimmed, results)
                }
                
                _youtubeSearchResults.value = results
                if (settingsManager.gaplessPlayback) {
                    results.take(2).forEach { result ->
                        launch { youtubeRepository.prefetchStream(result) }
                    }
                }
            } catch (e: Exception) {
                _youtubeSearchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    private fun getCachedResults(query: String): List<YouTubeResult>? {
        val key = query.lowercase().trim()
        val expiry = searchCacheAtMs[key] ?: 0L
        if (System.currentTimeMillis() > expiry) {
            searchCache.remove(key)
            searchCacheAtMs.remove(key)
            return null
        }
        return searchCache[key]
    }

    private fun putInCache(query: String, results: List<YouTubeResult>) {
        val key = query.lowercase().trim()
        if (searchCache.size > 50) {
            searchCache.clear()
            searchCacheAtMs.clear()
        }
        searchCache[key] = results
        searchCacheAtMs[key] = System.currentTimeMillis() + CACHE_EXPIRY_MS
    }

    fun addToSearchHistory(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.equals(cleanQuery, ignoreCase = true) }
        current.add(0, cleanQuery)
        while (current.size > 30) current.removeAt(current.lastIndex)

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
