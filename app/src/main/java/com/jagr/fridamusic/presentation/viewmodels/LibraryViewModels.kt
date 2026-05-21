package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.MusicDatabase
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.data.repository.PlaybackHistoryRepository
import com.jagr.fridamusic.data.local.PlaylistEntity
import com.jagr.fridamusic.data.remote.innertube.DeezerApi
import com.jagr.fridamusic.data.remote.innertube.ResultType
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.data.repository.AudioRepository
import com.jagr.fridamusic.data.repository.SettingsManager
import com.jagr.fridamusic.domain.lyrics.LyricsLine
import com.jagr.fridamusic.domain.lyrics.LyricsParser
import com.jagr.fridamusic.domain.model.PlaybackQueueState
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.QueueItem
import com.jagr.fridamusic.domain.model.QueueSource
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.service.MusicService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

enum class RepeatMode { OFF, ALL, ONE }
enum class AppTheme(val displayName: String) { SYSTEM("System Default"), LIGHT("Light"), DARK("Dark") }

class LibraryViewModels(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    val settingsManager = SettingsManager(application)
    private val playlistDao = MusicDatabase.getDatabase(application).playlistDao()
    val filterVoiceNotes = MutableStateFlow(settingsManager.filterVoiceNotes)
    val keepScreenOn = MutableStateFlow(settingsManager.keepScreenOn)
    val gaplessPlayback = MutableStateFlow(settingsManager.gaplessPlayback)
    val crossfadeDuration = MutableStateFlow(settingsManager.crossfadeDuration)
    val saveLastPlayback = MutableStateFlow(settingsManager.saveLastPlayback)

    private val _currentTheme = MutableStateFlow<AppTheme>(AppTheme.SYSTEM)
    val currentTheme = _currentTheme.asStateFlow()
    val sleepTimerMinutes = MutableStateFlow(0)
    private var sleepTimerJob: Job? = null
    private val _searchHistory = MutableStateFlow<List<String>>(
        settingsManager.searchHistory.split("||").filter { it.isNotBlank() }
    )
    private val _recentHistory =
        MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    private val _fullHistory =
        MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    private val _queueState = MutableStateFlow(PlaybackQueueState())
    private val _playlistCoverUris = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _followedArtists = MutableStateFlow(settingsManager.followedArtists)

    val recentHistory = _recentHistory.asStateFlow()
    val fullHistory = _fullHistory.asStateFlow()
    val searchHistory = _searchHistory.asStateFlow()
    val queueState = _queueState.asStateFlow()
    val manualQueue = _queueState
        .map { state -> state.upNext.map { it.song } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlistCoverUris = _playlistCoverUris.asStateFlow()
    val followedArtists = _followedArtists.asStateFlow()

    val isAutoPlayEnabled = MutableStateFlow(settingsManager.autoplayEnabled)

    fun toggleAutoplay(enabled: Boolean) {
        isAutoPlayEnabled.value = enabled
        settingsManager.autoplayEnabled = enabled
        if (enabled) {
            refreshAutoplayRecommendations(force = true)
        } else {
            recommendationJob?.cancel()
            _queueState.value = _queueState.value.copy(
                autoplay = emptyList(),
                isAutoplayLoading = false,
                autoplayError = null
            )
        }
        persistQueueState()
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

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting = _isExtracting.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _currentAlbumArt = MutableStateFlow<String?>(null)
    val currentAlbumArt = _currentAlbumArt.asStateFlow()

    private val _lyricsLines = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyricsLines = _lyricsLines.asStateFlow()

    private val _repeatMode = MutableStateFlow(
        runCatching { RepeatMode.valueOf(settingsManager.repeatModeName) }.getOrDefault(RepeatMode.OFF)
    )
    val repeatMode = _repeatMode.asStateFlow()
    val isShuffleMode = MutableStateFlow(settingsManager.shuffleEnabled)

    private val _youtubeSearchResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
    val youtubeSearchResults = _youtubeSearchResults.asStateFlow()

    val artistSongs = _youtubeSearchResults.map { results ->
        results.filter { it.type == ResultType.SONG }.map { result ->
            Song(
                id = result.videoId.hashCode().toLong(),
                title = result.title,
                artist = result.artist,
                data = result.videoId,
                duration = 0L,
                albumId = 0L,
                uri = Uri.parse("https://music.youtube.com/watch?v=${result.videoId}"),
                artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artistPlaylists = _youtubeSearchResults.map { results ->
        results.filter { it.type == ResultType.PLAYLIST }.map { result ->
            Song(
                id = result.videoId.hashCode().toLong(),
                title = result.title,
                artist = result.artist,
                data = result.videoId,
                duration = 0L,
                albumId = 0L,
                uri = Uri.parse("https://www.youtube.com/playlist?list=${result.videoId}"),
                artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists = playlistDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            Playlist(entity.id, entity.name, entity.description ?: "", entity.songIds.split(",").filter { it.isNotBlank() }.map { it.toLong() }, entity.createdAt)
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private val playbackHistoryRepository =
        PlaybackHistoryRepository(
            MusicDatabase.getDatabase(application).playbackHistoryDao()
        )
    private var progressJob: Job? = null
    private var extractionJob: Job? = null
    private var searchJob: Job? = null
    private var recommendationJob: Job? = null
    private val imageUrlCache = ConcurrentHashMap<String, String>()
    private val audioStreamCache = ConcurrentHashMap<String, String>()
    private val deezerToYoutubeMap = ConcurrentHashMap<String, String>()
    private val searchResultsCache = ConcurrentHashMap<String, List<YouTubeResult>>()

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_PLAY_PAUSE" -> togglePlayback()
                "ACTION_NEXT" -> skipToNext()
                "ACTION_PREV" -> skipToPrevious()
                "ACTION_SEEK" -> seekTo(intent.getLongExtra("SEEK_POSITION", 0L))
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction("ACTION_PLAY_PAUSE")
            addAction("ACTION_PREV")
            addAction("ACTION_NEXT")
            addAction("ACTION_SEEK")
        }
        ContextCompat.registerReceiver(getApplication(), notificationReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        ensureFavoritesPlaylistExists()
        refreshPlaylistCoverUris()
        restoreLastPlaybackState()
        loadRecentHistory()
    }

    @OptIn(UnstableApi::class)
    private fun restoreLastPlaybackState() {
        val fastLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,
                50000,
                500,
                500
            )
            .build()

        exoPlayer = ExoPlayer.Builder(getApplication())
            .setLoadControl(fastLoadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                        _currentSong.value?.let { updateNotification(it, isPlaying) }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = state
                        if (state == Player.STATE_READY) {
                            _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                        }
                        if (state == Player.STATE_ENDED) {
                            _currentPosition.value = 0L

                            when {
                                _repeatMode.value == RepeatMode.ONE -> {
                                    exoPlayer?.seekTo(0)
                                    exoPlayer?.play()
                                }
                                _queueState.value.hasPlayableNext ||
                                    isAutoPlayEnabled.value ||
                                    _repeatMode.value == RepeatMode.ALL -> {
                                    skipToNext()
                                }
                                else -> {
                                    _isPlaying.value = false
                                    exoPlayer?.seekTo(0)
                                    exoPlayer?.pause()
                                    _currentSong.value?.let { updateNotification(it, false) }
                                }
                            }
                        }
                    }
                })
            }

        viewModelScope.launch {
            delay(150)
            val (shouldRestore, savedSong, lastPos) = withContext(Dispatchers.IO) {
                val restore = settingsManager.saveLastPlayback && settingsManager.lastSongId != -1L
                if (!restore) return@withContext Triple(false, null, 0L)

                val song = Song(
                    id = settingsManager.lastSongId,
                    title = settingsManager.lastSongTitle ?: "Unknown Title",
                    artist = settingsManager.lastSongArtist ?: "Unknown Artist",
                    data = "",
                    duration = settingsManager.lastSongDuration,
                    albumId = 0L,
                    uri = Uri.parse(settingsManager.lastSongUri ?: ""),
                    artworkUri = Uri.parse(settingsManager.lastSongArtwork ?: "")
                )
                Triple(true, song, settingsManager.lastPosition)
            }

            if (shouldRestore && savedSong != null) {
                _currentSong.value = savedSong
                _currentPosition.value = lastPos
                _duration.value = savedSong.duration
                _currentAlbumArt.value = savedSong.artworkUri.toString()
                _queueState.value = restoreQueueState(settingsManager.playbackQueueJson)
                    ?.let { restored ->
                        if (restored.current != null) {
                            restored
                        } else {
                            restored.copy(current = QueueItem(savedSong, QueueSource.RESTORED))
                        }
                    }
                    ?: PlaybackQueueState(
                        current = QueueItem(savedSong, QueueSource.RESTORED),
                        source = QueueSource.RESTORED
                    )

                exoPlayer?.apply {
                    val uriString = savedSong.uri.toString()
                    if (uriString.isNotBlank()) {
                        try {
                            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                            prepare()
                            seekTo(lastPos)
                        } catch (e: Exception) {}
                    }
                    applyPlayerRepeatMode()
                }
            }
        }
    }

    fun playSong(song: Song) {
        playSongFromLibrary(song)
    }

    fun playSongFromCollection(
        song: Song,
        collection: List<Song>,
        source: QueueSource = QueueSource.LIBRARY,
        sourceName: String? = null,
        shuffle: Boolean = false
    ) {
        startQueueSession(
            songs = collection.ifEmpty { listOf(song) },
            startSong = song,
            source = source,
            sourceName = sourceName,
            shuffle = shuffle
        )
    }

    @OptIn(UnstableApi::class)
    private fun playSongNow(song: Song, toggleIfSame: Boolean = true) {
        if (toggleIfSame && sameSong(_currentSong.value, song) && exoPlayer != null) {
            togglePlayback()
            return
        }

        _errorMessage.value = null
        saveCurrentPlaybackState()

        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(song.uri))
            applyPlayerRepeatMode()
            prepare()
            play()
        }

        _currentSong.value = song
        syncQueueCurrentSong(song)
        viewModelScope.launch {
            playbackHistoryRepository.addToHistory(
                PlaybackHistoryEntity(
                    songId = song.uri.toString(),
                    title = song.title,
                    artist = song.artist,
                    artworkUrl = song.artworkUri.toString()
                )
            )
            loadHistory()
        }
        maybeRefreshAutoplayRecommendations()
        _isPlaying.value = true
        startProgressUpdate()
        updateNotification(song, true)

        val hasPreloadedArt = song.artworkUri != Uri.EMPTY && song.artworkUri.toString().startsWith("http")
        _currentAlbumArt.value = if (hasPreloadedArt) song.artworkUri.toString() else null

        viewModelScope.launch(Dispatchers.IO) {
            val url = if (hasPreloadedArt) song.artworkUri.toString() else getSongImageUrl(song)
            val lyrics = fetchLyrics(song.artist, song.title)

            withContext(Dispatchers.Main) {
                _currentAlbumArt.value = url
                _currentSong.value = _currentSong.value?.copy(lyrics = lyrics)
                _lyricsLines.value = lyrics?.let { LyricsParser.parseLrc(it) } ?: emptyList()
                updateNotification(song, true)
            }
        }
    }

    fun loadRecentHistory() {
        viewModelScope.launch { loadHistory() }
    }

    private suspend fun loadHistory() {
        _recentHistory.value = playbackHistoryRepository.getRecentHistory(10)
        _fullHistory.value = playbackHistoryRepository.getFullHistory()
    }

    private fun startQueueSession(
        songs: List<Song>,
        startSong: Song?,
        source: QueueSource,
        sourceName: String? = null,
        shuffle: Boolean = false
    ) {
        val selectedSong = startSong ?: songs.firstOrNull()
        if (selectedSong == null) {
            _errorMessage.value = getApplication<Application>().getString(R.string.no_songs_available)
            return
        }

        isShuffleMode.value = shuffle
        settingsManager.shuffleEnabled = shuffle

        val distinctSongs = distinctSongs(songs.ifEmpty { listOf(selectedSong) })
        val startIndex = distinctSongs.indexOfFirst { sameSong(it, selectedSong) }.coerceAtLeast(0)
        val ordered = if (shuffle) {
            listOf(distinctSongs[startIndex]) + distinctSongs
                .filterIndexed { index, _ -> index != startIndex }
                .shuffled()
        } else {
            distinctSongs.drop(startIndex) + distinctSongs.take(startIndex)
        }

        val current = queueItem(ordered.firstOrNull() ?: selectedSong, source, reason = sourceName)
        _queueState.value = PlaybackQueueState(
            current = current,
            previous = emptyList(),
            upNext = ordered.drop(1).map { queueItem(it, source, reason = sourceName) },
            source = source,
            sourceName = sourceName
        )
        persistQueueState()
        playQueueItem(current)
    }

    private fun playQueueItem(item: QueueItem) {
        if (item.song.requiresRemoteExtraction()) {
            playYouTubeSongNow(
                YouTubeResult(
                    videoId = item.song.data,
                    title = item.song.title,
                    artist = item.song.artist,
                    thumbnailUrl = item.song.artworkUri.toString(),
                    type = ResultType.SONG
                )
            )
        } else {
            playSongNow(item.song, toggleIfSame = false)
        }
    }

    private fun queueItem(
        song: Song,
        source: QueueSource,
        reason: String? = null,
        userInserted: Boolean = false
    ): QueueItem = QueueItem(song, source, reason, userInserted)

    private fun appendPrevious(previous: List<QueueItem>, current: QueueItem?): List<QueueItem> {
        return (previous + listOfNotNull(current)).takeLast(50)
    }

    private fun distinctSongs(songs: List<Song>): List<Song> {
        val seen = mutableSetOf<String>()
        return songs.filter { seen.add(songIdentityKey(it)) }
    }

    private fun sameSong(first: Song?, second: Song?): Boolean {
        if (first == null || second == null) return false
        return first.id == second.id || metadataIdentity(first.title, first.artist) == metadataIdentity(second.title, second.artist)
    }

    private fun songIdentityKey(song: Song): String {
        val remoteId = song.data.takeIf { it.isNotBlank() && song.uri.toString().startsWith("http", ignoreCase = true) }
        return remoteId ?: "${song.id}|${metadataIdentity(song.title, song.artist)}"
    }

    private fun metadataIdentity(title: String, artist: String): String =
        "${normalizeSearchText(title)}|${normalizeSearchText(artist)}"

    private fun resultToSong(result: YouTubeResult): Song =
        Song(
            id = result.videoId.hashCode().toLong(),
            title = result.title,
            artist = result.artist,
            data = result.videoId,
            duration = 0L,
            albumId = 0L,
            uri = Uri.parse("https://music.youtube.com/watch?v=${result.videoId}"),
            artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
        )

    private fun syncQueueCurrentSong(song: Song) {
        val state = _queueState.value
        val current = state.current
        if (current == null || sameSong(current.song, song)) {
            _queueState.value = state.copy(current = (current ?: queueItem(song, QueueSource.LIBRARY)).copy(song = song))
            persistQueueState()
        }
    }

    private fun applyPlayerRepeatMode() {
        exoPlayer?.repeatMode = if (_repeatMode.value == RepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    private fun maybeRefreshAutoplayRecommendations() {
        if (!isAutoPlayEnabled.value) return
        val state = _queueState.value
        if (state.upNext.size + state.autoplay.size >= 6 || state.isAutoplayLoading) return
        refreshAutoplayRecommendations(force = false)
    }

    private fun refreshAutoplayRecommendations(
        force: Boolean = false,
        playFirstWhenReady: Boolean = false
    ) {
        if (!isAutoPlayEnabled.value) return
        val seed = _queueState.value.current?.song ?: _currentSong.value ?: return
        if (!force && _queueState.value.autoplay.isNotEmpty()) return

        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch(Dispatchers.IO) {
            _queueState.value = _queueState.value.copy(isAutoplayLoading = true, autoplayError = null)
            try {
                val recommendations = generateAutoplayRecommendations(seed, _queueState.value)
                withContext(Dispatchers.Main) {
                    _queueState.value = _queueState.value.copy(
                        autoplay = recommendations,
                        isAutoplayLoading = false,
                        autoplayError = if (recommendations.isEmpty()) {
                            getApplication<Application>().getString(R.string.queue_autoplay_empty)
                        } else {
                            null
                        }
                    )
                    persistQueueState()

                    if (playFirstWhenReady && recommendations.isNotEmpty()) {
                        skipToNext()
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        _queueState.value = _queueState.value.copy(
                            isAutoplayLoading = false,
                            autoplayError = getApplication<Application>().getString(R.string.queue_autoplay_error)
                        )
                        persistQueueState()
                    }
                }
            }
        }
    }

    private data class RecommendationCandidate(
        val song: Song,
        val score: Int,
        val reason: String
    )

    private suspend fun generateAutoplayRecommendations(
        seed: Song,
        state: PlaybackQueueState
    ): List<QueueItem> = withContext(Dispatchers.IO) {
        val excluded = (state.previous.takeLast(20) + listOfNotNull(state.current) + state.upNext + state.autoplay)
            .map { songIdentityKey(it.song) }
            .toMutableSet()
        val historyCounts = _fullHistory.value.groupingBy { metadataIdentity(it.title, it.artist) }.eachCount()
        val candidates = LinkedHashMap<String, RecommendationCandidate>()

        fun addCandidate(song: Song, baseScore: Int, reason: String) {
            val key = songIdentityKey(song)
            if (key in excluded || sameSong(seed, song)) return
            val score = recommendationScore(seed, song, historyCounts) + baseScore
            if (score < 24) return
            val existing = candidates[key]
            if (existing == null || score > existing.score) {
                candidates[key] = RecommendationCandidate(song, score, reason)
            }
        }

        _songs.value.forEach { song ->
            addCandidate(song, 0, recommendationReason(seed, song))
        }

        _youtubeSearchResults.value
            .filter { it.type == ResultType.SONG }
            .forEachIndexed { index, result ->
                addCandidate(resultToSong(result), (12 - index).coerceAtLeast(0), recommendationReason(seed, resultToSong(result)))
            }

        if (candidates.size < 12) {
            recommendationQueries(seed).forEach { query ->
                val cacheKey = normalizeSearchText(query)
                val results = searchResultsCache[cacheKey] ?: run {
                    val merged = mergeSearchResults(
                        query = query,
                        groups = listOf(
                            DeezerApi.search(query),
                            YouTube.search(query)
                        )
                    )
                    if (searchResultsCache.size > 24) searchResultsCache.clear()
                    searchResultsCache[cacheKey] = merged
                    merged
                }

                results
                    .filter { it.type == ResultType.SONG }
                    .take(10)
                    .forEachIndexed { index, result ->
                        addCandidate(
                            song = resultToSong(result),
                            baseScore = (22 - index).coerceAtLeast(4),
                            reason = query
                        )
                    }
            }
        }

        val selected = selectDiverseRecommendations(candidates.values.toList(), seed)
        if (selected.isNotEmpty()) {
            return@withContext selected.map {
                queueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason)
            }
        }

        _songs.value
            .filterNot { songIdentityKey(it) in excluded || sameSong(seed, it) }
            .sortedBy { stableSearchRank(seed.title, songIdentityKey(it)) }
            .take(8)
            .map { queueItem(it, QueueSource.AUTOPLAY, reason = getApplication<Application>().getString(R.string.from_your_library)) }
    }

    private fun recommendationScore(
        seed: Song,
        candidate: Song,
        historyCounts: Map<String, Int>
    ): Int {
        val seedArtist = normalizeSearchText(seed.artist)
        val candidateArtist = normalizeSearchText(candidate.artist)
        val seedAlbum = normalizeSearchText(seed.album)
        val candidateAlbum = normalizeSearchText(candidate.album)
        val seedTokens = recommendationTokens(seed)
        val candidateTokens = recommendationTokens(candidate)
        val seedStyles = styleTokens(seed)
        val candidateStyles = styleTokens(candidate)

        var score = 0
        if (seedArtist.isNotBlank() && seedArtist == candidateArtist) score += 70
        if (seedArtist.isNotBlank() && candidateArtist.contains(seedArtist)) score += 26
        if (seedAlbum.isNotBlank() && seedAlbum == candidateAlbum) score += 22
        score += seedTokens.intersect(candidateTokens).size * 8
        score += seedStyles.intersect(candidateStyles).size * 28
        score += kotlin.math.min((historyCounts[metadataIdentity(candidate.title, candidate.artist)] ?: 0) * 3, 15)

        val recentKeys = _fullHistory.value.take(12).map { metadataIdentity(it.title, it.artist) }.toSet()
        if (metadataIdentity(candidate.title, candidate.artist) in recentKeys) score -= 30
        if (candidate.title.equals(seed.title, ignoreCase = true) && candidate.artist.equals(seed.artist, ignoreCase = true)) score -= 100

        return score
    }

    private fun recommendationReason(seed: Song, candidate: Song): String {
        val sharedStyles = styleTokens(seed).intersect(styleTokens(candidate))
        return when {
            normalizeSearchText(seed.artist).isNotBlank() &&
                normalizeSearchText(seed.artist) == normalizeSearchText(candidate.artist) -> seed.artist
            sharedStyles.isNotEmpty() -> sharedStyles.first()
            candidate.album.isNotBlank() && normalizeSearchText(candidate.album) == normalizeSearchText(seed.album) -> candidate.album
            else -> getApplication<Application>().getString(R.string.autoplay)
        }
    }

    private fun recommendationQueries(seed: Song): List<String> {
        val artist = seed.artist.takeIf { normalizeSearchText(it).isNotBlank() && !it.contains("unknown", ignoreCase = true) }
        val styles = styleTokens(seed)
        val baseTitle = seed.title.replace(Regex("\\(.*?\\)|\\[.*?]"), "").trim()
        val queries = LinkedHashSet<String>()

        if (artist != null) {
            styles.forEach { style -> queries += "$artist $style music" }
            queries += "$artist similar songs"
            queries += "$artist $baseTitle"
        }
        styles.forEach { style -> queries += "$style music ${artist.orEmpty()}".trim() }
        if (baseTitle.isNotBlank()) queries += "$baseTitle ${artist.orEmpty()} audio".trim()

        return queries.filter { it.isNotBlank() }.take(4)
    }

    private fun selectDiverseRecommendations(
        candidates: List<RecommendationCandidate>,
        seed: Song
    ): List<RecommendationCandidate> {
        val artistCounts = mutableMapOf<String, Int>()
        val sorted = candidates.sortedWith(
            compareByDescending<RecommendationCandidate> { it.score }
                .thenBy { stableSearchRank(seed.title, songIdentityKey(it.song)) }
        )
        val selected = mutableListOf<RecommendationCandidate>()

        sorted.forEach { candidate ->
            val artist = normalizeSearchText(candidate.song.artist)
            val count = artistCounts[artist] ?: 0
            val lastTwoSameArtist = selected.takeLast(2).all {
                normalizeSearchText(it.song.artist) == artist
            } && selected.size >= 2
            if (count >= 3 || lastTwoSameArtist) return@forEach

            selected += candidate
            artistCounts[artist] = count + 1
            if (selected.size >= 12) return selected
        }

        return selected
    }

    private fun recommendationTokens(song: Song): Set<String> =
        expandedSearchTokens(normalizeSearchText("${song.title} ${song.artist} ${song.album}"))

    private fun styleTokens(song: Song): Set<String> {
        val text = normalizeSearchText("${song.title} ${song.artist} ${song.album}")
        val styles = linkedSetOf<String>()
        val groups = mapOf(
            "phonk" to setOf("phonk", "phonky", "drift"),
            "slowed" to setOf("slowed", "slow", "reverb"),
            "nightcore" to setOf("nightcore", "sped", "speed up", "sped up"),
            "lofi" to setOf("lofi", "lo fi", "chill"),
            "trap" to setOf("trap", "drill"),
            "rap" to setOf("rap", "hip hop"),
            "rock" to setOf("rock", "metal", "punk"),
            "electronic" to setOf("edm", "house", "techno", "electro"),
            "latin" to setOf("reggaeton", "salsa", "bachata", "corrido"),
            "pop" to setOf("pop", "dance")
        )
        groups.forEach { (style, aliases) ->
            if (aliases.any { alias -> text.contains(alias) }) styles += style
        }
        return styles
    }

    private fun buildHistoryQueueSongs(
        selected: PlaybackHistoryEntity,
        selectedRemoteMatch: YouTubeResult?
    ): List<Song> {
        val songsByUri = _songs.value.associateBy { it.uri.toString() }
        val songsByMetadata = _songs.value.associateBy { metadataIdentity(it.title, it.artist) }
        val selectedKey = metadataIdentity(selected.title, selected.artist)
        val seen = mutableSetOf<String>()

        return _fullHistory.value.mapNotNull { history ->
            val key = metadataIdentity(history.title, history.artist)
            val song = songsByUri[history.songId] ?: songsByMetadata[key] ?: if (key == selectedKey && selectedRemoteMatch != null) {
                resultToSong(
                    YouTubeResult(
                        videoId = selectedRemoteMatch.videoId,
                        title = history.title,
                        artist = history.artist,
                        thumbnailUrl = history.artworkUrl.orEmpty(),
                        type = ResultType.SONG
                    )
                )
            } else {
                null
            }
            song?.takeIf { seen.add(songIdentityKey(it)) }
        }
    }

    private fun persistQueueState() {
        if (!settingsManager.saveLastPlayback) return
        settingsManager.playbackQueueJson = encodeQueueState(_queueState.value)
    }

    private fun encodeQueueState(state: PlaybackQueueState): String {
        return JSONObject().apply {
            put("source", state.source.name)
            put("sourceName", state.sourceName)
            put("current", state.current?.toJson())
            put("previous", JSONArray().apply { state.previous.forEach { put(it.toJson()) } })
            put("upNext", JSONArray().apply { state.upNext.forEach { put(it.toJson()) } })
            put("autoplay", JSONArray().apply { state.autoplay.forEach { put(it.toJson()) } })
        }.toString()
    }

    private fun restoreQueueState(payload: String): PlaybackQueueState? {
        if (payload.isBlank()) return null
        return runCatching {
            val json = JSONObject(payload)
            PlaybackQueueState(
                current = json.optJSONObject("current")?.toQueueItem(),
                previous = json.optJSONArray("previous").toQueueItems(),
                upNext = json.optJSONArray("upNext").toQueueItems(),
                autoplay = json.optJSONArray("autoplay").toQueueItems(),
                source = runCatching { QueueSource.valueOf(json.optString("source", QueueSource.RESTORED.name)) }
                    .getOrDefault(QueueSource.RESTORED),
                sourceName = json.optString("sourceName").takeIf { it.isNotBlank() && it != "null" }
            )
        }.getOrNull()
    }

    private fun QueueItem.toJson(): JSONObject =
        JSONObject().apply {
            put("source", source.name)
            put("reason", reason)
            put("userInserted", userInserted)
            put("song", song.toJson())
        }

    private fun Song.toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("artist", artist)
            put("data", data)
            put("duration", duration)
            put("albumId", albumId)
            put("uri", uri.toString())
            put("artworkUri", artworkUri.toString())
            put("album", album)
            put("dateAdded", dateAdded)
            put("lyrics", lyrics)
            put("isFavorite", isFavorite)
            put("isExplicit", isExplicit)
        }

    private fun JSONObject.toQueueItem(): QueueItem? {
        val song = optJSONObject("song")?.toSong() ?: return null
        val source = runCatching { QueueSource.valueOf(optString("source", QueueSource.RESTORED.name)) }
            .getOrDefault(QueueSource.RESTORED)
        return QueueItem(
            song = song,
            source = source,
            reason = optString("reason").takeIf { it.isNotBlank() && it != "null" },
            userInserted = optBoolean("userInserted", false)
        )
    }

    private fun JSONObject.toSong(): Song =
        Song(
            id = optLong("id"),
            title = optString("title"),
            artist = optString("artist"),
            data = optString("data"),
            duration = optLong("duration", 0L),
            albumId = optLong("albumId", 0L),
            uri = optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
            artworkUri = optString("artworkUri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
            album = optString("album"),
            dateAdded = optLong("dateAdded", 0L),
            lyrics = optString("lyrics").takeIf { it.isNotBlank() && it != "null" },
            isFavorite = optBoolean("isFavorite", false),
            isExplicit = optBoolean("isExplicit", false)
        )

    private fun JSONArray?.toQueueItems(): List<QueueItem> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.toQueueItem()?.let(::add)
            }
        }
    }

    fun playHistoryItem(history: PlaybackHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRemoteHistoryItem = history.songId.startsWith("http", ignoreCase = true)

            if (isRemoteHistoryItem) {
                val match = YouTube.search("${history.title} ${history.artist} audio")
                    .firstOrNull { it.type == ResultType.SONG }

                if (match != null) {
                    val historyContext = buildHistoryQueueSongs(history, match)
                    withContext(Dispatchers.Main) {
                        val song = resultToSong(
                            YouTubeResult(
                                videoId = match.videoId,
                                title = history.title,
                                artist = history.artist,
                                thumbnailUrl = history.artworkUrl.orEmpty()
                            )
                        )
                        startQueueSession(
                            songs = historyContext.ifEmpty { listOf(song) },
                            startSong = song,
                            source = QueueSource.HISTORY,
                            sourceName = getApplication<Application>().getString(R.string.history_tab)
                        )
                    }
                } else {
                    _errorMessage.value = getApplication<Application>().getString(R.string.history_reload_failed)
                }
                return@launch
            }

            val localSong = _songs.value.firstOrNull { song ->
                song.uri.toString() == history.songId ||
                    (song.title == history.title && song.artist == history.artist)
            }

            if (localSong != null) {
                val historyContext = buildHistoryQueueSongs(history, null)
                withContext(Dispatchers.Main) {
                    startQueueSession(
                        songs = historyContext.ifEmpty { listOf(localSong) },
                        startSong = localSong,
                        source = QueueSource.HISTORY,
                        sourceName = getApplication<Application>().getString(R.string.history_tab)
                    )
                }
            } else {
                _errorMessage.value = getApplication<Application>().getString(R.string.song_unavailable)
            }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val playlistSongs = songsForPlaylist(playlist)
        startQueueSession(
            songs = playlistSongs,
            startSong = playlistSongs.firstOrNull(),
            source = QueueSource.PLAYLIST,
            sourceName = playlist.name,
            shuffle = shuffle
        )
    }

    fun playSongs(collection: List<Song>, shuffle: Boolean = false) {
        startQueueSession(
            songs = collection,
            startSong = collection.firstOrNull(),
            source = QueueSource.LIBRARY,
            sourceName = getApplication<Application>().getString(R.string.library),
            shuffle = shuffle
        )
    }

    fun playSongFromLibrary(song: Song) {
        val localContext = _songs.value.takeIf { librarySongs ->
            librarySongs.any { sameSong(it, song) }
        }.orEmpty()

        startQueueSession(
            songs = localContext.ifEmpty { listOf(song) },
            startSong = song,
            source = if (localContext.isEmpty()) QueueSource.SEARCH else QueueSource.LIBRARY,
            sourceName = if (localContext.isEmpty()) null else getApplication<Application>().getString(R.string.library)
        )
    }

    fun playSongFromSearch(song: Song, relatedSongs: List<Song>, query: String) {
        startQueueSession(
            songs = relatedSongs.ifEmpty { listOf(song) },
            startSong = song,
            source = QueueSource.SEARCH,
            sourceName = query.takeIf { it.isNotBlank() }
        )
    }

    fun playSongFromArtist(song: Song, artistSongs: List<Song>) {
        startQueueSession(
            songs = artistSongs.ifEmpty { listOf(song) },
            startSong = song,
            source = QueueSource.ARTIST,
            sourceName = song.artist.takeIf { it.isNotBlank() }
        )
    }

    fun playYouTubeSong(result: YouTubeResult) {
        val song = resultToSong(result)
        val related = _youtubeSearchResults.value
            .filter { it.type == ResultType.SONG }
            .map(::resultToSong)

        startQueueSession(
            songs = related.ifEmpty { listOf(song) },
            startSong = song,
            source = QueueSource.SEARCH,
            sourceName = result.artist.takeIf { it.isNotBlank() }
        )
    }

    fun addSongNext(song: Song) {
        if (_queueState.value.current == null) {
            startQueueSession(listOf(song), song, QueueSource.USER)
        } else {
            _queueState.value = _queueState.value.copy(
                upNext = listOf(queueItem(song, QueueSource.USER, userInserted = true)) + _queueState.value.upNext
            )
            persistQueueState()
        }
    }

    fun addSongToQueue(song: Song) {
        if (_queueState.value.current == null) {
            startQueueSession(listOf(song), song, QueueSource.USER)
        } else {
            _queueState.value = _queueState.value.copy(
                upNext = _queueState.value.upNext + queueItem(song, QueueSource.USER, userInserted = true)
            )
            persistQueueState()
        }
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        val queuedSongs = songsForPlaylist(playlist)
        addSongsToQueue(queuedSongs, QueueSource.PLAYLIST, playlist.name)
    }

    fun addSongsToQueue(collection: List<Song>) {
        addSongsToQueue(collection, QueueSource.USER, null)
    }

    private fun addSongsToQueue(collection: List<Song>, source: QueueSource, sourceName: String?) {
        if (collection.isEmpty()) return
        if (_queueState.value.current == null) {
            startQueueSession(collection, collection.firstOrNull(), source, sourceName)
        } else {
            _queueState.value = _queueState.value.copy(
                upNext = _queueState.value.upNext + collection.map {
                    queueItem(it, source, reason = sourceName, userInserted = true)
                }
            )
            persistQueueState()
        }
    }

    suspend fun resolveShareUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val videoId = song.data.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
            ?: runCatching {
                YouTube.search("${song.title} ${song.artist} audio")
                    .firstOrNull { it.type == ResultType.SONG }
                    ?.videoId
            }.getOrNull()

        videoId?.let { "https://music.youtube.com/watch?v=$it" }
    }

    suspend fun resolveShareUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            YouTube.search("$title $artist audio")
                .firstOrNull { it.type == ResultType.SONG }
                ?.videoId
                ?.let { "https://music.youtube.com/watch?v=$it" }
        }.getOrNull()
    }

    fun clearManualQueue() {
        _queueState.value = _queueState.value.copy(
            upNext = emptyList(),
            autoplay = emptyList(),
            autoplayError = null,
            isAutoplayLoading = false
        )
        persistQueueState()
    }

    fun playUpNext(index: Int) {
        val state = _queueState.value
        val item = state.upNext.getOrNull(index) ?: return
        _queueState.value = state.copy(
            current = item,
            previous = (state.previous + listOfNotNull(state.current) + state.upNext.take(index)).takeLast(50),
            upNext = state.upNext.drop(index + 1),
            autoplayError = null
        )
        persistQueueState()
        playQueueItem(item)
    }

    fun playAutoplayItem(index: Int) {
        val state = _queueState.value
        val item = state.autoplay.getOrNull(index) ?: return
        _queueState.value = state.copy(
            current = item,
            previous = appendPrevious(state.previous, state.current),
            autoplay = state.autoplay.drop(index + 1),
            autoplayError = null
        )
        persistQueueState()
        playQueueItem(item)
        maybeRefreshAutoplayRecommendations()
    }

    fun replayPrevious(index: Int) {
        val state = _queueState.value
        val item = state.previous.getOrNull(index) ?: return
        _queueState.value = state.copy(
            current = item,
            previous = state.previous.take(index),
            upNext = state.previous.drop(index + 1) + listOfNotNull(state.current) + state.upNext,
            autoplayError = null
        )
        persistQueueState()
        playQueueItem(item)
    }

    fun removeFromQueue(index: Int) {
        val state = _queueState.value
        if (index !in state.upNext.indices) return
        _queueState.value = state.copy(
            upNext = state.upNext.filterIndexed { itemIndex, _ -> itemIndex != index }
        )
        persistQueueState()
    }

    fun moveQueueItem(index: Int, direction: Int) {
        val state = _queueState.value
        val targetIndex = (index + direction).coerceIn(0, state.upNext.lastIndex)
        if (index !in state.upNext.indices || index == targetIndex) return

        val next = state.upNext.toMutableList()
        val item = next.removeAt(index)
        next.add(targetIndex, item)
        _queueState.value = state.copy(upNext = next)
        persistQueueState()
    }

    fun moveQueueItemToNext(index: Int) {
        val state = _queueState.value
        val item = state.upNext.getOrNull(index) ?: return
        _queueState.value = state.copy(
            upNext = listOf(item) + state.upNext.filterIndexed { itemIndex, _ -> itemIndex != index }
        )
        persistQueueState()
    }

    private fun playYouTubeSongNow(result: YouTubeResult) {
        extractionJob?.cancel()

        val newExtractionJob = viewModelScope.launch(Dispatchers.IO) {
            _isExtracting.value = true
            _errorMessage.value = null

            try {
                var realYtId = deezerToYoutubeMap[result.videoId]

                if (realYtId == null) {
                    val ytMatch = YouTube.search("${result.title} ${result.artist} audio").firstOrNull { it.type == ResultType.SONG }
                    realYtId = ytMatch?.videoId
                        ?: throw Exception(getApplication<Application>().getString(R.string.youtube_version_not_found))
                    deezerToYoutubeMap[result.videoId] = realYtId
                }

                val cachedUrl = audioStreamCache[realYtId]
                if (cachedUrl != null) {
                    val virtualSong = Song(
                        id = result.videoId.hashCode().toLong(),
                        title = result.title,
                        artist = result.artist,
                        data = result.videoId,
                        duration = 1000L,
                        albumId = 0L,
                        uri = Uri.parse(cachedUrl),
                        artworkUri = if (result.thumbnailUrl.isNotEmpty()) Uri.parse(result.thumbnailUrl) else Uri.EMPTY
                    )
                    rememberPlaylistSong(virtualSong)

                    withContext(Dispatchers.Main) {
                        _currentAlbumArt.value = result.thumbnailUrl
                        playSongNow(virtualSong, toggleIfSame = false)

                        launch {
                            delay(800)
                            _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                        }
                    }

                    prefetchNextSongInList(result.videoId)
                    return@launch
                }

                val videoUrl = "https://www.youtube.com/watch?v=$realYtId"
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

                if (!isActive) return@launch

                val audioStream = streamInfo.audioStreams
                    .filter { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
                    .maxByOrNull { it.bitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.bitrate }

                if (audioStream != null) {
                    audioStreamCache[realYtId] = audioStream.url ?: ""

                    val virtualSong = Song(
                        id = result.videoId.hashCode().toLong(),
                        title = result.title,
                        artist = result.artist,
                        data = result.videoId,
                        duration = streamInfo.duration * 1000L,
                        albumId = 0L,
                        uri = Uri.parse(audioStream.url ?: ""),
                        artworkUri = if (result.thumbnailUrl.isNotEmpty()) Uri.parse(result.thumbnailUrl) else Uri.EMPTY
                    )
                    rememberPlaylistSong(virtualSong)

                    withContext(Dispatchers.Main) {
                        _currentAlbumArt.value = result.thumbnailUrl
                        playSongNow(virtualSong, toggleIfSame = false)
                        prefetchNextSongInList(result.videoId)
                    }
                } else {
                    _errorMessage.value = getApplication<Application>().getString(R.string.no_compatible_audio_streams)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _errorMessage.value = getApplication<Application>().getString(
                        R.string.audio_extraction_error_format,
                        e.localizedMessage ?: e.message.orEmpty()
                    )
                }
            } finally {
                val isActiveExtraction = extractionJob === currentCoroutineContext()[Job]
                withContext(Dispatchers.Main) {
                    if (isActiveExtraction) {
                        _isExtracting.value = false
                    }
                }
            }
        }
        extractionJob = newExtractionJob
    }

    fun togglePlayback() {
        exoPlayer?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
            _isPlaying.value = player.isPlaying
            _currentSong.value?.let { song -> updateNotification(song, player.isPlaying) }
            if (!player.isPlaying) saveCurrentPlaybackState()
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _currentPosition.value = position
        _currentSong.value?.let { updateNotification(it, _isPlaying.value) }
    }

    fun skipToNext() {
        val state = _queueState.value
        val current = state.current

        state.upNext.firstOrNull()?.let { next ->
            _queueState.value = state.copy(
                current = next,
                previous = appendPrevious(state.previous, current),
                upNext = state.upNext.drop(1),
                autoplayError = null
            )
            persistQueueState()
            playQueueItem(next)
            return
        }

        state.autoplay.firstOrNull()?.let { next ->
            _queueState.value = state.copy(
                current = next,
                previous = appendPrevious(state.previous, current),
                autoplay = state.autoplay.drop(1),
                autoplayError = null
            )
            persistQueueState()
            playQueueItem(next)
            maybeRefreshAutoplayRecommendations()
            return
        }

        if (_repeatMode.value == RepeatMode.ALL) {
            val loop = state.previous + listOfNotNull(current)
            val next = loop.firstOrNull()
            if (next != null) {
                _queueState.value = state.copy(
                    current = next,
                    previous = emptyList(),
                    upNext = loop.drop(1),
                    autoplayError = null
                )
                persistQueueState()
                playQueueItem(next)
                return
            }
        }

        if (isAutoPlayEnabled.value) {
            refreshAutoplayRecommendations(force = true, playFirstWhenReady = true)
        }
    }

    fun skipToPrevious() {
        val currentPosition = exoPlayer?.currentPosition ?: _currentPosition.value
        if (currentPosition > 3000L) {
            seekTo(0)
            return
        }

        val state = _queueState.value
        val previous = state.previous
        val previousItem = previous.lastOrNull()

        if (previousItem != null) {
            _queueState.value = state.copy(
                current = previousItem,
                previous = previous.dropLast(1),
                upNext = listOfNotNull(state.current) + state.upNext,
                autoplayError = null
            )
            persistQueueState()
            playQueueItem(previousItem)
            return
        }

        if (_repeatMode.value == RepeatMode.ALL) {
            val loop = listOfNotNull(state.current) + state.upNext
            val last = loop.lastOrNull()
            if (last != null && loop.size > 1) {
                _queueState.value = state.copy(
                    current = last,
                    previous = loop.dropLast(1),
                    upNext = emptyList(),
                    autoplayError = null
                )
                persistQueueState()
                playQueueItem(last)
            }
        }
    }

    private fun saveCurrentPlaybackState() {
        if (!settingsManager.saveLastPlayback) return
        val song = _currentSong.value ?: return
        val pos = exoPlayer?.currentPosition ?: _currentPosition.value

        settingsManager.lastSongId = song.id
        settingsManager.lastSongTitle = song.title
        settingsManager.lastSongArtist = song.artist
        settingsManager.lastSongUri = song.uri.toString()
        settingsManager.lastSongArtwork = _currentAlbumArt.value ?: ""
        settingsManager.lastSongDuration = song.duration
        settingsManager.lastPosition = pos
        settingsManager.autoplayEnabled = isAutoPlayEnabled.value
        settingsManager.shuffleEnabled = isShuffleMode.value
        settingsManager.repeatModeName = _repeatMode.value.name
        settingsManager.playbackQueueJson = encodeQueueState(_queueState.value)
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFiles = repository.getAudioFiles(filterVoiceNotes.value)
            _songs.value = audioFiles
        }
    }

    private fun ensureFavoritesPlaylistExists() {
        viewModelScope.launch(Dispatchers.IO) {
            val allPlaylists = playlistDao.getAllPlaylistsOnce()
            if (allPlaylists.none { it.name in favoritePlaylistNames() }) {
                createPlaylist(
                    getApplication<Application>().getString(R.string.favorites_playlist_name),
                    getApplication<Application>().getString(R.string.favorites_playlist_description)
                )
            }
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(
                PlaylistEntity(name = name, description = description ?: "", songIds = "", createdAt = System.currentTimeMillis())
            )
        }
    }

    fun createPlaylistWithSong(name: String, description: String? = null, song: Song) {
        rememberPlaylistSong(song)
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = name,
                    description = description ?: "",
                    songIds = song.id.toString(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updatePlaylistDetails(playlist: Playlist, name: String, description: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.updatePlaylist(
                playlist.toEntity(
                    name = name.trim(),
                    description = description?.trim().orEmpty()
                )
            )
        }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song) {
        rememberPlaylistSong(song)
        addSongToPlaylist(playlist, song.id)
    }

    fun addSongToPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val newIds = (playlist.songIds + songId).distinct().joinToString(",")
            playlistDao.updatePlaylist(playlist.toEntity(songIds = newIds))
        }
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val newIds = playlist.songIds.filter { it != songId }.joinToString(",")
            playlistDao.updatePlaylist(playlist.toEntity(songIds = newIds))
        }
    }

    fun moveSongInPlaylist(playlist: Playlist, songId: Long, direction: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = playlist.songIds.toMutableList()
            val fromIndex = ids.indexOf(songId)
            if (fromIndex == -1) return@launch

            val toIndex = (fromIndex + direction).coerceIn(0, ids.lastIndex)
            if (fromIndex == toIndex) return@launch

            val movedId = ids.removeAt(fromIndex)
            ids.add(toIndex, movedId)
            playlistDao.updatePlaylist(playlist.toEntity(songIds = ids.joinToString(",")))
        }
    }

    fun updatePlaylistSongOrder(playlist: Playlist, orderedSongIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val knownIds = playlist.songIds.toSet()
            val requestedIds = orderedSongIds.filter { it in knownIds }.distinct()
            val remainingIds = playlist.songIds.filterNot { it in requestedIds }
            val newIds = (requestedIds + remainingIds).joinToString(",")
            playlistDao.updatePlaylist(playlist.toEntity(songIds = newIds))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            val entityToDelete = PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description ?: "",
                songIds = playlist.songIds.joinToString(","),
                createdAt = playlist.createdAt
            )

            playlistDao.deletePlaylist(entityToDelete)
            settingsManager.setPlaylistCoverUri(playlist.id, null)
            refreshPlaylistCoverUris()
        }
    }

    fun songsForPlaylist(playlist: Playlist): List<Song> {
        val localSongsById = _songs.value.associateBy { it.id }
        return playlist.songIds.mapNotNull { songId ->
            localSongsById[songId] ?: restorePlaylistSong(songId)
        }
    }

    fun playlistCoverUri(playlistId: Long): String? =
        _playlistCoverUris.value[playlistId] ?: settingsManager.playlistCoverUri(playlistId)

    fun setPlaylistCoverUri(playlistId: Long, uri: String?) {
        settingsManager.setPlaylistCoverUri(playlistId, uri)
        _playlistCoverUris.value = _playlistCoverUris.value
            .toMutableMap()
            .apply {
                if (uri.isNullOrBlank()) remove(playlistId) else put(playlistId, uri)
            }
    }

    fun toggleFollowArtist(artistName: String) {
        val updated = _followedArtists.value.toMutableSet().apply {
            if (!add(artistName)) remove(artistName)
        }
        _followedArtists.value = updated
        settingsManager.followedArtists = updated
    }

    private fun refreshPlaylistCoverUris() {
        viewModelScope.launch(Dispatchers.IO) {
            val covers = playlistDao.getAllPlaylistsOnce()
                .mapNotNull { entity ->
                    settingsManager.playlistCoverUri(entity.id)?.let { uri -> entity.id to uri }
                }
                .toMap()
            _playlistCoverUris.value = covers
        }
    }

    private fun Playlist.toEntity(
        name: String = this.name,
        description: String = this.description.orEmpty(),
        songIds: String = this.songIds.joinToString(",")
    ): PlaylistEntity {
        return PlaylistEntity(
            id = id,
            name = name,
            description = description,
            songIds = songIds,
            createdAt = createdAt
        )
    }

    private fun rememberPlaylistSong(song: Song) {
        if (!song.requiresRemoteExtraction()) return

        val payload = JSONObject().apply {
            put("title", song.title)
            put("artist", song.artist)
            put("data", song.data)
            put("duration", song.duration)
            put("uri", song.uri.toString())
            put("artworkUri", song.artworkUri.toString())
            put("album", song.album)
            put("dateAdded", song.dateAdded)
            put("isExplicit", song.isExplicit)
        }.toString()

        settingsManager.setPlaylistSongMetadata(song.id, payload)
    }

    private fun restorePlaylistSong(songId: Long): Song? {
        val payload = settingsManager.playlistSongMetadata(songId) ?: return null
        return runCatching {
            val json = JSONObject(payload)
            Song(
                id = songId,
                title = json.optString("title"),
                artist = json.optString("artist"),
                data = json.optString("data"),
                duration = json.optLong("duration", 0L),
                albumId = 0L,
                uri = json.optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
                artworkUri = json.optString("artworkUri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
                album = json.optString("album"),
                dateAdded = json.optLong("dateAdded", 0L),
                isExplicit = json.optBoolean("isExplicit", false)
            )
        }.getOrNull()
    }

    private fun Song.requiresRemoteExtraction(): Boolean {
        return uri.toString().startsWith("http", ignoreCase = true) && data.isNotBlank()
    }

    fun toggleLike(song: Song) {
        rememberPlaylistSong(song)
        viewModelScope.launch(Dispatchers.IO) {
            val allPlaylists = playlistDao.getAllPlaylistsOnce()
            var likePlaylistEntity = allPlaylists.find { it.name in favoritePlaylistNames() }

            if (likePlaylistEntity == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = getApplication<Application>().getString(R.string.favorites_playlist_name),
                        description = getApplication<Application>().getString(R.string.favorites_playlist_description),
                        songIds = "",
                        createdAt = System.currentTimeMillis()
                    )
                )
                delay(200)
                val updatedPlaylists = playlistDao.getAllPlaylistsOnce()
                likePlaylistEntity = updatedPlaylists.find { it.name in favoritePlaylistNames() }
            }

            likePlaylistEntity?.let { entity ->
                val currentIds = entity.songIds.split(",").filter { it.isNotBlank() }.map { it.toLong() }

                val newIdsString = if (currentIds.contains(song.id)) {
                    currentIds.filter { it != song.id }.joinToString(",")
                } else {
                    (currentIds + song.id).distinct().joinToString(",")
                }

                playlistDao.updatePlaylist(
                    PlaylistEntity(
                        id = entity.id,
                        name = entity.name,
                        description = entity.description,
                        songIds = newIdsString,
                        createdAt = entity.createdAt
                    )
                )
            }
        }
    }

    fun searchYouTube(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            searchJob?.cancel()
            _youtubeSearchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        val cacheKey = normalizeSearchText(cleanQuery)
        searchResultsCache[cacheKey]?.let { cachedResults ->
            searchJob?.cancel()
            _youtubeSearchResults.value = cachedResults
            _isSearching.value = false
            return
        }

        searchJob?.cancel()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                delay(120)
                if (!isActive) return@launch

                val deezerResults = async { DeezerApi.search(cleanQuery) }
                val youtubeResults = async { YouTube.search(cleanQuery) }

                val results = mergeSearchResults(
                    query = cleanQuery,
                    groups = listOf(
                        deezerResults.await(),
                        youtubeResults.await()
                    )
                )

                if (isActive) {
                    if (searchResultsCache.size > 24) searchResultsCache.clear()
                    searchResultsCache[cacheKey] = results
                    _youtubeSearchResults.value = results
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _youtubeSearchResults.value = emptyList()
                }
            } finally {
                if (isActive) {
                    withContext(Dispatchers.Main) { _isSearching.value = false }
                }
            }
        }
    }

    private data class RankedSearchResult(
        val result: YouTubeResult,
        val score: Int,
        val order: Int
    )

    private fun mergeSearchResults(
        query: String,
        groups: List<List<YouTubeResult>>
    ): List<YouTubeResult> {
        val rankedByKey = LinkedHashMap<String, RankedSearchResult>()

        groups.flatten().forEachIndexed { index, result ->
            val key = "${result.type}:${result.videoId.ifBlank { "${result.title}|${result.artist}" }}"
            val ranked = RankedSearchResult(
                result = result,
                score = remoteSearchScore(query, result) - index,
                order = index
            )
            val existing = rankedByKey[key]
            if (existing == null || ranked.score > existing.score) {
                rankedByKey[key] = ranked
            }
        }

        return rankedByKey.values
            .sortedWith(
                compareByDescending<RankedSearchResult> { it.score }
                    .thenBy { stableSearchRank(query, it.result.videoId.ifBlank { it.result.title }) }
                    .thenBy { it.order }
            )
            .map { it.result }
            .let { ranked ->
                val perTypeCounts = mutableMapOf<ResultType, Int>()
                ranked.filter { result ->
                    val count = perTypeCounts[result.type] ?: 0
                    if (count >= 20) {
                        false
                    } else {
                        perTypeCounts[result.type] = count + 1
                        true
                    }
                }.take(48)
            }
    }

    private fun remoteSearchScore(query: String, result: YouTubeResult): Int {
        val normalizedQuery = normalizeSearchText(query)
        val queryTokens = expandedSearchTokens(normalizedQuery)
        val title = normalizeSearchText(result.title)
        val artist = normalizeSearchText(result.artist)
        val combined = "$title $artist"

        var score = when (result.type) {
            ResultType.SONG -> 12
            ResultType.ARTIST -> 10
            ResultType.PLAYLIST -> 9
            ResultType.ALBUM -> 8
        }

        if (title == normalizedQuery || artist == normalizedQuery) score += 70
        if (title.contains(normalizedQuery) || artist.contains(normalizedQuery)) score += 42
        queryTokens.forEach { token ->
            if (title.startsWith(token)) score += 18
            if (title.contains(token)) score += 14
            if (artist.startsWith(token)) score += 16
            if (artist.contains(token)) score += 12
            if (combined.contains(token)) score += 4
        }

        if (result.type == ResultType.PLAYLIST && "playlist" in combined) score += 6
        if (result.type == ResultType.ARTIST && queryTokens.any { artist.contains(it) || title.contains(it) }) score += 8

        return score
    }

    private fun expandedSearchTokens(value: String): Set<String> {
        val tokens = value.split(" ").filter { it.length > 1 }.toMutableSet()
        if ("brazilian" in tokens) tokens += setOf("brasil", "brasileiro", "brasileira")
        if ("brasilian" in tokens) tokens += setOf("brasil", "brazilian")
        if ("slowed" in tokens) tokens += setOf("slow", "reverb")
        if ("nightcore" in tokens) tokens += setOf("sped", "speed")
        if ("phonk" in tokens) tokens += setOf("drift", "phonky")
        return tokens
    }

    private fun stableSearchRank(query: String, key: String): Int =
        kotlin.math.abs("$query|$key".hashCode())

    private fun normalizeSearchText(value: String?): String {
        val withoutMarks = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return withoutMarks
            .lowercase()
            .replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)\\b(feat|ft|featuring)\\b\\.?"), " ")
            .replace(Regex("(?i)\\b(official|video|audio|lyrics|lyric|visualizer|remaster|remastered|explicit|clean|edit)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun favoritePlaylistNames(): Set<String> {
        val localizedName = getApplication<Application>().getString(R.string.favorites_playlist_name)
        return setOf(localizedName, "Me gusta", "Favorites")
    }

    fun setShuffleMode(enabled: Boolean) {
        if (isShuffleMode.value == enabled) return
        isShuffleMode.value = enabled
        settingsManager.shuffleEnabled = enabled
        if (enabled) {
            _queueState.value = _queueState.value.copy(upNext = _queueState.value.upNext.shuffled())
        }
        persistQueueState()
    }

    fun toggleShuffleMode() {
        setShuffleMode(!isShuffleMode.value)
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        settingsManager.repeatModeName = _repeatMode.value.name
        applyPlayerRepeatMode()
        persistQueueState()
    }

    fun updateTheme(theme: AppTheme) { _currentTheme.value = theme }

    fun updateFilterVoiceNotes(enabled: Boolean) {
        settingsManager.filterVoiceNotes = enabled
        filterVoiceNotes.value = enabled
        loadSongs()
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        settingsManager.keepScreenOn = enabled
        keepScreenOn.value = enabled
    }

    fun updateGapless(enabled: Boolean) {
        settingsManager.gaplessPlayback = enabled
        gaplessPlayback.value = enabled
    }

    fun updateCrossfade(duration: Float) {
        settingsManager.crossfadeDuration = duration
        crossfadeDuration.value = duration
    }

    fun updateSaveLastPlayback(enabled: Boolean) {
        settingsManager.saveLastPlayback = enabled
        saveLastPlayback.value = enabled
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerMinutes.value = minutes
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60 * 1000L)
                exoPlayer?.pause()
                _isPlaying.value = false
                stopProgressUpdate()
                sleepTimerMinutes.value = 0
                _currentSong.value?.let { song -> updateNotification(song, false) }
            }
        }
    }

    fun openSystemEqualizer(intentLauncher: (Intent) -> Unit) {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getApplication<Application>().packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        intentLauncher(intent)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val currentPos = exoPlayer?.currentPosition ?: 0L
                val currentDur = exoPlayer?.duration ?: 0L

                _currentPosition.value = currentPos

                if (currentDur > 0) {
                    _duration.value = currentDur
                    if (_currentSong.value?.duration != currentDur) {
                        _currentSong.value = _currentSong.value?.copy(duration = currentDur)
                    }
                }

                delay(250)
            }
        }
    }

    private fun stopProgressUpdate() { progressJob?.cancel() }

    private fun updateNotification(song: Song, isPlaying: Boolean) {
        val intent = Intent(getApplication(), MusicService::class.java).apply {
            putExtra("TITLE", song.title)
            putExtra("ARTIST", song.artist ?: "Unknown Artist")
            putExtra("IS_PLAYING", isPlaying)
            putExtra("ALBUM_ART_URL", _currentAlbumArt.value ?: "")
            putExtra("CURRENT_POSITION", exoPlayer?.currentPosition ?: _currentPosition.value)
            putExtra("DURATION", exoPlayer?.duration?.takeIf { it > 0 } ?: song.duration)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    suspend fun getSongImageUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val preloadedArtwork = song.artworkUri.toString()
            .takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        if (preloadedArtwork != null) return@withContext preloadedArtwork

        val cacheKey = "song_${song.id}"
        if (imageUrlCache.containsKey(cacheKey)) return@withContext imageUrlCache[cacheKey]

        val url = fetchAlbumArt(song.title, song.artist, requireTrackMatch = true)
        if (url != null) imageUrlCache[cacheKey] = url
        return@withContext url
    }

    suspend fun getDistinctSongImageUrls(
        songs: List<Song>,
        maxImages: Int = 4,
        scanLimit: Int = 40
    ): List<String> = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        val seenArtwork = mutableSetOf<String>()
        val seenMetadata = mutableSetOf<String>()

        for (song in songs.distinctBy { it.id }.take(scanLimit)) {
            val metadataKey = songArtworkMetadataKey(song)
            if (!seenMetadata.add(metadataKey)) continue

            val url = getSongImageUrl(song)?.takeIf { it.isNotBlank() } ?: continue
            val artworkKey = artworkIdentityKey(url)
            if (seenArtwork.add(artworkKey)) {
                urls.add(url)
            }

            if (urls.size >= maxImages) break
        }

        urls
    }

    suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = "artist_$artistName"
        if (imageUrlCache.containsKey(cacheKey)) return@withContext imageUrlCache[cacheKey]
        val url = fetchAlbumArt(artistName, "", requireTrackMatch = false)
        if (url != null) imageUrlCache[cacheKey] = url
        return@withContext url
    }

    private fun fetchAlbumArt(
        title: String,
        artist: String?,
        requireTrackMatch: Boolean
    ): String? {
        return try {
            val cleanQuery = normalizeArtworkText(title)
            val cleanArtist = if (artist.isNullOrBlank() || artist.contains("unknown", ignoreCase = true)) "" else artist.trim()
            val finalQueryText = "$cleanQuery $cleanArtist".trim().replace(Regex("\\s+"), " ")
            if (finalQueryText.isBlank()) return null

            val encodedQuery = URLEncoder.encode(finalQueryText, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=10"

            val response = URL(url).readText()
            val json = JSONObject(response)
            val results = json.getJSONArray("results")

            var bestUrl: String? = null
            var bestScore = 0

            for (index in 0 until results.length()) {
                val result = results.optJSONObject(index) ?: continue
                val artworkUrl = result.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
                val normalizedArtworkUrl = normalizeAppleArtworkUrl(artworkUrl)

                if (!requireTrackMatch) return normalizedArtworkUrl

                val score = artworkMetadataScore(
                    expectedTitle = title,
                    expectedArtist = cleanArtist,
                    resultTitle = result.optString("trackName"),
                    resultArtist = result.optString("artistName")
                )

                if (score > bestScore) {
                    bestScore = score
                    bestUrl = normalizedArtworkUrl
                }
            }

            bestUrl
        } catch (e: Exception) { null }
    }

    private fun artworkMetadataScore(
        expectedTitle: String,
        expectedArtist: String,
        resultTitle: String,
        resultArtist: String
    ): Int {
        val titleScore = textMatchScore(
            expected = normalizeArtworkText(expectedTitle),
            actual = normalizeArtworkText(resultTitle)
        )
        if (titleScore < 2) return 0

        val artistScore = if (expectedArtist.isBlank()) {
            1
        } else {
            textMatchScore(
                expected = normalizeArtworkText(expectedArtist),
                actual = normalizeArtworkText(resultArtist)
            )
        }
        if (expectedArtist.isNotBlank() && artistScore == 0) return 0

        return titleScore * 10 + artistScore
    }

    private fun textMatchScore(expected: String, actual: String): Int {
        if (expected.isBlank() || actual.isBlank()) return 0
        if (expected == actual) return 4
        if ((expected.length >= 4 && actual.contains(expected)) ||
            (actual.length >= 4 && expected.contains(actual))
        ) {
            return 3
        }

        val expectedTokens = expected.split(" ").filter { it.length > 1 }.toSet()
        val actualTokens = actual.split(" ").filter { it.length > 1 }.toSet()
        if (expectedTokens.isEmpty() || actualTokens.isEmpty()) return 0

        val overlap = expectedTokens.count { it in actualTokens }
        val expectedCoverage = overlap.toDouble() / expectedTokens.size.toDouble()
        val actualCoverage = overlap.toDouble() / actualTokens.size.toDouble()

        return when {
            expectedCoverage >= 0.80 && actualCoverage >= 0.50 -> 2
            expectedCoverage >= 0.60 && actualCoverage >= 0.60 -> 1
            else -> 0
        }
    }

    private fun songArtworkMetadataKey(song: Song): String {
        return listOf(
            normalizeArtworkText(song.title),
            normalizeArtworkText(song.artist),
            normalizeArtworkText(song.album)
        ).joinToString("|")
    }

    private fun artworkIdentityKey(url: String): String {
        return url
            .substringBefore("?")
            .replace(Regex("/\\d+x\\d+bb\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE), "/artwork")
    }

    private fun normalizeAppleArtworkUrl(url: String): String {
        return url
            .replace("100x100bb.jpg", "600x600bb.jpg")
            .replace("100x100bb.png", "600x600bb.png")
    }

    private fun normalizeArtworkText(value: String?): String {
        return value.orEmpty()
            .lowercase()
            .replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), " ")
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)\\b(feat|ft|featuring)\\b\\.?"), " ")
            .replace(Regex("(?i)\\b(official|video|audio|lyrics|lyric|visualizer|remaster|remastered|explicit|clean|edit)\\b"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?]"), "").trim()
        val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")

        try {
            val url = "https://lrclib.net/api/get?artist_name=$encodedArtist&track_name=$encodedTitle"
            val connection = URL(url).openConnection()
            connection.setRequestProperty("User-Agent", "FridaMusic/1.0")

            val response = connection.getInputStream().bufferedReader().readText()
            val json = JSONObject(response)

            val syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (syncedLyrics != null) return@withContext syncedLyrics

            val plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            if (plainLyrics != null) return@withContext plainLyrics
        } catch (e: Exception) {}

        try {
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
            val response = URL(url).readText()
            return@withContext JSONObject(response).optString("lyrics").takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) { return@withContext null }
    }

    private fun prefetchYouTubeAudio(videoId: String) {
        if (audioStreamCache.containsKey(videoId)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
                val audioStream = streamInfo.audioStreams
                    .filter { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
                    .maxByOrNull { it.bitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.bitrate }

                if (audioStream?.url != null) {
                    audioStreamCache[videoId] = audioStream.url ?: ""
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun prefetchNextSongInList(currentVideoId: String) {
        val ytList = _youtubeSearchResults.value
        val currentIndex = ytList.indexOfFirst { it.videoId == currentVideoId }

        if (currentIndex != -1 && currentIndex < ytList.size -1) {
            val nextVideoId = ytList[currentIndex + 1].videoId
            prefetchYouTubeAudio(nextVideoId)
        }
    }

    override fun onCleared() {
        saveCurrentPlaybackState()
        super.onCleared()
        exoPlayer?.release()
        try { getApplication<Application>().unregisterReceiver(notificationReceiver) } catch (e: Exception) {}
        getApplication<Application>().startService(Intent(getApplication(), MusicService::class.java).apply { action = "STOP_SERVICE" })
    }
}
