@file:OptIn(UnstableApi::class)

package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import com.jagr.fridamusic.domain.recommendation.AutoplayDiversity
import com.jagr.fridamusic.presentation.service.MusicService
import com.jagr.fridamusic.data.cache.getCacheDataSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

enum class RepeatMode { OFF, ALL, ONE }
enum class AppTheme(val displayName: String) { SYSTEM("System Default"), LIGHT("Light"), DARK("Dark") }

private const val AUTOPLAY_TARGET_SIZE = 12
private const val AUTOPLAY_FAST_READY_SIZE = 8
private const val AUTOPLAY_REMOTE_QUERY_LIMIT = 4
private const val AUTOPLAY_REMOTE_TIMEOUT_MS = 4_000L
private const val AUTOPLAY_CACHE_LIMIT = 18
private const val AUTOPLAY_PREFETCH_LIMIT = 4
private const val AUTOPLAY_PREPARED_UP_NEXT_SIZE = 4
private const val AUTOPLAY_PREFETCH_GRACE_ATTEMPTS = 8
private const val AUTOPLAY_PREFETCH_GRACE_DELAY_MS = 50L
private const val UPCOMING_QUEUE_PREFETCH_SIZE = 2
private const val ARTWORK_LOOKUP_RETRY_DELAY_MS = 5 * 60 * 1000L
private const val INITIAL_PROFILE_WARMUP_LIMIT = 24
private const val RECOMMENDATION_PROFILE_WARMUP_DELAY_MS = 1_000L
private const val SEARCH_RESULTS_CACHE_TTL_MS = 10 * 60 * 1000L
private const val SEARCH_RESULTS_CACHE_LIMIT = 20
private const val SEARCH_STREAM_PREFETCH_LIMIT = 2
private const val REMOTE_STREAM_CACHE_FALLBACK_TTL_MS = 10 * 60 * 1000L
private const val REMOTE_STREAM_CACHE_EXPIRY_GRACE_MS = 2 * 60 * 1000L
private const val METADATA_NETWORK_TIMEOUT_MS = 4_000
private const val PLAYBACK_UI_PROGRESS_INTERVAL_MS = 500L
private const val AUTOPLAY_LOG_TAG = "FridaAutoplay"
private const val PLAYBACK_LOG_TAG = "FridaPlayback"
private val YOUTUBE_VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")

class LibraryViewModels(application: Application) : AndroidViewModel(application) {
    private data class RemotePlaybackStream(
        val url: String,
        val source: String,
        val formatName: String,
        val bitrate: Int
    )

    private data class SearchCacheEntry(
        val results: List<YouTubeResult>,
        val cachedAtMs: Long
    )

    private val repository = AudioRepository(application)
    val settingsManager = SettingsManager(application)
    private val playlistDao = MusicDatabase.getDatabase(application).playlistDao()
    val filterVoiceNotes = MutableStateFlow(settingsManager.filterVoiceNotes)
    val keepScreenOn = MutableStateFlow(settingsManager.keepScreenOn)
    val gaplessPlayback = MutableStateFlow(settingsManager.gaplessPlayback)
    val crossfadeDuration = MutableStateFlow(settingsManager.crossfadeDuration)
    val saveLastPlayback = MutableStateFlow(settingsManager.saveLastPlayback)

    private val _currentTheme = MutableStateFlow(
        AppTheme.entries.toTypedArray().getOrNull(settingsManager.appThemePreference) ?: AppTheme.SYSTEM
    )
    val currentTheme = _currentTheme.asStateFlow()

    val enableBlurEffect = MutableStateFlow(settingsManager.enableBlurEffect)

    val sleepTimerMinutes = MutableStateFlow(0)
    private var sleepTimerJob: Job? = null
    private val _searchHistory = MutableStateFlow<List<String>>(
        settingsManager.searchHistory.split("||").filter { it.isNotBlank() }
    )
    private val _recentHistory =
        MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    private val _fullHistory =
        MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    private val _mostPlayedHistory =
        MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    private val _queueState = MutableStateFlow(PlaybackQueueState())
    private val _playlistCoverUris = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _followedArtists = MutableStateFlow(settingsManager.followedArtists)
    private val _blacklistedSongIds = MutableStateFlow(settingsManager.blacklistedSongIds)

    val recentHistory = _recentHistory.asStateFlow()
    val fullHistory = _fullHistory.asStateFlow()
    val mostPlayedHistory = _mostPlayedHistory.asStateFlow()
    val searchHistory = _searchHistory.asStateFlow()
    val queueState = _queueState.asStateFlow()
    val manualQueue = _queueState
        .map { state -> state.upNext.map { it.song } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlistCoverUris = _playlistCoverUris.asStateFlow()
    val followedArtists = _followedArtists.asStateFlow()
    val blacklistedSongIds = _blacklistedSongIds.asStateFlow()

    val isAutoPlayEnabled = MutableStateFlow(settingsManager.autoplayEnabled)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredSongs = combine(_songs, _searchQuery.debounce(300L)) { songList, query ->
        withContext(Dispatchers.Default) {
            if (query.isBlank()) {
                songList
            } else {
                songList.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleAutoplay(enabled: Boolean) {
        isAutoPlayEnabled.value = enabled
        settingsManager.autoplayEnabled = enabled
        Log.i(AUTOPLAY_LOG_TAG, "toggle enabled=$enabled current=${_queueState.value.current?.song?.title.orEmpty()}")
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
    private var profileWarmupJob: Job? = null
    private val imageUrlCache = ConcurrentHashMap<String, String>()
    private val imageUrlMisses = ConcurrentHashMap<String, Long>()
    private val imageUrlLookups = ConcurrentHashMap<String, Deferred<String?>>()
    private val audioStreamCache = ConcurrentHashMap<String, String>()
    private val audioStreamCacheExpiresAtMs = ConcurrentHashMap<String, Long>()
    private val audioDurationCache = ConcurrentHashMap<String, Long>()
    private val deezerToYoutubeMap = ConcurrentHashMap<String, String>()
    private val searchResultsCache = ConcurrentHashMap<String, SearchCacheEntry>()
    private val autoplayRemoteSearchCache = ConcurrentHashMap<String, List<YouTubeResult>>()
    private val autoplayRecommendationCache = ConcurrentHashMap<String, List<QueueItem>>()
    private val recommendationProfileCache = ConcurrentHashMap<String, SongRecommendationProfile>()
    private val failedAutoplayItems = mutableSetOf<String>()
    private val failedQueueRetryCounts = ConcurrentHashMap<String, Int>()
    private val queuePrefetchInFlight = ConcurrentHashMap.newKeySet<String>()
    private val unavailableAutoplayVideoIds = ConcurrentHashMap.newKeySet<String>()
    private val rejectedAutoplayVideoIds = ConcurrentHashMap.newKeySet<String>()
    private val readyAutoplayVideoIds = ConcurrentHashMap.newKeySet<String>()
    private val autoplayPrefetchInFlight = ConcurrentHashMap.newKeySet<String>()
    private var autoplayAnchorSong: Song? = null
    private var activeSearchKey: String? = null
    private var currentPlaybackConfirmed = false
    private var historyRecordedForCurrentAttempt = false
    private var preferPrefetchedAutoplayNext = false

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
                        if (isPlaying) {
                            Log.i(PLAYBACK_LOG_TAG, "isPlaying=true title=${_currentSong.value?.title.orEmpty()}")
                            confirmCurrentPlayback()
                            startProgressUpdate()
                        } else {
                            stopProgressUpdate()
                        }
                        _currentSong.value?.let { updateNotification(it, isPlaying) }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = state
                        if (state == Player.STATE_READY) {
                            _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                            Log.i(
                                PLAYBACK_LOG_TAG,
                                "state=READY title=${_currentSong.value?.title.orEmpty()} durationMs=${_duration.value}"
                            )
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
                    override fun onPlayerError(error: PlaybackException) {
                        val failedSong = _currentSong.value
                        if (failedSong != null && recoverFromFailedQueueItem(failedSong, "player:${error.errorCode}")) {
                            return
                        }
                        Log.w(AUTOPLAY_LOG_TAG, "playback failed current=${failedSong?.title.orEmpty()} code=${error.errorCode}", error)
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
                _queueState.value = sanitizeQueueState(_queueState.value)
                persistQueueState()

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

    private fun playSongNow(song: Song, toggleIfSame: Boolean = true) {
        if (toggleIfSame && sameSong(_currentSong.value, song) && exoPlayer != null) {
            togglePlayback()
            return
        }

        _errorMessage.value = null
        saveCurrentPlaybackState()
        _currentSong.value = song
        syncQueueCurrentSong(song)
        val preloadedArtwork = song.artworkUri.toString()
            .takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() && it.startsWith("http") }
        _currentAlbumArt.value = preloadedArtwork

        val cacheFactory = getCacheDataSourceFactory(getApplication())
        val mediaSource = ProgressiveMediaSource.Factory(cacheFactory)
            .createMediaSource(MediaItem.fromUri(song.uri))

        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(song.uri))
            applyPlayerRepeatMode()
            setMediaSource(mediaSource)
            prepare()
            play()
        }

        maybeRefreshAutoplayRecommendations()
        _isPlaying.value = true
        startProgressUpdate()
        updateNotification(song, true)

        viewModelScope.launch(Dispatchers.IO) {
            val url = preloadedArtwork ?: getSongImageUrl(song)
            val lyrics = settingsManager.localLyrics(song.id) ?: fetchLyrics(song.artist, song.title)

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
        _mostPlayedHistory.value = playbackHistoryRepository.getMostPlayed(20)
    }

    private fun confirmCurrentPlayback() {
        val song = _currentSong.value ?: return
        currentPlaybackConfirmed = true
        failedQueueRetryCounts.remove(songIdentityKey(song))
        if (historyRecordedForCurrentAttempt) return
        historyRecordedForCurrentAttempt = true
        Log.i(AUTOPLAY_LOG_TAG, "confirmed playback song=${song.title}")
        viewModelScope.launch {
            playbackHistoryRepository.addToHistory(
                PlaybackHistoryEntity(
                    songId = song.uri.toString(),
                    title = song.title,
                    artist = song.artist,
                    artworkUrl = _currentAlbumArt.value
                        ?: song.artworkUri.toString().takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() }
                )
            )
            loadHistory()
        }
    }

    private fun startQueueSession(
        songs: List<Song>,
        startSong: Song?,
        source: QueueSource,
        sourceName: String? = null,
        shuffle: Boolean = false
    ) {
        val playableSongs = songs.filterNot(::isBlacklisted)
        val selectedSong = startSong?.takeUnless(::isBlacklisted) ?: playableSongs.firstOrNull()
        if (selectedSong == null) {
            _errorMessage.value = getApplication<Application>().getString(R.string.no_songs_available)
            return
        }

        isShuffleMode.value = shuffle
        settingsManager.shuffleEnabled = shuffle
        failedAutoplayItems.clear()
        failedQueueRetryCounts.clear()
        currentPlaybackConfirmed = false
        historyRecordedForCurrentAttempt = false
        preferPrefetchedAutoplayNext = false
        autoplayAnchorSong = selectedSong

        val sessionSongs = if (source == QueueSource.PLAYLIST) {
            playableSongs.ifEmpty { listOf(selectedSong) }
        } else {
            distinctSongs(playableSongs.ifEmpty { listOf(selectedSong) })
        }
        val startIndex = sessionSongs.indexOfFirst { sameSong(it, selectedSong) }.coerceAtLeast(0)
        val ordered = if (shuffle) {
            listOf(sessionSongs[startIndex]) + sessionSongs
                .filterIndexed { index, _ -> index != startIndex }
                .shuffled()
        } else if (source == QueueSource.PLAYLIST) {
            sessionSongs.drop(startIndex)
        } else {
            sessionSongs.drop(startIndex) + sessionSongs.take(startIndex)
        }
        val queueSongs = diversifyQueueSongs(ordered, source)

        val current = queueItem(queueSongs.firstOrNull() ?: selectedSong, source, reason = sourceName)
        _queueState.value = PlaybackQueueState(
            current = current,
            previous = emptyList(),
            upNext = queueSongs.drop(1).map { queueItem(it, source, reason = sourceName) },
            source = source,
            sourceName = sourceName
        )
        Log.i(
            AUTOPLAY_LOG_TAG,
            "queue source=$source name=${sourceName.orEmpty()} shuffle=$shuffle current=${current.song.title} next=${_queueState.value.upNext.take(5).joinToString(" | ") { it.song.title }}"
        )
        persistQueueState()
        prefetchUpcomingQueueItems(_queueState.value.upNext)
        playQueueItem(current)
    }

    private fun diversifyQueueSongs(songs: List<Song>, source: QueueSource): List<Song> {
        if (!shouldDiversifyQueueSource(source)) return songs
        return AutoplayDiversity.diversifySequence(songs)
    }

    private fun sanitizeQueueState(state: PlaybackQueueState): PlaybackQueueState {
        if (!shouldDiversifyQueueSource(state.source) || state.upNext.size < 2) return state
        val current = state.current ?: return state
        val orderedItems = listOf(current) + state.upNext
        val itemByKey = orderedItems.associateBy { songIdentityKey(it.song) }
        val diversifiedUpNext = AutoplayDiversity.diversifySequence(orderedItems.map { it.song })
            .drop(1)
            .mapNotNull { itemByKey[songIdentityKey(it)] }

        return state.copy(upNext = diversifiedUpNext)
    }

    private fun shouldDiversifyQueueSource(source: QueueSource): Boolean =
        source != QueueSource.PLAYLIST

    private fun playQueueItem(item: QueueItem) {
        currentPlaybackConfirmed = false
        historyRecordedForCurrentAttempt = false
        val waitForPrefetch = preferPrefetchedAutoplayNext &&
                item.source == QueueSource.AUTOPLAY &&
                item.song.data in autoplayPrefetchInFlight
        preferPrefetchedAutoplayNext = false

        if (waitForPrefetch) {
            viewModelScope.launch {
                var remainingAttempts = AUTOPLAY_PREFETCH_GRACE_ATTEMPTS
                while (item.song.data in autoplayPrefetchInFlight && remainingAttempts > 0) {
                    delay(AUTOPLAY_PREFETCH_GRACE_DELAY_MS)
                    remainingAttempts--
                }
                val current = _queueState.value.current
                if (current != null &&
                    current.source == item.source &&
                    songIdentityKey(current.song) == songIdentityKey(item.song)
                ) {
                    startQueueItemPlayback(item)
                }
            }
            return
        }
        startQueueItemPlayback(item)
    }

    private fun startQueueItemPlayback(item: QueueItem) {
        if (item.source == QueueSource.AUTOPLAY && !isAllowedAutoplayCandidate(item.song)) {
            recoverFromFailedAutoplay(item.song, "policy:context")
            return
        }
        if (item.source == QueueSource.AUTOPLAY && item.song.data in rejectedAutoplayVideoIds) {
            recoverFromFailedAutoplay(item.song, "policy:duration")
            return
        }
        if (item.source == QueueSource.AUTOPLAY && item.song.data in unavailableAutoplayVideoIds) {
            recoverFromFailedAutoplay(item.song, "prefetch:no_stream")
            return
        }
        if (item.song.requiresRemoteExtraction()) {
            playYouTubeSongNow(
                YouTubeResult(
                    videoId = item.song.data,
                    title = item.song.title,
                    artist = item.song.artist,
                    thumbnailUrl = item.song.artworkUri.toString(),
                    type = ResultType.SONG,
                    durationMs = item.song.duration
                ),
                trustVideoId = item.source == QueueSource.AUTOPLAY
            )
        } else {
            playSongNow(item.song, toggleIfSame = false)
        }
    }

    private fun recoverFromFailedAutoplay(failedSong: Song, reason: String): Boolean {
        val current = _queueState.value.current ?: return false
        if (current.source != QueueSource.AUTOPLAY ||
            songIdentityKey(current.song) != songIdentityKey(failedSong)
        ) {
            return false
        }

        val failedKey = songIdentityKey(current.song)
        if (!failedAutoplayItems.add(failedKey)) return false

        Log.w(AUTOPLAY_LOG_TAG, "skip failed autoplay song=${current.song.title} reason=$reason")
        exoPlayer?.stop()
        _isPlaying.value = false
        preferPrefetchedAutoplayNext = true
        skipToNext()
        return true
    }

    private fun recoverFromFailedQueueItem(failedSong: Song, reason: String): Boolean {
        if (recoverFromFailedAutoplay(failedSong, reason)) return true

        val state = _queueState.value
        val failedItem = state.current ?: return false
        if (songIdentityKey(failedItem.song) != songIdentityKey(failedSong)) return false

        val next = state.upNext.firstOrNull() ?: return false
        val failedKey = songIdentityKey(failedItem.song)
        val retryCount = failedQueueRetryCounts[failedKey] ?: 0
        val shouldRetryAfterNext = !currentPlaybackConfirmed &&
                failedItem.song.requiresRemoteExtraction() &&
                retryCount == 0
        if (shouldRetryAfterNext) {
            failedQueueRetryCounts[failedKey] = retryCount + 1
        }

        val remaining = state.upNext.drop(1).filterNot { songIdentityKey(it.song) == failedKey }
        _queueState.value = state.copy(
            current = next,
            previous = appendPrevious(state.previous, failedItem),
            upNext = buildList {
                if (shouldRetryAfterNext) add(failedItem)
                addAll(remaining)
            },
            autoplayError = null
        )
        persistQueueState()
        Log.w(
            AUTOPLAY_LOG_TAG,
            "skip unavailable queue song=${failedItem.song.title} reason=$reason retryAfterNext=$shouldRetryAfterNext next=${next.song.title}"
        )
        exoPlayer?.stop()
        _isPlaying.value = false
        if (shouldRetryAfterNext) {
            prefetchQueuedRemoteSong(failedItem.song)
        }
        prefetchUpcomingQueueItems(_queueState.value.upNext)
        playQueueItem(next)
        return true
    }

    private fun handleRemotePlaybackFailure(result: YouTubeResult, message: String) {
        val current = _queueState.value.current
        if (current != null &&
            current.song.data == result.videoId &&
            recoverFromFailedQueueItem(current.song, message)
        ) {
            return
        }
        _errorMessage.value = message
    }

    private fun queueItem(
        song: Song,
        source: QueueSource,
        reason: String? = null,
        userInserted: Boolean = false
    ): QueueItem = QueueItem(song, source, reason, userInserted)

    private fun appendPrevious(previous: List<QueueItem>, current: QueueItem?): List<QueueItem> {
        if (!currentPlaybackConfirmed || current == null) return previous
        return (previous + current).takeLast(50)
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
            duration = result.durationMs,
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

    private fun selectPlayableRemoteStream(streamInfo: StreamInfo): RemotePlaybackStream? {
        val audioStream = streamInfo.audioStreams
            .filter { it.format?.name?.contains("OPUS", ignoreCase = true) == true }
            .maxByOrNull { it.bitrate }
            ?: streamInfo.audioStreams.maxByOrNull { it.bitrate }

        audioStream?.url
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                return RemotePlaybackStream(
                    url = url,
                    source = "audio",
                    formatName = audioStream.format?.name.orEmpty(),
                    bitrate = audioStream.bitrate
                )
            }

        val muxedStream = streamInfo.videoStreams
            .asSequence()
            .filterNot { it.isVideoOnly }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.url?.isNotBlank() == true }
            .minWithOrNull(
                compareBy(
                    { if (it.format == MediaFormat.MPEG_4) 0 else 1 },
                    { remoteVideoHeight(it.resolution) },
                    { it.bitrate.coerceAtLeast(0) }
                )
            )

        return muxedStream?.url
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                RemotePlaybackStream(
                    url = url,
                    source = "muxed",
                    formatName = muxedStream.format?.name.orEmpty(),
                    bitrate = muxedStream.bitrate
                )
            }
    }

    private fun remoteVideoHeight(resolution: String?): Int =
        Regex("(\\d+)p", RegexOption.IGNORE_CASE)
            .find(resolution.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Int.MAX_VALUE

    private fun cacheRemoteStream(videoId: String, streamUrl: String, durationMs: Long) {
        audioStreamCache[videoId] = streamUrl
        audioStreamCacheExpiresAtMs[videoId] = remoteStreamExpiresAtMs(streamUrl)
        audioDurationCache[videoId] = durationMs
    }

    private fun cachedRemoteStreamUrl(videoId: String): String? {
        val streamUrl = audioStreamCache[videoId] ?: return null
        val expiresAtMs = audioStreamCacheExpiresAtMs[videoId] ?: remoteStreamExpiresAtMs(streamUrl).also {
            audioStreamCacheExpiresAtMs[videoId] = it
        }
        if (System.currentTimeMillis() + REMOTE_STREAM_CACHE_EXPIRY_GRACE_MS >= expiresAtMs) {
            audioStreamCache.remove(videoId)
            audioStreamCacheExpiresAtMs.remove(videoId)
            audioDurationCache.remove(videoId)
            readyAutoplayVideoIds.remove(videoId)
            return null
        }
        return streamUrl
    }

    private fun hasCachedRemoteStream(videoId: String): Boolean =
        cachedRemoteStreamUrl(videoId) != null

    private fun remoteStreamExpiresAtMs(streamUrl: String): Long {
        val urlExpirySeconds = runCatching {
            Uri.parse(streamUrl).getQueryParameter("expire")?.toLongOrNull()
        }.getOrNull()

        return urlExpirySeconds
            ?.times(1000L)
            ?: (System.currentTimeMillis() + REMOTE_STREAM_CACHE_FALLBACK_TTL_MS)
    }

    private fun maybeRefreshAutoplayRecommendations() {
        if (!isAutoPlayEnabled.value) return
        val state = _queueState.value
        if (state.autoplay.size >= AUTOPLAY_FAST_READY_SIZE || state.isAutoplayLoading) return
        refreshAutoplayRecommendations(force = false)
    }

    private fun refreshAutoplayRecommendations(
        force: Boolean = false,
        playFirstWhenReady: Boolean = false
    ) {
        if (!isAutoPlayEnabled.value) return
        val seed = _queueState.value.current?.song ?: _currentSong.value ?: return
        val initialState = _queueState.value
        if (!force && initialState.autoplay.size >= AUTOPLAY_FAST_READY_SIZE) return
        if (initialState.current?.source != QueueSource.AUTOPLAY || autoplayAnchorSong == null) {
            autoplayAnchorSong = seed
        }
        val anchorSeed = autoplayAnchorSong ?: seed
        val cacheKey = autoplayCacheKey(anchorSeed)
        val cachedRecommendations = autoplayRecommendationCache[cacheKey]
            ?.let { filterAutoplayForState(it, seed, initialState, anchorSeed) }
            .orEmpty()
        Log.i(
            AUTOPLAY_LOG_TAG,
            "refresh force=$force playFirst=$playFirstWhenReady seed=${seed.title} anchor=${anchorSeed.title} upNext=${initialState.upNext.size} autoplay=${initialState.autoplay.size} cached=${cachedRecommendations.size}"
        )

        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (cachedRecommendations.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (!isCurrentAutoplaySeed(seed)) return@withContext
                        val nextAutoplay = mergeAutoplayRecommendations(
                            existing = if (force) emptyList() else _queueState.value.autoplay,
                            incoming = cachedRecommendations,
                            seed = seed,
                            anchorSeed = anchorSeed,
                            state = _queueState.value
                        )
                        _queueState.value = _queueState.value.copy(
                            autoplay = nextAutoplay,
                            isAutoplayLoading = nextAutoplay.size < AUTOPLAY_FAST_READY_SIZE,
                            autoplayError = null
                        )
                        persistQueueState()
                        prefetchAutoplayCandidates(nextAutoplay, skipFirst = playFirstWhenReady)
                        if (playFirstWhenReady) {
                            skipToNext()
                        }
                    }
                    if (_queueState.value.autoplay.size >= AUTOPLAY_FAST_READY_SIZE || playFirstWhenReady) return@launch
                } else {
                    withContext(Dispatchers.Main) {
                        if (!isCurrentAutoplaySeed(seed)) return@withContext
                        _queueState.value = _queueState.value.copy(isAutoplayLoading = true, autoplayError = null)
                    }
                }

                val instantRecommendations = generateAutoplayRecommendations(
                    seed = seed,
                    anchorSeed = anchorSeed,
                    state = initialState,
                    allowRemote = false
                )

                if (instantRecommendations.isNotEmpty() && cachedRecommendations.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (!isCurrentAutoplaySeed(seed)) return@withContext
                        val nextAutoplay = mergeAutoplayRecommendations(
                            existing = if (force) emptyList() else _queueState.value.autoplay,
                            incoming = instantRecommendations,
                            seed = seed,
                            anchorSeed = anchorSeed,
                            state = _queueState.value
                        )
                        _queueState.value = _queueState.value.copy(
                            autoplay = nextAutoplay,
                            isAutoplayLoading = nextAutoplay.size < AUTOPLAY_FAST_READY_SIZE,
                            autoplayError = null
                        )
                        persistQueueState()
                        prefetchAutoplayCandidates(nextAutoplay, skipFirst = playFirstWhenReady)
                        if (playFirstWhenReady) {
                            skipToNext()
                        }
                    }
                    if (_queueState.value.autoplay.size >= AUTOPLAY_FAST_READY_SIZE || playFirstWhenReady) {
                        autoplayRecommendationCache[cacheKey] = instantRecommendations
                        trimAutoplayRecommendationCache()
                        return@launch
                    }
                }

                val recommendations = generateAutoplayRecommendations(
                    seed = seed,
                    anchorSeed = anchorSeed,
                    state = initialState,
                    allowRemote = true
                ).ifEmpty { cachedRecommendations.ifEmpty { instantRecommendations } }

                withContext(Dispatchers.Main) {
                    if (!isCurrentAutoplaySeed(seed)) return@withContext
                    val nextAutoplay = mergeAutoplayRecommendations(
                        existing = if (force) emptyList() else _queueState.value.autoplay,
                        incoming = recommendations,
                        seed = seed,
                        anchorSeed = anchorSeed,
                        state = _queueState.value
                    )
                    _queueState.value = _queueState.value.copy(
                        autoplay = nextAutoplay,
                        isAutoplayLoading = false,
                        autoplayError = if (nextAutoplay.isEmpty()) {
                            getApplication<Application>().getString(R.string.queue_autoplay_empty)
                        } else {
                            null
                        }
                    )
                    persistQueueState()
                    prefetchAutoplayCandidates(nextAutoplay, skipFirst = playFirstWhenReady)

                    if (playFirstWhenReady && nextAutoplay.isNotEmpty()) {
                        skipToNext()
                    }
                }
                if (recommendations.isNotEmpty()) {
                    autoplayRecommendationCache[cacheKey] = recommendations
                    trimAutoplayRecommendationCache()
                }
                Log.i(AUTOPLAY_LOG_TAG, "refresh complete seed=${seed.title} recommendations=${recommendations.size}")
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.w(AUTOPLAY_LOG_TAG, "refresh failed seed=${seed.title}", e)
                    withContext(Dispatchers.Main) {
                        if (!isCurrentAutoplaySeed(seed)) return@withContext
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
        val profile: SongRecommendationProfile,
        val score: Int,
        val reason: String
    )

    private data class RecommendationContext(
        val tokens: Set<String>,
        val styles: Set<String>,
        val artists: Set<String>
    )

    private data class SongRecommendationProfile(
        val identityKey: String,
        val metadataKey: String,
        val titleBase: String,
        val variants: Set<String>,
        val styles: Set<String>,
        val tokens: Set<String>,
        val artist: String,
        val primaryArtist: String,
        val album: String
    )

    private fun isCurrentAutoplaySeed(seed: Song): Boolean {
        val currentSeed = _queueState.value.current?.song ?: _currentSong.value
        return sameSong(currentSeed, seed)
    }

    private fun autoplayCacheKey(seed: Song): String =
        songProfile(seed).let { profile ->
            listOf(
                profile.metadataKey,
                profile.titleBase,
                profile.styles.sorted().joinToString(","),
                profile.variants.sorted().joinToString(",")
            ).joinToString("|")
        }

    private fun filterAutoplayForState(
        cached: List<QueueItem>,
        seed: Song,
        state: PlaybackQueueState,
        anchorSeed: Song
    ): List<QueueItem> {
        val excluded = (state.previous.takeLast(20) + listOfNotNull(state.current) + state.upNext.take(AUTOPLAY_FAST_READY_SIZE) + state.autoplay)
            .map { songIdentityKey(it.song) }
            .toSet()
        val candidates = cached
            .filter { item -> isAllowedAutoplayCandidate(item.song, anchorSeed) }
            .filterNot { item -> isKnownBlockedAutoplayCandidate(item.song) }
            .filterNot { item -> songIdentityKey(item.song) in excluded || sameSong(seed, item.song) }
            .mapIndexed { index, item ->
                RecommendationCandidate(
                    song = item.song,
                    profile = songProfile(item.song),
                    score = AUTOPLAY_TARGET_SIZE - index,
                    reason = item.reason ?: getApplication<Application>().getString(R.string.autoplay)
                )
            }

        return selectDiverseRecommendations(candidates, anchorSeed, autoplayReservedSongs(state))
            .map { queueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason) }
    }

    private fun mergeAutoplayRecommendations(
        existing: List<QueueItem>,
        incoming: List<QueueItem>,
        seed: Song,
        anchorSeed: Song,
        state: PlaybackQueueState
    ): List<QueueItem> {
        val seen = mutableSetOf<String>()
        val merged = buildList {
            (existing + incoming).forEach { item ->
                if (seen.add(songIdentityKey(item.song)) &&
                    !sameSong(seed, item.song) &&
                    isAllowedAutoplayCandidate(item.song, anchorSeed) &&
                    !isKnownBlockedAutoplayCandidate(item.song)
                ) {
                    add(item)
                }
            }
        }
        val candidates = merged.mapIndexed { index, item ->
            RecommendationCandidate(
                song = item.song,
                profile = songProfile(item.song),
                score = AUTOPLAY_TARGET_SIZE * 2 - index,
                reason = item.reason ?: getApplication<Application>().getString(R.string.autoplay)
            )
        }

        return selectDiverseRecommendations(candidates, anchorSeed, autoplayReservedSongs(state))
            .map { queueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason) }
    }

    private fun trimAutoplayRecommendationCache() {
        if (autoplayRecommendationCache.size <= AUTOPLAY_CACHE_LIMIT) return
        autoplayRecommendationCache.keys.take(autoplayRecommendationCache.size - AUTOPLAY_CACHE_LIMIT).forEach {
            autoplayRecommendationCache.remove(it)
        }
    }

    private suspend fun generateAutoplayRecommendations(
        seed: Song,
        anchorSeed: Song,
        state: PlaybackQueueState,
        allowRemote: Boolean
    ): List<QueueItem> = withContext(Dispatchers.IO) {
        val generateStartedAt = System.currentTimeMillis()
        Log.i(AUTOPLAY_LOG_TAG, "generate start allowRemote=$allowRemote seed=${seed.title} anchor=${anchorSeed.title} source=${state.source} upNext=${state.upNext.size}")
        val seedProfile = songProfile(anchorSeed)
        val currentProfile = songProfile(seed)
        val immediateUpNext = state.upNext.take(AUTOPLAY_FAST_READY_SIZE)
        val excludedContext = state.previous.takeLast(20) + listOfNotNull(state.current) + immediateUpNext + state.autoplay
        val activeBaseContext = state.previous.takeLast(8) + immediateUpNext + state.autoplay
        val excluded = excludedContext
            .map { songIdentityKey(it.song) }
            .toMutableSet()
        val localSongs = _songs.value
        val context = recommendationContext(state, anchorSeed)
        val reservedAutoplaySongs = autoplayReservedSongs(state)
        val autoplayHistory = _fullHistory.value.take(300)
        val historyCounts = autoplayHistory.groupingBy { metadataIdentity(it.title, it.artist) }.eachCount()
        val recentMetadataKeys = autoplayHistory.take(12).map { metadataIdentity(it.title, it.artist) }.toSet()
        val recentBaseCounts = autoplayHistory
            .take(24)
            .map { titleBaseKey(it.title) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
        val activeBaseCounts = activeBaseContext
            .map { songProfile(it.song).titleBase }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
        val candidates = LinkedHashMap<String, RecommendationCandidate>()

        fun isSeedProfile(profile: SongRecommendationProfile): Boolean =
            profile.identityKey == seedProfile.identityKey ||
                    profile.metadataKey == seedProfile.metadataKey ||
                    profile.identityKey == currentProfile.identityKey ||
                    profile.metadataKey == currentProfile.metadataKey

        fun isContextMatch(profile: SongRecommendationProfile): Boolean {
            if (profile.styles.intersect(seedProfile.styles).isNotEmpty()) return true
            if (profile.styles.intersect(context.styles).isNotEmpty()) return true
            if (profile.primaryArtist.isNotBlank() && profile.primaryArtist == seedProfile.primaryArtist) return true
            if (profile.primaryArtist.isNotBlank() && profile.primaryArtist in context.artists) return true
            if (profile.tokens.intersect(seedProfile.tokens).isNotEmpty()) return true
            if (profile.tokens.intersect(context.tokens).size >= 2) return true
            if (profile.metadataKey in historyCounts) return true
            return false
        }

        fun addCandidate(song: Song, baseScore: Int, reason: String? = null, requireContextMatch: Boolean = false) {
            val profile = songProfile(song)
            val key = profile.identityKey
            if (key in excluded || isSeedProfile(profile)) return
            if (!isAllowedAutoplayCandidate(song, anchorSeed)) {
                Log.i(AUTOPLAY_LOG_TAG, "reject autoplay song=${song.title} anchor=${anchorSeed.title}")
                return
            }
            if (requireContextMatch && !isContextMatch(profile)) return
            val score = recommendationScore(
                seed = seedProfile,
                candidate = profile,
                historyCounts = historyCounts,
                recentMetadataKeys = recentMetadataKeys,
                recentBaseCounts = recentBaseCounts,
                activeBaseCounts = activeBaseCounts,
                context = context
            ) + baseScore
            if (score < 22) return
            val existing = candidates[key]
            if (existing == null || score > existing.score) {
                candidates[key] = RecommendationCandidate(
                    song = song,
                    profile = profile,
                    score = score,
                    reason = reason ?: recommendationReason(anchorSeed, song)
                )
            }
        }

        if (state.source == QueueSource.SEARCH || state.source == QueueSource.ARTIST) {
            _youtubeSearchResults.value
                .filter { it.type == ResultType.SONG && YOUTUBE_VIDEO_ID_PATTERN.matches(it.videoId) }
                .forEachIndexed { index, result ->
                    val song = resultToSong(result)
                    addCandidate(song, (22 - index).coerceAtLeast(8))
                }
        }

        val fastSelected = selectDiverseRecommendations(candidates.values.toList(), anchorSeed, reservedAutoplaySongs)
        if (fastSelected.size >= AUTOPLAY_FAST_READY_SIZE || (!allowRemote && fastSelected.isNotEmpty())) {
            Log.i(
                AUTOPLAY_LOG_TAG,
                "generate fast allowRemote=$allowRemote candidates=${candidates.size} selected=${fastSelected.size} took=${System.currentTimeMillis() - generateStartedAt}ms seed=${seed.title}"
            )
            return@withContext fastSelected.map {
                queueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason)
            }
        }

        if (allowRemote && fastSelected.size < AUTOPLAY_FAST_READY_SIZE) {
            recommendationQueries(anchorSeed, context)
                .take(AUTOPLAY_REMOTE_QUERY_LIMIT)
                .map { query ->
                    async {
                        query to cachedAutoplayRemoteSearch(query)
                    }
                }
                .awaitAll()
                .forEach { (query, results) ->
                    Log.i(AUTOPLAY_LOG_TAG, "remote query='$query' results=${results.size}")
                    results
                        .filter { it.type == ResultType.SONG }
                        .take(AUTOPLAY_TARGET_SIZE)
                        .forEachIndexed { index, result ->
                            addCandidate(
                                song = resultToSong(result),
                                baseScore = (30 - index).coerceAtLeast(12)
                            )
                        }
                }
        }

        val selected = selectDiverseRecommendations(candidates.values.toList(), anchorSeed, reservedAutoplaySongs)
        if (selected.size >= AUTOPLAY_FAST_READY_SIZE) {
            return@withContext selected.map {
                queueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason)
            }
        }

        if (!allowRemote) {
            return@withContext emptyList()
        }

        val libraryReason = getApplication<Application>().getString(R.string.from_your_library)
        val libraryFallback = localSongs
            .mapNotNull {
                val profile = songProfile(it)
                if (profile.identityKey in excluded ||
                    isSeedProfile(profile) ||
                    !isAllowedAutoplayCandidate(it, anchorSeed)
                ) return@mapNotNull null
                RecommendationCandidate(
                    song = it,
                    profile = profile,
                    score = recommendationScore(
                        seed = seedProfile,
                        candidate = profile,
                        historyCounts = historyCounts,
                        recentMetadataKeys = recentMetadataKeys,
                        recentBaseCounts = recentBaseCounts,
                        activeBaseCounts = activeBaseCounts,
                        context = context
                    ) + 10,
                    reason = libraryReason
                )
            }
            .filter { it.score >= 12 }

        val fallbackSelected = selectDiverseRecommendations(
            selected + libraryFallback,
            anchorSeed,
            reservedAutoplaySongs
        )
        Log.i(
            AUTOPLAY_LOG_TAG,
            "generate fallback allowRemote=$allowRemote remote=${selected.size} local=${libraryFallback.size} selected=${fallbackSelected.size} seed=${seed.title}"
        )
        fallbackSelected
            .take(AUTOPLAY_FAST_READY_SIZE)
            .map { queueItem(it.song, QueueSource.AUTOPLAY, reason = it.reason) }
    }

    private fun recommendationContext(state: PlaybackQueueState, anchorSeed: Song): RecommendationContext {
        val contextSongs = (
                listOf(anchorSeed) +
                        state.previous.filter { it.source != QueueSource.AUTOPLAY }.takeLast(8).map { it.song } +
                        state.upNext.filter { it.source != QueueSource.AUTOPLAY }.take(40).map { it.song }
                )
        val contextProfiles = contextSongs.map(::songProfile)
        val sourceTokens = expandedSearchTokens(normalizeSearchText(state.sourceName.orEmpty()))
        return RecommendationContext(
            tokens = contextProfiles.flatMap { it.tokens }.toSet() + sourceTokens,
            styles = contextProfiles.flatMap { it.styles }.toSet() + AutoplayDiversity.styleTokens(state.sourceName.orEmpty()),
            artists = contextProfiles.map { it.primaryArtist }.filter { it.isNotBlank() }.toSet()
        )
    }

    private fun isAllowedAutoplayCandidate(song: Song, anchorSeed: Song? = autoplayAnchorSong): Boolean {
        if (!AutoplayDiversity.isAutoplayTrackCandidate(song)) return false
        val anchor = anchorSeed ?: return true
        val anchorProfile = songProfile(anchor)
        val candidateProfile = songProfile(song)
        if (!AutoplayDiversity.isCompatibleWithAnchor(anchorProfile.styles, candidateProfile.styles)) return false
        if (!AutoplayDiversity.hasCoreStyle(anchorProfile.styles) ||
            AutoplayDiversity.hasCoreStyle(candidateProfile.styles)
        ) return true

        val samePrimaryArtist = anchorProfile.primaryArtist.isNotBlank() &&
                anchorProfile.primaryArtist == candidateProfile.primaryArtist
        val relatedTitle = anchorProfile.titleBase.isNotBlank() &&
                candidateProfile.titleBase.isNotBlank() &&
                (candidateProfile.titleBase.contains(anchorProfile.titleBase) ||
                        anchorProfile.titleBase.contains(candidateProfile.titleBase))
        val sharedTokens = anchorProfile.tokens.intersect(candidateProfile.tokens).size >= 2
        return samePrimaryArtist || relatedTitle || sharedTokens
    }

    private fun isKnownBlockedAutoplayCandidate(song: Song): Boolean =
        song.requiresRemoteExtraction() &&
                (song.data in unavailableAutoplayVideoIds || song.data in rejectedAutoplayVideoIds)

    private fun songProfile(song: Song): SongRecommendationProfile {
        val key = songIdentityKey(song)
        recommendationProfileCache[key]?.let { return it }
        val profile = SongRecommendationProfile(
            identityKey = key,
            metadataKey = metadataIdentity(song.title, song.artist),
            titleBase = AutoplayDiversity.baseTitleKey(song),
            variants = AutoplayDiversity.variantTokens(song),
            styles = AutoplayDiversity.styleTokens(song),
            tokens = expandedSearchTokens(normalizeSearchText("${song.title} ${song.artist} ${song.album}")),
            artist = normalizeSearchText(song.artist),
            primaryArtist = primaryArtistName(song.artist)?.let(::normalizeSearchText).orEmpty(),
            album = normalizeSearchText(song.album)
        )
        if (recommendationProfileCache.size > 1_200) recommendationProfileCache.clear()
        recommendationProfileCache[key] = profile
        return profile
    }

    private suspend fun cachedAutoplayRemoteSearch(query: String): List<YouTubeResult> = withContext(Dispatchers.IO) {
        val cacheKey = normalizeSearchText(query)
        autoplayRemoteSearchCache[cacheKey]?.let { return@withContext it }

        val remoteSongs = withTimeoutOrNull(AUTOPLAY_REMOTE_TIMEOUT_MS) {
            runCatching { YouTube.search(query) }.getOrDefault(emptyList())
                .filter { it.type == ResultType.SONG }
        }.orEmpty()
        if (remoteSongs.isEmpty()) {
            Log.i(AUTOPLAY_LOG_TAG, "remote timeout/empty query='$query'")
        }
        if (autoplayRemoteSearchCache.size > 24) autoplayRemoteSearchCache.clear()
        autoplayRemoteSearchCache[cacheKey] = remoteSongs
        remoteSongs
    }

    private fun recommendationScore(
        seed: SongRecommendationProfile,
        candidate: SongRecommendationProfile,
        historyCounts: Map<String, Int>,
        recentMetadataKeys: Set<String>,
        recentBaseCounts: Map<String, Int>,
        activeBaseCounts: Map<String, Int>,
        context: RecommendationContext
    ): Int {
        val seedArtist = seed.artist
        val candidateArtist = candidate.artist
        val candidatePrimaryArtist = candidate.primaryArtist
        val seedAlbum = seed.album
        val candidateAlbum = candidate.album
        val seedTokens = seed.tokens
        val candidateTokens = candidate.tokens
        val seedStyles = seed.styles
        val candidateStyles = candidate.styles
        val seedVariants = seed.variants
        val candidateVariants = candidate.variants
        val seedBase = seed.titleBase
        val candidateBase = candidate.titleBase
        val sameBaseTitle = seedBase.isNotBlank() && seedBase == candidateBase
        val sharedStyles = seedStyles.intersect(candidateStyles)
        val sharedContextStyles = candidateStyles.intersect(context.styles)
        val sharedTokens = seedTokens.intersect(candidateTokens)
        val sharedContextTokens = candidateTokens.intersect(context.tokens)
        val sharedVariants = seedVariants.intersect(candidateVariants)

        var score = 0
        score += (sharedStyles.size * 34).coerceAtMost(90)
        score += (sharedContextStyles.size * 18).coerceAtMost(54)
        if (seedStyles.isNotEmpty() && sharedStyles.isEmpty()) score -= 18

        if (seedArtist.isNotBlank() && seedArtist == candidateArtist) {
            score += 42
        } else if (seedArtist.isNotBlank() && (candidateArtist.contains(seedArtist) || seedArtist.contains(candidateArtist))) {
            score += 18
        }
        if (candidatePrimaryArtist.isNotBlank() && candidatePrimaryArtist in context.artists && !sameBaseTitle) score += 18
        if (seedAlbum.isNotBlank() && seedAlbum == candidateAlbum) score += 16
        score += (sharedTokens.size * 5).coerceAtMost(42)
        score += (sharedContextTokens.size * 2).coerceAtMost(18)
        score += kotlin.math.min((historyCounts[candidate.metadataKey] ?: 0) * 3, 15)
        if (candidateArtist.isNotBlank() && candidateArtist != seedArtist && !sameBaseTitle) score += 10

        if (candidate.metadataKey in recentMetadataKeys) score -= 30
        if (candidate.metadataKey == seed.metadataKey) score -= 100
        if (candidateBase.isNotBlank()) {
            score -= ((recentBaseCounts[candidateBase] ?: 0) * 18).coerceAtMost(72)
            score -= ((activeBaseCounts[candidateBase] ?: 0) * 22).coerceAtMost(88)
        }
        if (sameBaseTitle) {
            score -= 90
            if (seedVariants.isNotEmpty() || candidateVariants.isNotEmpty()) score -= 20
        } else if (candidateVariants.isNotEmpty() && seedVariants.isNotEmpty() && sharedVariants.isEmpty()) {
            score -= 8
        }

        return score
    }

    private fun recommendationReason(seed: Song, candidate: Song): String {
        val seedProfile = songProfile(seed)
        val candidateProfile = songProfile(candidate)
        val sharedStyles = seedProfile.styles.intersect(candidateProfile.styles)
        return when {
            seedProfile.artist.isNotBlank() && seedProfile.artist == candidateProfile.artist -> seed.artist
            sharedStyles.isNotEmpty() -> sharedStyles.first()
            candidate.album.isNotBlank() && candidateProfile.album == seedProfile.album -> candidate.album
            else -> getApplication<Application>().getString(R.string.autoplay)
        }
    }

    private fun recommendationQueries(seed: Song, context: RecommendationContext): List<String> {
        val artist = primaryArtistName(seed.artist)
        val styles = (styleTokens(seed) + context.styles).toList()
        val variants = variantTokens(seed)
        val queries = LinkedHashSet<String>()

        queries += "${seed.title} ${seed.artist}".trim()
        when {
            "funk" in styles -> {
                queries += "brazilian funk montagem phonk songs"
                queries += "funk phonk similar songs"
            }
            "phonk" in styles -> {
                queries += "phonk funk similar songs"
                queries += "drift phonk songs"
            }
            "trap" in styles -> queries += "trap phonk similar songs"
            "jazz" in styles -> queries += "jazz soul similar songs"
        }
        if (artist != null) {
            styles.take(2).forEach { style -> queries += "$artist $style songs" }
            queries += "$artist songs"
        }
        styles.take(3).forEach { style ->
            queries += "$style similar songs ${artist.orEmpty()}".trim()
        }
        if (styles.size >= 2) queries += "${styles[0]} ${styles[1]} songs"
        if ("slowed" in variants) queries += "slowed reverb similar songs ${artist.orEmpty()}".trim()
        if ("sped" in variants) queries += "sped up nightcore similar songs ${artist.orEmpty()}".trim()
        if (queries.isEmpty() && artist != null) queries += "$artist related songs"
        if (queries.isEmpty() && seed.album.isNotBlank()) queries += "${seed.album} similar songs"

        return queries.filter { it.isNotBlank() }.take(5)
    }

    private fun primaryArtistName(artist: String): String? {
        return artist
            .split(Regex("\\s*(,|&|\\bx\\b|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE))
            .firstOrNull { normalizeSearchText(it).isNotBlank() && !it.contains("unknown", ignoreCase = true) }
            ?.trim()
    }

    private fun selectDiverseRecommendations(
        candidates: List<RecommendationCandidate>,
        seed: Song,
        reservedSongs: List<Song> = emptyList()
    ): List<RecommendationCandidate> {
        val artistCounts = mutableMapOf<String, Int>()
        val titleBaseCounts = mutableMapOf<String, Int>()
        val seedBaseTitle = songProfile(seed).titleBase
        val selectedFamilies = reservedSongs.toMutableList()
        val sorted = candidates.sortedWith(
            compareByDescending<RecommendationCandidate> { it.score }
                .thenBy { stableSearchRank(seed.title, it.profile.identityKey) }
        )
        val selected = mutableListOf<RecommendationCandidate>()

        sorted.forEach { candidate ->
            if (selectedFamilies.any { song ->
                    AutoplayDiversity.isSameTitleFamily(song.title, candidate.song.title)
                }
            ) return@forEach

            val artist = candidate.profile.primaryArtist.ifBlank { candidate.profile.artist }
            val count = artistCounts[artist] ?: 0
            val lastTwoSameArtist = selected.takeLast(2).all {
                val selectedArtist = it.profile.primaryArtist.ifBlank { it.profile.artist }
                selectedArtist == artist
            } && selected.size >= 2
            if (artist.isNotBlank() && (count >= 3 || lastTwoSameArtist)) return@forEach

            val baseTitle = candidate.profile.titleBase
            val baseCount = titleBaseCounts[baseTitle] ?: 0
            val maxPerBase = if (baseTitle.isNotBlank() && baseTitle == seedBaseTitle) 1 else 2
            val lastSameBase = baseTitle.isNotBlank() && selected.lastOrNull()?.let {
                it.profile.titleBase == baseTitle
            } == true
            if (baseTitle.isNotBlank() && (baseCount >= maxPerBase || lastSameBase)) return@forEach

            selected += candidate
            selectedFamilies += candidate.song
            if (artist.isNotBlank()) artistCounts[artist] = count + 1
            if (baseTitle.isNotBlank()) titleBaseCounts[baseTitle] = baseCount + 1
            if (selected.size >= AUTOPLAY_TARGET_SIZE) return selected
        }

        return selected
    }

    private fun autoplayReservedSongs(state: PlaybackQueueState): List<Song> = buildList {
        state.current?.takeIf { it.source == QueueSource.AUTOPLAY }?.song?.let(::add)
        addAll(state.upNext.filter { it.source == QueueSource.AUTOPLAY }.map { it.song })
    }

    private fun recommendationTokens(song: Song): Set<String> = songProfile(song).tokens

    private fun styleTokens(song: Song): Set<String> = songProfile(song).styles

    private fun titleBaseKey(song: Song): String = songProfile(song).titleBase

    private fun titleBaseKey(title: String): String = AutoplayDiversity.baseTitleKey(title)

    private fun variantTokens(song: Song): Set<String> = songProfile(song).variants

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
        playPlaylistSongs(playlist, songsForPlaylist(playlist), shuffle)
    }

    fun playPlaylistSongs(playlist: Playlist, playlistSongs: List<Song>, shuffle: Boolean = false) {
        startQueueSession(
            songs = playlistSongs,
            startSong = if (shuffle) playlistSongs.randomOrNull() else playlistSongs.firstOrNull(),
            source = QueueSource.PLAYLIST,
            sourceName = playlist.name,
            shuffle = shuffle
        )
    }

    fun playSongs(collection: List<Song>, shuffle: Boolean = false) {
        startQueueSession(
            songs = collection,
            startSong = if (shuffle) collection.randomOrNull() else collection.firstOrNull(),
            source = QueueSource.LIBRARY,
            sourceName = getApplication<Application>().getString(R.string.library),
            shuffle = shuffle
        )
    }

    fun shuffleLibrary() {
        playSongs(_songs.value, shuffle = true)
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
        viewModelScope.launch(Dispatchers.IO) {
            var ytId = deezerToYoutubeMap[result.videoId]
            if (ytId == null) {
                val ytMatch = YouTube.search("${result.title} ${result.artist} audio").firstOrNull { it.type == ResultType.SONG }
                ytId = ytMatch?.videoId
                if (ytId != null) deezerToYoutubeMap[result.videoId] = ytId
            }

            val cachedAudioUrl = ytId?.let(::cachedRemoteStreamUrl)

            withContext(Dispatchers.Main) {
                if (cachedAudioUrl != null) {
                    val virtualSong = Song(
                        id = result.videoId.hashCode().toLong(),
                        title = result.title,
                        artist = result.artist,
                        data = result.videoId,
                        duration = 1000L,
                        albumId = 0L,
                        uri = Uri.parse(cachedAudioUrl),
                        artworkUri = if (result.thumbnailUrl.isNotEmpty()) Uri.parse(result.thumbnailUrl) else Uri.EMPTY
                    )

                    _currentSong.value = virtualSong
                    _currentAlbumArt.value = result.thumbnailUrl
                    _isPlaying.value = true

                    exoPlayer?.setMediaItem(MediaItem.fromUri(virtualSong.uri))
                    exoPlayer?.prepare()
                    exoPlayer?.play()

                    launch {
                        delay(800)
                        _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                    }

                    val related = _youtubeSearchResults.value
                        .filter { it.type == ResultType.SONG }
                        .map(::resultToSong)

                    _queueState.value = PlaybackQueueState(
                        current = QueueItem(virtualSong, QueueSource.SEARCH, reason = result.artist),
                        upNext = related.filterNot { it.data == result.videoId }.map { QueueItem(it, QueueSource.SEARCH) },
                        source = QueueSource.SEARCH,
                        sourceName = result.artist
                    )
                    persistQueueState()
                } else {
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
            }
        }
    }

    fun addSongNext(song: Song) {
        if (isBlacklisted(song)) return
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
        if (isBlacklisted(song)) return
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
        addPlaylistSongsToQueue(playlist, songsForPlaylist(playlist))
    }

    fun addPlaylistSongsToQueue(playlist: Playlist, queuedSongs: List<Song>) {
        addSongsToQueue(queuedSongs, QueueSource.PLAYLIST, playlist.name)
    }

    fun addSongsToQueue(collection: List<Song>) {
        addSongsToQueue(collection, QueueSource.USER, null)
    }

    private fun addSongsToQueue(collection: List<Song>, source: QueueSource, sourceName: String?) {
        val playableSongs = collection.filterNot(::isBlacklisted)
        if (playableSongs.isEmpty()) return
        if (_queueState.value.current == null) {
            startQueueSession(playableSongs, playableSongs.firstOrNull(), source, sourceName)
        } else {
            _queueState.value = _queueState.value.copy(
                upNext = _queueState.value.upNext + playableSongs.map {
                    queueItem(it, source, reason = sourceName, userInserted = true)
                }
            )
            persistQueueState()
        }
    }

    suspend fun resolveShareUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val videoId = song.data.takeIf { YOUTUBE_VIDEO_ID_PATTERN.matches(it) }
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
            previous = appendPrevious(state.previous, state.current),
            upNext = state.upNext.drop(index + 1),
            autoplayError = null
        )
        persistQueueState()
        playQueueItem(item)
        if (
            item.source == QueueSource.AUTOPLAY &&
            _queueState.value.current?.let { current ->
                songIdentityKey(current.song) == songIdentityKey(item.song)
            } == true
        ) {
            maybeRefreshAutoplayRecommendations()
        }
    }

    fun playAutoplayItem(index: Int) {
        val state = _queueState.value
        val item = state.autoplay.getOrNull(index) ?: return
        Log.i(AUTOPLAY_LOG_TAG, "select autoplay song=${item.song.title}")
        _queueState.value = state.copy(
            current = item,
            previous = appendPrevious(state.previous, state.current),
            autoplay = state.autoplay.drop(index + 1),
            autoplayError = null
        )
        persistQueueState()
        playQueueItem(item)
        if (_queueState.value.current?.let { current -> songIdentityKey(current.song) == songIdentityKey(item.song) } == true) {
            maybeRefreshAutoplayRecommendations()
        }
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

    private fun playYouTubeSongNow(result: YouTubeResult, trustVideoId: Boolean = false) {
        extractionJob?.cancel()

        val newExtractionJob = viewModelScope.launch(Dispatchers.IO) {
            _isExtracting.value = true
            _errorMessage.value = null

            try {
                var realYtId = result.videoId.takeIf { trustVideoId && YOUTUBE_VIDEO_ID_PATTERN.matches(it) }
                    ?: deezerToYoutubeMap[result.videoId]

                if (realYtId == null) {
                    val ytMatch = YouTube.search("${result.title} ${result.artist} audio").firstOrNull { it.type == ResultType.SONG }
                    realYtId = ytMatch?.videoId
                        ?: throw Exception(getApplication<Application>().getString(R.string.youtube_version_not_found))
                    deezerToYoutubeMap[result.videoId] = realYtId
                }

                val cachedUrl = cachedRemoteStreamUrl(realYtId)
                if (cachedUrl != null) {
                    Log.i(PLAYBACK_LOG_TAG, "stream cache hit id=$realYtId title=${result.title}")
                    val cachedDurationMs = audioDurationCache[realYtId] ?: result.durationMs
                    if (trustVideoId &&
                        !AutoplayDiversity.isAutoplayTrackCandidate(
                            "${result.title} ${result.artist}",
                            cachedDurationMs
                        )
                    ) {
                        rejectedAutoplayVideoIds.add(result.videoId)
                        withContext(Dispatchers.Main) {
                            handleRemotePlaybackFailure(result, "Blocked autoplay long-form content")
                        }
                        return@launch
                    }
                    val virtualSong = Song(
                        id = result.videoId.hashCode().toLong(),
                        title = result.title,
                        artist = result.artist,
                        data = result.videoId,
                        duration = cachedDurationMs.coerceAtLeast(1000L),
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
                val streamDurationMs = streamInfo.duration * 1000L
                if (trustVideoId &&
                    !AutoplayDiversity.isAutoplayTrackCandidate(
                        "${result.title} ${result.artist}",
                        streamDurationMs
                    )
                ) {
                    rejectedAutoplayVideoIds.add(result.videoId)
                    withContext(Dispatchers.Main) {
                        handleRemotePlaybackFailure(result, "Blocked autoplay long-form content")
                    }
                    return@launch
                }

                val playbackStream = selectPlayableRemoteStream(streamInfo)
                Log.i(
                    PLAYBACK_LOG_TAG,
                    "extract id=$realYtId title=${result.title} audioStreams=${streamInfo.audioStreams.size} " +
                            "videoStreams=${streamInfo.videoStreams.size} selectedSource=${playbackStream?.source.orEmpty()} " +
                            "selectedFormat=${playbackStream?.formatName.orEmpty()} bitrate=${playbackStream?.bitrate ?: 0}"
                )

                if (playbackStream != null) {
                    cacheRemoteStream(realYtId, playbackStream.url, streamDurationMs)

                    val virtualSong = Song(
                        id = result.videoId.hashCode().toLong(),
                        title = result.title,
                        artist = result.artist,
                        data = result.videoId,
                        duration = streamDurationMs,
                        albumId = 0L,
                        uri = Uri.parse(playbackStream.url),
                        artworkUri = if (result.thumbnailUrl.isNotEmpty()) Uri.parse(result.thumbnailUrl) else Uri.EMPTY
                    )
                    rememberPlaylistSong(virtualSong)

                    withContext(Dispatchers.Main) {
                        _currentAlbumArt.value = result.thumbnailUrl
                        playSongNow(virtualSong, toggleIfSame = false)
                        prefetchNextSongInList(result.videoId)
                    }
                } else {
                    if (trustVideoId) {
                        unavailableAutoplayVideoIds.add(result.videoId)
                    }
                    withContext(Dispatchers.Main) {
                        handleRemotePlaybackFailure(
                            result,
                            getApplication<Application>().getString(R.string.no_compatible_audio_streams)
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        handleRemotePlaybackFailure(
                            result,
                            getApplication<Application>().getString(
                                R.string.audio_extraction_error_format,
                                e.localizedMessage ?: e.message.orEmpty()
                            )
                        )
                    }
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
        promoteReadyAutoplayToUpNext()
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
            prefetchUpcomingQueueItems(_queueState.value.upNext)
            playQueueItem(next)
            if (next.source == QueueSource.AUTOPLAY &&
                _queueState.value.current?.let { currentItem ->
                    songIdentityKey(currentItem.song) == songIdentityKey(next.song)
                } == true
            ) {
                maybeRefreshAutoplayRecommendations()
            }
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
            if (_queueState.value.current?.let { currentItem -> songIdentityKey(currentItem.song) == songIdentityKey(next.song) } == true) {
                maybeRefreshAutoplayRecommendations()
            }
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
                .filterNot(::isBlacklisted)
            _songs.value = audioFiles
            warmRecommendationProfiles(audioFiles)
        }
    }

    private fun warmRecommendationProfiles(songs: List<Song>) {
        profileWarmupJob?.cancel()
        profileWarmupJob = viewModelScope.launch(Dispatchers.Default) {
            delay(RECOMMENDATION_PROFILE_WARMUP_DELAY_MS)
            val startedAt = System.currentTimeMillis()
            val warmupSongs = songs.take(INITIAL_PROFILE_WARMUP_LIMIT)
            warmupSongs.forEach { songProfile(it) }
            Log.i(
                AUTOPLAY_LOG_TAG,
                "profile warmup songs=${warmupSongs.size}/${songs.size} cached=${recommendationProfileCache.size} took=${System.currentTimeMillis() - startedAt}ms"
            )
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

    fun toggleBlacklist(song: Song) {
        val key = song.id.toString()
        val updated = _blacklistedSongIds.value.toMutableSet().apply {
            if (!add(key)) remove(key)
        }
        _blacklistedSongIds.value = updated
        settingsManager.blacklistedSongIds = updated
        if (key in updated) {
            _queueState.value = _queueState.value.copy(
                upNext = _queueState.value.upNext.filterNot { it.song.id == song.id },
                autoplay = _queueState.value.autoplay.filterNot { it.song.id == song.id }
            )
            persistQueueState()
        }
        loadSongs()
    }

    fun isSongBlacklisted(song: Song): Boolean = isBlacklisted(song)

    fun saveLocalLyrics(song: Song, lyrics: String) {
        val storedLyrics = lyrics.trim().takeIf { it.isNotBlank() }
        settingsManager.setLocalLyrics(song.id, storedLyrics)
        if (sameSong(_currentSong.value, song)) {
            _currentSong.value = _currentSong.value?.copy(lyrics = storedLyrics)
            _lyricsLines.value = storedLyrics?.let(LyricsParser::parseLrc).orEmpty()
        }
    }

    fun localLyrics(song: Song): String = settingsManager.localLyrics(song.id).orEmpty()

    private fun isBlacklisted(song: Song): Boolean =
        song.id.toString() in _blacklistedSongIds.value

    fun searchYouTube(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            activeSearchKey = null
            searchJob?.cancel()
            _youtubeSearchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        val searchKey = normalizeSearchText(cleanQuery)
        activeSearchKey = searchKey
        searchJob?.cancel()
        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = searchResultsCache[searchKey]
                    ?.takeIf { System.currentTimeMillis() - it.cachedAtMs <= SEARCH_RESULTS_CACHE_TTL_MS }
                    ?.results
                val results = cached ?: DeezerApi.search(cleanQuery).also { loaded ->
                    if (loaded.isNotEmpty()) {
                        if (searchResultsCache.size >= SEARCH_RESULTS_CACHE_LIMIT) searchResultsCache.clear()
                        searchResultsCache[searchKey] = SearchCacheEntry(loaded, System.currentTimeMillis())
                    }
                }
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Main) {
                    if (activeSearchKey == searchKey) {
                        _youtubeSearchResults.value = results
                        _isSearching.value = false
                    }
                }
                prefetchSearchStreams(results)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    withContext(Dispatchers.Main) {
                        if (activeSearchKey == searchKey) {
                            _youtubeSearchResults.value = emptyList()
                            _isSearching.value = false
                        }
                    }
                }
            }
        }
    }

    private suspend fun prefetchSearchStreams(results: List<YouTubeResult>) {
        results.asSequence()
            .filter { it.type == ResultType.SONG }
            .take(SEARCH_STREAM_PREFETCH_LIMIT)
            .forEach { result ->
                currentCoroutineContext().ensureActive()
                try {
                    val ytMatch = YouTube.search("${result.title} ${result.artist} audio")
                        .firstOrNull { it.type == ResultType.SONG }
                        ?: return@forEach
                    deezerToYoutubeMap[result.videoId] = ytMatch.videoId

                    val streamInfo = StreamInfo.getInfo(
                        ServiceList.YouTube,
                        "https://www.youtube.com/watch?v=${ytMatch.videoId}"
                    )
                    selectPlayableRemoteStream(streamInfo)?.let { playbackStream ->
                        val streamUrl = playbackStream.url
                        cacheRemoteStream(ytMatch.videoId, streamUrl, streamInfo.duration * 1000L)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
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

    fun updateTheme(theme: AppTheme) {
        _currentTheme.value = theme
        settingsManager.appThemePreference = theme.ordinal
    }

    fun updateEnableBlurEffect(enabled: Boolean) {
        settingsManager.enableBlurEffect = enabled
        enableBlurEffect.value = enabled
    }

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

                delay(PLAYBACK_UI_PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdate() { progressJob?.cancel() }

    private fun updateNotification(song: Song, isPlaying: Boolean) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
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
    }

    suspend fun getSongImageUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val preloadedArtwork = song.artworkUri.toString()
            .takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() }
        if (preloadedArtwork != null) return@withContext preloadedArtwork

        val cacheKey = "song_${songArtworkMetadataKey(song)}"
        resolveArtworkUrl(cacheKey) {
            fetchAlbumArt(song.title, song.artist, requireTrackMatch = true)
        }
    }

    suspend fun getHistoryImageUrl(history: PlaybackHistoryEntity): String? = withContext(Dispatchers.IO) {
        history.artworkUrl
            ?.takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() }
            ?.let { return@withContext it }

        _songs.value.firstOrNull { song ->
            song.uri.toString() == history.songId ||
                    metadataIdentity(song.title, song.artist) == metadataIdentity(history.title, history.artist)
        }?.let { return@withContext getSongImageUrl(it) }

        resolveArtworkUrl("history_${metadataIdentity(history.title, history.artist)}") {
            fetchAlbumArt(history.title, history.artist, requireTrackMatch = true)
        }
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
        val cacheKey = "artist_${normalizeArtworkText(artistName)}"
        resolveArtworkUrl(cacheKey) {
            fetchAlbumArt(artistName, "", requireTrackMatch = false)
        }
    }

    private suspend fun resolveArtworkUrl(cacheKey: String, lookup: () -> String?): String? {
        imageUrlCache[cacheKey]?.let { return it }
        imageUrlMisses[cacheKey]?.let { failedAt ->
            if (System.currentTimeMillis() - failedAt < ARTWORK_LOOKUP_RETRY_DELAY_MS) return null
        }

        val request = imageUrlLookups.computeIfAbsent(cacheKey) {
            viewModelScope.async(Dispatchers.IO) { lookup() }
        }
        val url = try {
            request.await()
        } finally {
            imageUrlLookups.remove(cacheKey, request)
        }

        if (url.isNullOrBlank()) {
            imageUrlMisses[cacheKey] = System.currentTimeMillis()
        } else {
            imageUrlMisses.remove(cacheKey)
            imageUrlCache[cacheKey] = url
        }
        return url
    }

    private fun fetchAlbumArt(
        title: String,
        artist: String?,
        requireTrackMatch: Boolean
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val cleanQuery = normalizeArtworkText(title)
            val cleanArtist = if (artist.isNullOrBlank() || artist.contains("unknown", ignoreCase = true)) "" else artist.trim()
            val finalQueryText = "$cleanQuery $cleanArtist".trim().replace(Regex("\\s+"), " ")
            if (finalQueryText.isBlank()) return null

            val encodedQuery = URLEncoder.encode(finalQueryText, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=10"
            val activeConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = METADATA_NETWORK_TIMEOUT_MS
                readTimeout = METADATA_NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", "FridaMusic/1.0")
            }
            connection = activeConnection
            if (activeConnection.responseCode !in 200..299) return null
            val response = activeConnection.inputStream.bufferedReader().use { it.readText() }
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
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
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
            readTextWithTimeout(url)?.let { response ->
                val json = JSONObject(response)
                val syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                if (syncedLyrics != null) return@withContext syncedLyrics

                val plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
                if (plainLyrics != null) return@withContext plainLyrics
            }
        } catch (e: Exception) {}

        try {
            val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
            val response = readTextWithTimeout(url) ?: return@withContext null
            return@withContext JSONObject(response).optString("lyrics").takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) { return@withContext null }
    }

    private fun readTextWithTimeout(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = METADATA_NETWORK_TIMEOUT_MS
            readTimeout = METADATA_NETWORK_TIMEOUT_MS
            setRequestProperty("User-Agent", "FridaMusic/1.0")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun prefetchAutoplayCandidates(items: List<QueueItem>, skipFirst: Boolean) {
        val candidates = items.asSequence()
            .drop(if (skipFirst) 1 else 0)
            .filter { it.source == QueueSource.AUTOPLAY && it.song.requiresRemoteExtraction() }
            .toList()
        candidates
            .filter { hasCachedRemoteStream(it.song.data) }
            .forEach { readyAutoplayVideoIds.add(it.song.data) }
        candidates.asSequence()
            .map { it.song.data }
            .filter { YOUTUBE_VIDEO_ID_PATTERN.matches(it) }
            .distinct()
            .filterNot {
                it in unavailableAutoplayVideoIds ||
                        it in rejectedAutoplayVideoIds ||
                        hasCachedRemoteStream(it)
            }
            .take(AUTOPLAY_PREFETCH_LIMIT)
            .forEach { prefetchYouTubeAudio(it, identifyUnavailable = true) }
        promoteReadyAutoplayToUpNext()
    }

    private fun prefetchUpcomingQueueItems(items: List<QueueItem>) {
        items.asSequence()
            .filter { it.source != QueueSource.AUTOPLAY && it.song.requiresRemoteExtraction() }
            .take(UPCOMING_QUEUE_PREFETCH_SIZE)
            .forEach { prefetchQueuedRemoteSong(it.song) }
    }

    private fun prefetchQueuedRemoteSong(song: Song) {
        if (!song.requiresRemoteExtraction()) return
        val key = songIdentityKey(song)
        if (!queuePrefetchInFlight.add(key)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val videoId = song.data.takeIf { YOUTUBE_VIDEO_ID_PATTERN.matches(it) }
                    ?: deezerToYoutubeMap[song.data]
                    ?: YouTube.search("${song.title} ${song.artist} audio")
                        .firstOrNull { it.type == ResultType.SONG }
                        ?.videoId
                        ?.also { deezerToYoutubeMap[song.data] = it }
                if (videoId != null) {
                    prefetchYouTubeAudio(videoId)
                }
            } catch (_: Exception) {
            } finally {
                queuePrefetchInFlight.remove(key)
            }
        }
    }

    private fun prefetchYouTubeAudio(videoId: String, identifyUnavailable: Boolean = false) {
        if (hasCachedRemoteStream(videoId) ||
            videoId in unavailableAutoplayVideoIds ||
            videoId in rejectedAutoplayVideoIds ||
            !autoplayPrefetchInFlight.add(videoId)
        ) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val videoUrl = "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
                val durationMs = streamInfo.duration * 1000L
                if (identifyUnavailable &&
                    !AutoplayDiversity.isAutoplayTrackCandidate("", durationMs)
                ) {
                    rejectedAutoplayVideoIds.add(videoId)
                    withContext(Dispatchers.Main) {
                        discardBlockedAutoplayCandidate(videoId, "duration=${durationMs / 1000L}s")
                    }
                    return@launch
                }
                val playbackStream = selectPlayableRemoteStream(streamInfo)
                val audioUrl = playbackStream?.url

                if (audioUrl != null) {
                    cacheRemoteStream(videoId, audioUrl, durationMs)
                    if (identifyUnavailable) {
                        readyAutoplayVideoIds.add(videoId)
                        withContext(Dispatchers.Main) {
                            promoteReadyAutoplayToUpNext()
                        }
                    }
                } else if (identifyUnavailable) {
                    unavailableAutoplayVideoIds.add(videoId)
                    withContext(Dispatchers.Main) {
                        discardBlockedAutoplayCandidate(videoId, "no_stream")
                    }
                }
            } catch (_: Exception) {
            } finally {
                autoplayPrefetchInFlight.remove(videoId)
            }
        }
    }

    private fun discardBlockedAutoplayCandidate(videoId: String, reason: String) {
        val state = _queueState.value
        val nextAutoplay = state.autoplay.filterNot { it.song.data == videoId }
        val nextUpNext = state.upNext.filterNot {
            it.source == QueueSource.AUTOPLAY && it.song.data == videoId
        }
        if (nextAutoplay.size == state.autoplay.size && nextUpNext.size == state.upNext.size) return
        _queueState.value = state.copy(autoplay = nextAutoplay, upNext = nextUpNext)
        persistQueueState()
        promoteReadyAutoplayToUpNext()
        prefetchAutoplayCandidates(_queueState.value.autoplay, skipFirst = false)
    }

    private fun promoteReadyAutoplayToUpNext() {
        val state = _queueState.value
        if (state.upNext.any { it.source != QueueSource.AUTOPLAY }) return

        val existingIds = state.upNext.map { songIdentityKey(it.song) }.toSet()
        val openSlots = (AUTOPLAY_PREPARED_UP_NEXT_SIZE - state.upNext.size).coerceAtLeast(0)
        if (openSlots == 0) return

        val protectedFamilies = state.upNext.map { it.song }.toMutableList()
        val hasPendingRemote = state.autoplay.any { item ->
            item.song.requiresRemoteExtraction() &&
                    item.song.data !in readyAutoplayVideoIds &&
                    item.song.data !in unavailableAutoplayVideoIds &&
                    item.song.data !in rejectedAutoplayVideoIds
        }
        val promoted = buildList {
            state.autoplay.forEach { item ->
                if (size >= openSlots) return@forEach
                val isRemoteReady = item.song.requiresRemoteExtraction() &&
                        item.song.data in readyAutoplayVideoIds
                val isLocalFallbackReady = !item.song.requiresRemoteExtraction() && !hasPendingRemote
                val isReady = isRemoteReady || isLocalFallbackReady
                val duplicatesFamily = protectedFamilies.any { queuedSong ->
                    AutoplayDiversity.isSameTitleFamily(queuedSong.title, item.song.title)
                }
                if (isReady &&
                    !duplicatesFamily &&
                    songIdentityKey(item.song) !in existingIds
                ) {
                    add(item)
                    protectedFamilies += item.song
                }
            }
        }
        if (promoted.isEmpty()) return

        val promotedIds = promoted.map { songIdentityKey(it.song) }.toSet()
        _queueState.value = state.copy(
            upNext = state.upNext + promoted,
            autoplay = state.autoplay.filterNot { songIdentityKey(it.song) in promotedIds }
        )
        persistQueueState()
    }

    private fun prefetchNextSongInList(currentVideoId: String) {
        val ytList = _youtubeSearchResults.value
        val currentIndex = ytList.indexOfFirst { it.videoId == currentVideoId }

        if (currentIndex != -1 && currentIndex < ytList.size -1) {
            val nextVideoId = ytList[currentIndex + 1].videoId
            prefetchYouTubeAudio(nextVideoId)
        }
    }

    fun clearCaches() {
        audioStreamCache.clear()
        audioStreamCacheExpiresAtMs.clear()
        audioDurationCache.clear()
        _youtubeSearchResults.value = emptyList()
        YouTube.clearCache()
    }

    override fun onCleared() {
        saveCurrentPlaybackState()
        super.onCleared()
        
        // Liberar recursos pesados
        exoPlayer?.release()
        exoPlayer = null
        
        // Limpiar cachés de memoria para liberar heap
        audioStreamCache.clear()
        audioStreamCacheExpiresAtMs.clear()
        audioDurationCache.clear()
        _youtubeSearchResults.value = emptyList()
        _lyricsLines.value = emptyList()
        
        // Cancelar timer de sueño si existe
        sleepTimerJob?.cancel()

        try { getApplication<Application>().unregisterReceiver(notificationReceiver) } catch (e: Exception) {}
        getApplication<Application>().startService(Intent(getApplication(), MusicService::class.java).apply { action = "STOP_SERVICE" })
    }
}