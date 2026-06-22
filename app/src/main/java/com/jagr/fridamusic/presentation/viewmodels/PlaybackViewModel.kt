@file:OptIn(UnstableApi::class)
package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.jagr.fridamusic.data.local.MusicDatabase
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.data.remote.innertube.YouTube
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.data.remote.innertube.ResultType
import com.jagr.fridamusic.data.repository.*
import com.jagr.fridamusic.domain.model.*
import android.net.Uri
import com.jagr.fridamusic.presentation.service.MusicService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

import org.json.JSONArray
import org.json.JSONObject

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    application: Application,
    private val settingsManager: SettingsManager,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val youtubeRepository: YouTubeRepository,
    private val autoplayRepository: AutoplayRepository,
    private val audioRepository: AudioRepository
) : AndroidViewModel(application) {
    
    private val _queueState = MutableStateFlow(PlaybackQueueState())
    val queueState = _queueState.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()
    
    private var recommendationJob: Job? = null
    private var autoplayAnchorSong: Song? = null
    private val AUTOPLAY_FAST_READY_SIZE = 8

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState = _sleepTimerState.asStateFlow()

    private var sleepTimerJob: Job? = null

    private val _repeatMode = MutableStateFlow(
        runCatching { RepeatMode.valueOf(settingsManager.repeatModeName) }.getOrDefault(RepeatMode.OFF)
    )
    val repeatMode = _repeatMode.asStateFlow()
    val isShuffleMode = MutableStateFlow(settingsManager.shuffleEnabled)

    private var mediaController: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>
    private var progressJob: Job? = null
    private var playbackInitiationJob: Job? = null

    private var currentPlaybackConfirmed = false
    private val failedQueueRetryCounts = mutableMapOf<String, Int>()

    init {
        val sessionToken = SessionToken(application, ComponentName(application, MusicService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                setupPlayerListener()
                applyPlayerRepeatMode()
            },
            ContextCompat.getMainExecutor(application)
        )
        
        val savedJson = settingsManager.playbackQueueJson
        if (savedJson.isNotBlank()) {
            restoreQueueState(savedJson)?.let { restored ->
                _queueState.value = restored
                _currentSong.value = restored.current?.song
            }
        }
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    confirmCurrentPlayback()
                    startProgressUpdate()
                } else {
                    stopProgressUpdate()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = state
                if (state == Player.STATE_READY) {
                    _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                }
                if (state == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _errorMessage.value = "Playback error: ${error.message}"
                handlePlaybackError()
            }
        })
    }

    private fun handlePlaybackError() {
        val song = _currentSong.value ?: return
        if (song.uri.toString().startsWith("http")) {
            // Probably a remote stream error (expired URL or network)
            skipToNext()
        }
    }

    private fun onPlaybackEnded() {
        if (_repeatMode.value == RepeatMode.ONE) {
            seekTo(0)
            mediaController?.play()
        } else {
            if (_sleepTimerState.value.endOfSong && _sleepTimerState.value.minutes == 0 && sleepTimerJob != null) {
                mediaController?.pause()
                cancelSleepTimer()
                return
            }
            skipToNext()
        }
    }

    private fun confirmCurrentPlayback() {
        val song = _currentSong.value ?: return
        currentPlaybackConfirmed = true
        // Record history
        viewModelScope.launch(Dispatchers.IO) {
            playbackHistoryRepository.addToHistory(
                PlaybackHistoryEntity(
                    songId = song.uri.toString(),
                    title = song.title,
                    artist = song.artist,
                    artworkUrl = song.artworkUri.toString()
                )
            )
        }
        refreshAutoplayRecommendations()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                _currentPosition.value = mediaController?.currentPosition ?: 0L
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun playSong(song: Song, queue: List<Song> = emptyList(), source: QueueSource = QueueSource.LIBRARY, sourceName: String? = null) {
        val oldState = _queueState.value
        _currentSong.value = song
        _errorMessage.value = null
        
        playbackInitiationJob?.cancel()
        playbackInitiationJob = viewModelScope.launch {
            val uriString = song.uri.toString()
            val isYouTubeUrl = uriString.contains("youtube.com/watch") || uriString.contains("youtu.be/")
            
            val finalUri = if (isYouTubeUrl) {
                _isLoading.value = true
                _errorMessage.value = "Extracting audio..."
                val result = YouTubeResult(
                    videoId = song.data,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.artworkUri.toString(),
                    type = ResultType.SONG
                )
                val stream = youtubeRepository.extractAudioStream(result, trustVideoId = true)
                _isLoading.value = false
                _errorMessage.value = null // Clear "Extracting audio..." message
                if (stream != null) {
                    Uri.parse(stream.url)
                } else {
                    _errorMessage.value = "Failed to extract audio"
                    return@launch
                }
            } else {
                song.uri
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.artworkUri)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(finalUri)
                .setMediaMetadata(metadata)
                .build()

            mediaController?.apply {
                setMediaItem(mediaItem)
                prepare()
                play()
            }
        }
        
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val currentItem = QueueItem(song, source, reason = sourceName)
        val previousItems = if (queue.isNotEmpty()) {
            queue.take(startIndex).map { QueueItem(it, source, reason = sourceName) }
        } else {
            oldState.previous
        }
        val upNextItems = if (queue.isNotEmpty()) {
            queue.drop(startIndex + 1).map { QueueItem(it, source, reason = sourceName) }
        } else {
            oldState.upNext
        }
        
        val isNavigational = source == QueueSource.AUTOPLAY || source == QueueSource.USER || source == QueueSource.RESTORED
        val autoplayItems = if (isNavigational) {
            oldState.autoplay
        } else {
            emptyList()
        }

        _queueState.value = PlaybackQueueState(
            current = currentItem,
            previous = previousItems,
            upNext = upNextItems,
            autoplay = autoplayItems,
            source = source,
            sourceName = sourceName
        )
    }

    fun playPlaylist(playlist: Playlist, songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        startQueueSession(songs, songs.first(), QueueSource.PLAYLIST, playlist.name, shuffle)
    }

    fun playSongs(songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        startQueueSession(songs, songs.first(), QueueSource.LIBRARY, "Songs", shuffle)
    }

    fun playHistoryItem(item: PlaybackHistoryEntity, songs: List<Song>) {
        val localSong = songs.firstOrNull { it.id.toString() == item.songId || (it.title == item.title && it.artist == item.artist) }
        if (localSong != null) {
            playSong(localSong, source = QueueSource.HISTORY, sourceName = "History")
        }
    }

    fun addSongNext(song: Song) {
        val state = _queueState.value
        _queueState.value = state.copy(
            upNext = listOf(QueueItem(song, QueueSource.USER)) + state.upNext
        )
        persistQueueState()
    }

    fun addSongToQueue(song: Song) {
        val state = _queueState.value
        _queueState.value = state.copy(
            upNext = state.upNext + QueueItem(song, QueueSource.USER)
        )
        persistQueueState()
    }

    private fun startQueueSession(
        songs: List<Song>,
        startSong: Song?,
        source: QueueSource,
        sourceName: String? = null,
        shuffle: Boolean = false
    ) {
        val sessionSongs = if (shuffle) {
            val otherSongs = songs.filter { it.id != startSong?.id }.shuffled()
            if (startSong != null) listOf(startSong) + otherSongs else otherSongs
        } else {
            songs
        }

        val startIndex = if (startSong != null) sessionSongs.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0) else 0
        val ordered = sessionSongs.drop(startIndex) + sessionSongs.take(startIndex)
        val queueSongs = if (source == QueueSource.SEARCH) com.jagr.fridamusic.domain.recommendation.AutoplayDiversity.diversifySequence(ordered) else ordered

        val currentSong = queueSongs.firstOrNull() ?: return
        val currentItem = QueueItem(currentSong, source, reason = sourceName)
        val upNextItems = queueSongs.drop(1).map { QueueItem(it, source, reason = sourceName) }

        _queueState.value = PlaybackQueueState(
            current = currentItem,
            previous = emptyList(),
            upNext = upNextItems,
            source = source,
            sourceName = sourceName
        )

        playSong(currentSong)
        persistQueueState()
    }

    fun playUpNext(index: Int) {
        val state = _queueState.value
        val item = state.upNext.getOrNull(index) ?: return
        _queueState.value = state.copy(
            current = item,
            previous = state.previous + (state.current?.let { listOf(it) } ?: emptyList()),
            upNext = state.upNext.drop(index + 1)
        )
        playSong(item.song, source = item.source, sourceName = item.reason)
    }

    fun replayPrevious(index: Int) {
        val state = _queueState.value
        val item = state.previous.getOrNull(index) ?: return
        _queueState.value = state.copy(
            current = item,
            previous = state.previous.take(index),
            upNext = state.previous.drop(index + 1) + (state.current?.let { listOf(it) } ?: emptyList()) + state.upNext
        )
        playSong(item.song, source = item.source, sourceName = item.reason)
    }

    fun togglePlayback() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() {
        val state = _queueState.value
        when {
            state.upNext.isNotEmpty() -> {
                val next = state.upNext.first()
                _queueState.value = state.copy(
                    current = next,
                    previous = state.previous + (state.current?.let { listOf(it) } ?: emptyList()),
                    upNext = state.upNext.drop(1)
                )
                playSong(next.song, source = next.source, sourceName = next.reason)
                promoteReadyAutoplayToUpNext()
            }
            settingsManager.autoplayEnabled && state.autoplay.isNotEmpty() -> {
                val next = state.autoplay.first()
                _queueState.value = state.copy(
                    current = next,
                    previous = state.previous + (state.current?.let { listOf(it) } ?: emptyList()),
                    autoplay = state.autoplay.drop(1),
                    source = QueueSource.AUTOPLAY
                )
                playSong(next.song, source = QueueSource.AUTOPLAY, sourceName = next.reason)
            }
            _repeatMode.value == RepeatMode.ALL && (state.previous.isNotEmpty() || state.current != null) -> {
                val fullQueue = state.previous + (state.current?.let { listOf(it) } ?: emptyList()) + state.upNext
                if (fullQueue.isNotEmpty()) {
                    val first = fullQueue.first()
                    _queueState.value = state.copy(
                        current = first,
                        previous = emptyList(),
                        upNext = fullQueue.drop(1)
                    )
                    playSong(first.song, source = first.source, sourceName = first.reason)
                }
            }
            else -> {
                mediaController?.seekToNext()
            }
        }
    }

    fun skipToPrevious() {
        val state = _queueState.value
        if (state.previous.isNotEmpty()) {
            val prev = state.previous.last()
            _queueState.value = state.copy(
                current = prev,
                previous = state.previous.dropLast(1),
                upNext = (state.current?.let { listOf(it) } ?: emptyList()) + state.upNext
            )
            playSong(prev.song, source = prev.source, sourceName = prev.reason)
        } else {
            mediaController?.seekToPrevious()
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun setSleepTimer(minutes: Int, endOfSong: Boolean) {
        _sleepTimerState.value = SleepTimerState(minutes, endOfSong)
        sleepTimerJob?.cancel()

        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                var remainingMillis = minutes * 60 * 1000L
                while (remainingMillis > 0) {
                    delay(1000)
                    remainingMillis -= 1000
                    val currentMins = (remainingMillis / 60000L).toInt() + 1
                    if (currentMins != _sleepTimerState.value.minutes) {
                        _sleepTimerState.value = _sleepTimerState.value.copy(minutes = currentMins)
                    }
                }

                if (!_sleepTimerState.value.endOfSong) {
                    mediaController?.pause()
                    _sleepTimerState.value = SleepTimerState()
                } else {
                    _sleepTimerState.value = _sleepTimerState.value.copy(minutes = 0)
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerState.value = SleepTimerState()
    }

    fun toggleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        settingsManager.repeatModeName = next.name
        applyPlayerRepeatMode()
    }

    fun toggleShuffleMode() {
        val next = !isShuffleMode.value
        isShuffleMode.value = next
        settingsManager.shuffleEnabled = next
        if (next) {
            val state = _queueState.value
            _queueState.value = state.copy(upNext = state.upNext.shuffled())
        }
    }

    private fun applyPlayerRepeatMode() {
        mediaController?.repeatMode = if (_repeatMode.value == RepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    fun clearManualQueue() {
        _queueState.value = _queueState.value.copy(
            upNext = emptyList(),
            autoplay = emptyList(),
            autoplayError = null,
            isAutoplayLoading = false
        )
    }

    fun removeFromQueue(index: Int) {
        val state = _queueState.value
        if (index in state.upNext.indices) {
            _queueState.value = state.copy(
                upNext = state.upNext.filterIndexed { i, _ -> i != index }
            )
        }
    }

    fun moveQueueItem(index: Int, direction: Int) {
        val state = _queueState.value
        val targetIndex = (index + direction).coerceIn(0, state.upNext.lastIndex)
        if (index !in state.upNext.indices || index == targetIndex) return

        val next = state.upNext.toMutableList()
        val item = next.removeAt(index)
        next.add(targetIndex, item)
        _queueState.value = state.copy(upNext = next)
    }

    fun moveQueueItemToNext(index: Int) {
        val state = _queueState.value
        val item = state.upNext.getOrNull(index) ?: return
        _queueState.value = state.copy(
            upNext = listOf(item) + state.upNext.filterIndexed { i, _ -> i != index }
        )
    }

    fun addSongsToQueue(songs: List<Song>) {
        val state = _queueState.value
        _queueState.value = state.copy(
            upNext = state.upNext + songs.map { QueueItem(it, QueueSource.USER) }
        )
    }

    fun addPlaylistToQueue(playlist: Playlist, songs: List<Song>) {
        val state = _queueState.value
        _queueState.value = state.copy(
            upNext = state.upNext + songs.map { QueueItem(it, QueueSource.PLAYLIST, reason = playlist.name) }
        )
    }

    fun playYouTubeSong(result: YouTubeResult) {
        val song = Song(
            id = result.videoId.hashCode().toLong(),
            title = result.title,
            artist = result.artist,
            data = result.videoId,
            duration = 0L,
            albumId = 0L,
            uri = Uri.parse("https://music.youtube.com/watch?v=${result.videoId}"),
            artworkUri = Uri.parse(result.thumbnailUrl)
        )
        playSong(song, source = QueueSource.SEARCH, sourceName = result.artist)
    }

    private fun persistQueueState() {
        val state = _queueState.value
        settingsManager.playbackQueueJson = encodeQueueState(state)
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

    private fun QueueItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("song", song.toJson())
            put("source", source.name)
            put("reason", reason)
            put("userInserted", userInserted)
        }
    }

    private fun Song.toJson(): JSONObject {
        return JSONObject().apply {
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
            put("isExplicit", isExplicit)
        }
    }

    private fun restoreQueueState(json: String): PlaybackQueueState? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            PlaybackQueueState(
                source = runCatching { QueueSource.valueOf(root.getString("source")) }.getOrDefault(QueueSource.RESTORED),
                sourceName = root.optString("sourceName"),
                current = root.optJSONObject("current")?.toQueueItem(),
                previous = root.optJSONArray("previous").toQueueItems(),
                upNext = root.optJSONArray("upNext").toQueueItems(),
                autoplay = root.optJSONArray("autoplay").toQueueItems()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.toQueueItem(): QueueItem? {
        val songObj = optJSONObject("song") ?: return null
        return QueueItem(
            song = songObj.toSong(),
            source = runCatching { QueueSource.valueOf(optString("source")) }.getOrDefault(QueueSource.RESTORED),
            reason = optString("reason").takeIf { it.isNotBlank() },
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
            isExplicit = optBoolean("isExplicit", false)
        )

    private fun JSONArray?.toQueueItems(): List<QueueItem> {
        if (this == null) return emptyList()
        val list = mutableListOf<QueueItem>()
        for (i in 0 until length()) {
            optJSONObject(i)?.toQueueItem()?.let { list.add(it) }
        }
        return list
    }

    private fun refreshAutoplayRecommendations(force: Boolean = false) {
        if (!settingsManager.autoplayEnabled) return
        val seed = _currentSong.value ?: return
        val state = _queueState.value
        
        val threshold = AUTOPLAY_FAST_READY_SIZE / 2
        
        val isNavigational = state.source == QueueSource.AUTOPLAY || state.source == QueueSource.USER || state.source == QueueSource.RESTORED
        if (!force && state.autoplay.size >= threshold && isNavigational) return
        
        if (!isNavigational || autoplayAnchorSong == null) {
            autoplayAnchorSong = seed
        }
        val anchorSeed = autoplayAnchorSong ?: seed
        
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (state.autoplay.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _queueState.value = _queueState.value.copy(isAutoplayLoading = true, autoplayError = null)
                    }
                }
                
                val allSongs = audioRepository.getAudioFiles(settingsManager.filterVoiceNotes)
                val fullHistory = playbackHistoryRepository.getFullHistory()
                
                val recommendations = autoplayRepository.generateRecommendations(
                    seed = seed,
                    anchorSeed = anchorSeed,
                    state = _queueState.value,
                    allSongs = allSongs,
                    fullHistory = fullHistory,
                    allowRemote = true
                )
                
                withContext(Dispatchers.Main) {
                    val currentState = _queueState.value
                    val updatedAutoplay = currentState.autoplay + recommendations
                    
                    _queueState.value = currentState.copy(
                        autoplay = updatedAutoplay.take(20),
                        isAutoplayLoading = false,
                        autoplayError = if (updatedAutoplay.isEmpty()) "No recommendations found" else null
                    )
                    persistQueueState()
                    prefetchUpcomingQueueItems(recommendations)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _queueState.value = _queueState.value.copy(
                        isAutoplayLoading = false,
                        autoplayError = "Failed to load recommendations: ${e.message}"
                    )
                }
            }
        }
    }

    private fun prefetchUpcomingQueueItems(items: List<QueueItem>) {
        if (!settingsManager.gaplessPlayback) return
        viewModelScope.launch(Dispatchers.IO) {
            items.take(3).forEach { item ->
                val song = item.song
                val uriString = song.uri.toString()
                if (uriString.startsWith("http") || uriString.contains("youtube.com") || uriString.contains("youtu.be")) {
                    val videoId = if (uriString.startsWith("http")) song.data else {
                        // Extract video ID from URL if possible
                        uriString.substringAfter("v=").substringBefore("&")
                    }
                    
                    if (youtubeRepository.getCachedStream(videoId) == null) {
                        Log.d("PlaybackVM", "Prefetching stream for: ${song.title}")
                        val result = YouTubeResult(
                            videoId = videoId,
                            title = song.title,
                            artist = song.artist,
                            thumbnailUrl = song.artworkUri.toString(),
                            type = ResultType.SONG
                        )
                        youtubeRepository.prefetchStream(result)
                    }
                }
            }
        }
    }

    private fun promoteReadyAutoplayToUpNext() {
        val state = _queueState.value
        if (state.upNext.size >= 4) return
        val ready = state.autoplay.firstOrNull { youtubeRepository.getCachedStream(it.song.data) != null } ?: return
        
        _queueState.value = state.copy(
            upNext = state.upNext + ready,
            autoplay = state.autoplay.filter { it != ready }
        )
        persistQueueState()
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
    }
}
