@file:OptIn(UnstableApi::class)
package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
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
import java.io.File
import javax.inject.Inject

import org.json.JSONArray
import org.json.JSONObject

data class SleepTimerState(
    val minutes: Int = 0,
    val endOfSong: Boolean = false,
    val expiresAtMs: Long = 0L,
    val waitingForSongEnd: Boolean = false
)

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

    private val _repeatMode = MutableStateFlow(
        runCatching { RepeatMode.valueOf(settingsManager.repeatModeName) }.getOrDefault(RepeatMode.OFF)
    )
    val repeatMode = _repeatMode.asStateFlow()
    val isShuffleMode = MutableStateFlow(settingsManager.shuffleEnabled)

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState = _sleepTimerState.asStateFlow()

    private var mediaController: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>
    private var playerListener: Player.Listener? = null
    private var progressJob: Job? = null
    private var playbackInitiationJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var nativeQueueItems: List<QueueItem> = emptyList()
    private var updatingNativeQueue = false

    private var currentPlaybackConfirmed = false
    private var lastPlaybackSnapshotAtMs = 0L
    private val failedQueueRetryCounts = mutableMapOf<String, Int>()

    init {
        val sessionToken = SessionToken(application, ComponentName(application, MusicService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                setupPlayerListener()
                applyPlayerRepeatMode()
                mediaController?.shuffleModeEnabled = isShuffleMode.value
            },
            ContextCompat.getMainExecutor(application)
        )
        
        val savedJson = settingsManager.playbackQueueJson
        if (settingsManager.saveLastPlayback && savedJson.isNotBlank()) {
            restoreQueueState(savedJson)?.let { restored ->
                _queueState.value = restored
                _currentSong.value = restored.current?.song
            }
        } else if (!settingsManager.saveLastPlayback) {
            settingsManager.clearLastPlayback()
        }
    }

    private fun setupPlayerListener() {
        val listener = object : Player.Listener {
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
                    persistCurrentPlaybackSnapshot()
                }
                if (state == Player.STATE_ENDED) {
                    onPlaybackEnded()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!updatingNativeQueue) {
                    syncStateFromNativeQueue(mediaItem)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _errorMessage.value = "Playback error: ${error.message}"
                handlePlaybackError()
            }
        }
        playerListener = listener
        mediaController?.addListener(listener)
    }

    private fun handlePlaybackError() {
        val song = _currentSong.value ?: return
        if (song.uri.toString().startsWith("http")) {
            // Probably a remote stream error (expired URL or network)
            skipToNext()
        }
    }

    private fun onPlaybackEnded() {
        if (_sleepTimerState.value.waitingForSongEnd) {
            stopAfterSleepTimer()
            return
        }
        if (_repeatMode.value == RepeatMode.ONE) {
            seekTo(0)
            mediaController?.play()
        } else {
            skipToNext()
        }
    }

    private fun confirmCurrentPlayback() {
        val song = _currentSong.value ?: return
        if (currentPlaybackConfirmed) return
        currentPlaybackConfirmed = true
        // Registra el historial desde IO y Room emite el cambio a la UI mediante Flow.
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
        persistCurrentPlaybackSnapshot()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val position = mediaController?.currentPosition ?: 0L
                _currentPosition.value = position
                val now = System.currentTimeMillis()
                if (now - lastPlaybackSnapshotAtMs >= 2_000L) {
                    persistCurrentPlaybackSnapshot(position)
                    lastPlaybackSnapshotAtMs = now
                }
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
        currentPlaybackConfirmed = false

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

        val nextQueueState = PlaybackQueueState(
            current = currentItem,
            previous = previousItems,
            upNext = upNextItems,
            autoplay = autoplayItems,
            source = source,
            sourceName = sourceName,
            shuffleSnapshot = if (isShuffleMode.value) {
                if (queue.isNotEmpty()) previousItems + currentItem + upNextItems else oldState.shuffleSnapshot
            } else {
                emptyList()
            }
        )
        _queueState.value = nextQueueState
        persistQueueState()
        persistCurrentPlaybackSnapshot()

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
                val stream = try {
                    youtubeRepository.extractAudioStream(result, trustVideoId = true)
                } catch (error: Exception) {
                    null
                }
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

            setNativePlayerQueue(
                queueItems = nextQueueState.nativePlaybackQueue().ifEmpty { listOf(currentItem) },
                currentItem = currentItem,
                currentPlaybackUri = finalUri,
                startPositionMs = C.TIME_UNSET,
                playWhenReady = true
            )
        }
    }

    fun playPlaylist(playlist: Playlist, songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        startQueueSession(songs, songs.firstOrRandom(shuffle), QueueSource.PLAYLIST, playlist.name, shuffle)
    }

    fun playSongs(songs: List<Song>, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        startQueueSession(songs, songs.firstOrRandom(shuffle), QueueSource.LIBRARY, "Songs", shuffle)
    }

    fun playHomeShuffle(songs: List<Song>) {
        val playableSongs = songs
            .filter { it.isPlayableCandidate() }
            .distinctBy { it.stablePlaybackKey() }
        if (playableSongs.isEmpty()) {
            _errorMessage.value = "No songs available to play"
            return
        }
        startQueueSession(
            songs = playableSongs,
            startSong = playableSongs.shuffled().firstOrNull(),
            source = QueueSource.LIBRARY,
            sourceName = "Home Shuffle",
            shuffle = true
        )
    }

    fun playHistoryItem(item: PlaybackHistoryEntity, songs: List<Song>) {
        val localSong = songs.firstOrNull {
            it.uri.toString() == item.songId ||
                    it.id.toString() == item.songId ||
                    (it.title == item.title && it.artist == item.artist)
        }
        if (localSong != null) {
            playSong(localSong, queue = songs, source = QueueSource.HISTORY, sourceName = "History")
        } else {
            val restoredSong = item.toRestoredSong() ?: run {
                _errorMessage.value = "Could not reload this song from history"
                return
            }
            playSong(restoredSong, source = QueueSource.HISTORY, sourceName = "History")
        }
    }

    fun addSongNext(song: Song) {
        val state = _queueState.value
        val item = QueueItem(song, QueueSource.USER, userInserted = true)
        _queueState.value = state.copy(
            upNext = listOf(item) + state.upNext,
            shuffleSnapshot = state.shuffleSnapshot.insertAfterCurrent(state.current, item)
        )
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    fun addSongToQueue(song: Song) {
        val state = _queueState.value
        val item = QueueItem(song, QueueSource.USER, userInserted = true)
        _queueState.value = state.copy(
            upNext = state.upNext + item,
            shuffleSnapshot = state.shuffleSnapshot.appendIfActive(item)
        )
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    private fun startQueueSession(
        songs: List<Song>,
        startSong: Song?,
        source: QueueSource,
        sourceName: String? = null,
        shuffle: Boolean = false
    ) {
        val playableSongs = songs
            .filter { it.isPlayableCandidate() }
            .distinctBy { it.stablePlaybackKey() }
        if (playableSongs.isEmpty()) {
            _errorMessage.value = "No songs available to play"
            return
        }
        val safeStartSong = startSong?.takeIf { candidate ->
            playableSongs.any { it.stablePlaybackKey() == candidate.stablePlaybackKey() }
        } ?: playableSongs.firstOrRandom(shuffle)
        val sessionSongs = if (shuffle) {
            val otherSongs = playableSongs.filter { it.stablePlaybackKey() != safeStartSong?.stablePlaybackKey() }.shuffled()
            if (safeStartSong != null) listOf(safeStartSong) + otherSongs else otherSongs
        } else {
            playableSongs
        }

        if (shuffle) {
            isShuffleMode.value = true
            settingsManager.shuffleEnabled = true
            mediaController?.shuffleModeEnabled = true
        }

        val startIndex = if (safeStartSong != null) {
            sessionSongs.indexOfFirst { it.stablePlaybackKey() == safeStartSong.stablePlaybackKey() }.coerceAtLeast(0)
        } else {
            0
        }
        val ordered = sessionSongs.drop(startIndex) + sessionSongs.take(startIndex)
        val queueSongs = if (source == QueueSource.SEARCH) com.jagr.fridamusic.domain.recommendation.AutoplayDiversity.diversifySequence(ordered) else ordered

        val currentSong = queueSongs.firstOrNull() ?: return
        playSong(currentSong, queue = queueSongs, source = source, sourceName = sourceName)
        if (shuffle) {
            _queueState.value = _queueState.value.copy(
                shuffleSnapshot = playableSongs.map { QueueItem(it, source, reason = sourceName) }
            )
            persistQueueState()
        }
    }

    fun playUpNext(index: Int) {
        val state = _queueState.value
        val item = state.upNext.getOrNull(index) ?: return
        _queueState.value = state.copy(
            current = item,
            previous = state.previous + (state.current?.let { listOf(it) } ?: emptyList()),
            upNext = state.upNext.drop(index + 1)
        )
        persistQueueState()
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
        persistQueueState()
        playSong(item.song, source = item.source, sourceName = item.reason)
    }

    fun togglePlayback() {
        mediaController?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipToNext() {
        mediaController?.takeIf { it.hasNextMediaItem() }?.let { controller ->
            controller.seekToNextMediaItem()
            controller.play()
            return
        }

        val state = _queueState.value
        when {
            state.upNext.isNotEmpty() -> {
                val next = state.upNext.first()
                _queueState.value = state.copy(
                    current = next,
                    previous = state.previous + (state.current?.let { listOf(it) } ?: emptyList()),
                    upNext = state.upNext.drop(1)
                )
                persistQueueState()
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
                persistQueueState()
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
                    persistQueueState()
                    playSong(first.song, source = first.source, sourceName = first.reason)
                }
            }
            else -> {
                mediaController?.seekToNext()
            }
        }
    }

    fun skipToPrevious() {
        mediaController?.takeIf { it.hasPreviousMediaItem() }?.let { controller ->
            controller.seekToPreviousMediaItem()
            controller.play()
            return
        }

        val state = _queueState.value
        if (state.previous.isNotEmpty()) {
            val prev = state.previous.last()
            _queueState.value = state.copy(
                current = prev,
                previous = state.previous.dropLast(1),
                upNext = (state.current?.let { listOf(it) } ?: emptyList()) + state.upNext
            )
            persistQueueState()
            playSong(prev.song, source = prev.source, sourceName = prev.reason)
        } else {
            mediaController?.seekToPrevious()
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
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
        mediaController?.shuffleModeEnabled = false
        _queueState.value = if (next) {
            _queueState.value.shuffleEnabled()
        } else {
            _queueState.value.shuffleRestored()
        }
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    private fun applyPlayerRepeatMode() {
        mediaController?.repeatMode = if (_repeatMode.value == RepeatMode.ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        mediaController?.shuffleModeEnabled = false
    }

    fun clearManualQueue() {
        val nextState = _queueState.value.copy(
            upNext = emptyList(),
            autoplay = emptyList(),
            autoplayError = null,
            isAutoplayLoading = false
        )
        _queueState.value = nextState.copy(shuffleSnapshot = nextState.shuffleSnapshot.syncWithQueue(nextState))
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    fun removeFromQueue(index: Int) {
        val state = _queueState.value
        if (index in state.upNext.indices) {
            val removed = state.upNext[index]
            _queueState.value = state.copy(
                upNext = state.upNext.filterIndexed { i, _ -> i != index },
                shuffleSnapshot = state.shuffleSnapshot.removeIfActive(removed)
            )
            persistQueueState()
            refreshNativeQueueIfNeeded()
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
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    fun moveQueueItemToNext(index: Int) {
        val state = _queueState.value
        val item = state.upNext.getOrNull(index) ?: return
        _queueState.value = state.copy(
            upNext = listOf(item) + state.upNext.filterIndexed { i, _ -> i != index }
        )
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    fun addSongsToQueue(songs: List<Song>) {
        val state = _queueState.value
        val items = songs.map { QueueItem(it, QueueSource.USER, userInserted = true) }
        _queueState.value = state.copy(
            upNext = state.upNext + items,
            shuffleSnapshot = items.fold(state.shuffleSnapshot) { snapshot, item -> snapshot.appendIfActive(item) }
        )
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    fun addPlaylistToQueue(playlist: Playlist, songs: List<Song>) {
        val state = _queueState.value
        val items = songs.map { QueueItem(it, QueueSource.PLAYLIST, reason = playlist.name, userInserted = true) }
        _queueState.value = state.copy(
            upNext = state.upNext + items,
            shuffleSnapshot = items.fold(state.shuffleSnapshot) { snapshot, item -> snapshot.appendIfActive(item) }
        )
        persistQueueState()
        refreshNativeQueueIfNeeded()
    }

    fun playYouTubeSong(result: YouTubeResult) {
        val song = Song(
            id = result.videoId.hashCode().toLong(),
            title = result.title,
            artist = result.artist,
            data = result.videoId,
            duration = result.durationMs,
            albumId = 0L,
            uri = Uri.parse("https://music.youtube.com/watch?v=${result.videoId}"),
            artworkUri = Uri.parse(result.thumbnailUrl)
        )
        playSong(song, source = QueueSource.SEARCH, sourceName = result.artist)
    }

    fun removeCurrentFromHistory() {
        val song = _currentSong.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            playbackHistoryRepository.removeFromHistory(
                songId = song.uri.toString(),
                title = song.title,
                artist = song.artist
            )
        }
    }

    private fun persistQueueState() {
        if (!settingsManager.saveLastPlayback) {
            settingsManager.clearLastPlayback()
            return
        }
        val state = _queueState.value
        settingsManager.playbackQueueJson = encodeQueueState(state)
    }

    private fun persistCurrentPlaybackSnapshot(position: Long = mediaController?.currentPosition ?: _currentPosition.value) {
        val song = _currentSong.value
        if (!settingsManager.saveLastPlayback || song == null) {
            if (!settingsManager.saveLastPlayback) settingsManager.clearLastPlayback()
            return
        }
        settingsManager.lastSongId = song.id
        settingsManager.lastSongTitle = song.title
        settingsManager.lastSongArtist = song.artist
        settingsManager.lastSongUri = song.uri.toString()
        settingsManager.lastSongArtwork = song.artworkUri.toString()
        settingsManager.lastSongDuration = _duration.value.takeIf { it > 0L } ?: song.duration
        settingsManager.lastPosition = position.coerceAtLeast(0L)
    }

    fun setSleepTimer(minutes: Int, endOfSong: Boolean) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerState.value = SleepTimerState()
            return
        }

        val clampedMinutes = minutes.coerceIn(5, 120)
        val expiresAt = System.currentTimeMillis() + clampedMinutes * 60_000L
        _sleepTimerState.value = SleepTimerState(
            minutes = clampedMinutes,
            endOfSong = endOfSong,
            expiresAtMs = expiresAt
        )

        sleepTimerJob = viewModelScope.launch {
            delay(clampedMinutes * 60_000L)
            if (endOfSong && _currentSong.value != null && _isPlaying.value) {
                _sleepTimerState.value = _sleepTimerState.value.copy(waitingForSongEnd = true)
            } else {
                stopAfterSleepTimer()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerState.value = SleepTimerState()
    }

    private fun stopAfterSleepTimer() {
        sleepTimerJob?.cancel()
        mediaController?.pause()
        mediaController?.stop()
        _isPlaying.value = false
        _sleepTimerState.value = SleepTimerState()
        persistCurrentPlaybackSnapshot()
    }

    private fun encodeQueueState(state: PlaybackQueueState): String {
        return JSONObject().apply {
            put("source", state.source.name)
            put("sourceName", state.sourceName)
            put("current", state.current?.toJson())
            put("previous", JSONArray().apply { state.previous.forEach { put(it.toJson()) } })
            put("upNext", JSONArray().apply { state.upNext.forEach { put(it.toJson()) } })
            put("autoplay", JSONArray().apply { state.autoplay.forEach { put(it.toJson()) } })
            put("shuffleSnapshot", JSONArray().apply { state.shuffleSnapshot.forEach { put(it.toJson()) } })
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
                autoplay = root.optJSONArray("autoplay").toQueueItems(),
                shuffleSnapshot = root.optJSONArray("shuffleSnapshot").toQueueItems()
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
                    refreshNativeQueueIfNeeded()
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
        val prefetchLimit = if (settingsManager.gaplessPlayback) 2 else 1
        items.take(prefetchLimit).forEach { item ->
            if (item.song.uri.toString().startsWith("http")) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        youtubeRepository.getCachedStream(item.song.data) ?: run {
                            val ytMatch = YouTube.search("${item.song.title} ${item.song.artist} audio").firstOrNull { it.type == ResultType.SONG }
                            ytMatch?.let { youtubeRepository.extractAudioStream(it, true) }
                        }
                        withContext(Dispatchers.Main) {
                            refreshNativeQueueIfNeeded()
                        }
                    } catch (error: Exception) {
                        Log.w("FridaPlayback", "Remote prefetch skipped for ${item.song.title}: ${error.message}")
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
        refreshNativeQueueIfNeeded()
    }

    private fun List<Song>.firstOrRandom(shuffle: Boolean): Song? =
        if (shuffle) shuffled().firstOrNull() else firstOrNull()

    private fun PlaybackQueueState.fullQueue(): List<QueueItem> =
        previous + listOfNotNull(current) + upNext

    private fun PlaybackQueueState.nativePlaybackQueue(): List<QueueItem> =
        previous + listOfNotNull(current) + upNext + autoplay

    private fun setNativePlayerQueue(
        queueItems: List<QueueItem>,
        currentItem: QueueItem,
        currentPlaybackUri: Uri,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {
        val controller = mediaController ?: return
        val nativeQueue = buildNativeQueue(queueItems, currentItem, currentPlaybackUri)
        if (nativeQueue.mediaItems.isEmpty()) return

        nativeQueueItems = nativeQueue.items
        updatingNativeQueue = true
        try {
            controller.setMediaItems(nativeQueue.mediaItems, nativeQueue.currentIndex, startPositionMs)
            controller.shuffleModeEnabled = false
            controller.prepare()
            if (playWhenReady) {
                controller.play()
            }
        } finally {
            updatingNativeQueue = false
        }
    }

    private fun refreshNativeQueueIfNeeded() {
        val controller = mediaController ?: return
        val state = _queueState.value
        val currentItem = state.current ?: return
        val currentUri = controller.currentMediaItem?.localConfiguration?.uri
            ?: currentItem.song.nativePlaybackUriOrNull()
            ?: return
        val requestedQueue = state.nativePlaybackQueue().ifEmpty { listOf(currentItem) }
        val nativeQueue = buildNativeQueue(requestedQueue, currentItem, currentUri)
        val nextKeys = nativeQueue.items.map { it.stableKey() }
        val currentKeys = nativeQueueItems.map { it.stableKey() }
        if (nextKeys == currentKeys) return

        setNativePlayerQueue(
            queueItems = requestedQueue,
            currentItem = currentItem,
            currentPlaybackUri = currentUri,
            startPositionMs = controller.currentPosition.coerceAtLeast(0L),
            playWhenReady = controller.playWhenReady
        )
    }

    private fun buildNativeQueue(
        queueItems: List<QueueItem>,
        currentItem: QueueItem,
        currentPlaybackUri: Uri
    ): NativeQueue {
        val mediaItems = mutableListOf<MediaItem>()
        val playableItems = mutableListOf<QueueItem>()
        var currentIndex = -1
        val currentKey = currentItem.stableKey()

        queueItems.distinctBy { it.stableKey() }.forEach { item ->
            val playbackUri = if (item.stableKey() == currentKey) {
                currentPlaybackUri
            } else {
                item.song.nativePlaybackUriOrNull()
            } ?: return@forEach

            if (item.stableKey() == currentKey) {
                currentIndex = mediaItems.size
            }
            playableItems += item
            mediaItems += item.song.toMediaItem(playbackUri)
        }

        if (currentIndex == -1) {
            playableItems.add(0, currentItem)
            mediaItems.add(0, currentItem.song.toMediaItem(currentPlaybackUri))
            currentIndex = 0
        }

        return NativeQueue(mediaItems, playableItems, currentIndex)
    }

    private fun syncStateFromNativeQueue(mediaItem: MediaItem?) {
        if (mediaItem == null || nativeQueueItems.isEmpty()) return
        val index = mediaController?.currentMediaItemIndex?.takeIf { it in nativeQueueItems.indices }
            ?: nativeQueueItems.indexOfFirst { it.song.id.toString() == mediaItem.mediaId }
        val item = nativeQueueItems.getOrNull(index) ?: return
        if (_queueState.value.current?.stableKey() == item.stableKey()) return

        val state = _queueState.value
        val nativeKeys = nativeQueueItems.map { it.stableKey() }.toSet()
        val consumedNativeKeys = nativeQueueItems.take(index + 1).map { it.stableKey() }.toSet()
        val futureNativeItems = nativeQueueItems.drop(index + 1)
        _currentSong.value = item.song
        currentPlaybackConfirmed = false
        _currentPosition.value = mediaController?.currentPosition ?: 0L
        _duration.value = mediaController?.duration?.takeIf { it > 0L } ?: item.song.duration
        _queueState.value = state.copy(
            current = item,
            previous = nativeQueueItems.take(index),
            upNext = futureNativeItems.filter { it.source != QueueSource.AUTOPLAY } +
                    state.upNext.filter { it.stableKey() !in nativeKeys },
            autoplay = state.autoplay.filter { it.stableKey() !in consumedNativeKeys },
            source = item.source,
            sourceName = item.reason ?: state.sourceName
        )
        persistQueueState()
        if (mediaController?.isPlaying == true) {
            confirmCurrentPlayback()
        } else {
            persistCurrentPlaybackSnapshot()
        }
    }

    private fun Song.toMediaItem(playbackUri: Uri): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(playbackUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()

    private fun Song.nativePlaybackUriOrNull(): Uri? {
        val uriText = uri.toString()
        if (uriText.isBlank() || uri == Uri.EMPTY) return null
        if (uriText.startsWith("http", ignoreCase = true)) {
            val videoId = youtubeVideoIdOrNull() ?: return null
            return youtubeRepository.getCachedStream(videoId)?.let(Uri::parse)
        }
        return uri
    }

    private fun Song.youtubeVideoIdOrNull(): String? =
        data.takeIf { YOUTUBE_VIDEO_ID.matches(it) }
            ?: runCatching { uri.getQueryParameter("v") }.getOrNull()
            ?: YOUTUBE_VIDEO_ID.find(uri.toString())?.value

    private fun PlaybackQueueState.shuffleEnabled(): PlaybackQueueState {
        val snapshot = shuffleSnapshot.ifEmpty { fullQueue().distinctBy { it.stableKey() } }
        val currentKey = current?.stableKey()
        val shuffledUpNext = upNext
            .filter { it.stableKey() != currentKey }
            .distinctBy { it.stableKey() }
            .shuffled()
        return copy(
            upNext = shuffledUpNext,
            shuffleSnapshot = snapshot
        )
    }

    private fun PlaybackQueueState.shuffleRestored(): PlaybackQueueState {
        val currentItem = current ?: return copy(shuffleSnapshot = emptyList())
        val snapshot = shuffleSnapshot.ifEmpty { return copy(shuffleSnapshot = emptyList()) }
        val currentKey = currentItem.stableKey()
        val activeQueue = fullQueue()
        val activeKeys = activeQueue.map { it.stableKey() }.toSet()
        val snapshotKeys = snapshot.map { it.stableKey() }.toSet()
        val validSnapshot = snapshot
            .filter { it.stableKey() in activeKeys || it.stableKey() == currentKey }
        val validInsertions = activeQueue
            .filter { it.stableKey() !in snapshotKeys }
        val restored = (validSnapshot + validInsertions)
            .distinctBy { it.stableKey() }
        val currentIndex = restored.indexOfFirst { it.stableKey() == currentKey }

        return if (currentIndex >= 0) {
            copy(
                current = currentItem,
                previous = restored.take(currentIndex),
                upNext = restored.drop(currentIndex + 1),
                shuffleSnapshot = emptyList()
            )
        } else {
            copy(
                current = currentItem,
                previous = restored,
                upNext = activeQueue.filter { it.stableKey() != currentKey && it.stableKey() !in restored.map { item -> item.stableKey() } },
                shuffleSnapshot = emptyList()
            )
        }
    }

    private fun List<QueueItem>.insertAfterCurrent(current: QueueItem?, item: QueueItem): List<QueueItem> {
        if (isEmpty()) return this
        val itemKey = item.stableKey()
        val clean = filter { it.stableKey() != itemKey }.toMutableList()
        val currentIndex = current?.let { currentItem ->
            clean.indexOfFirst { it.stableKey() == currentItem.stableKey() }
        } ?: -1
        clean.add((currentIndex + 1).coerceIn(0, clean.size), item)
        return clean
    }

    private fun List<QueueItem>.appendIfActive(item: QueueItem): List<QueueItem> {
        if (isEmpty()) return this
        return (this + item).distinctBy { it.stableKey() }
    }

    private fun List<QueueItem>.removeIfActive(item: QueueItem): List<QueueItem> {
        if (isEmpty()) return this
        val itemKey = item.stableKey()
        return filter { it.stableKey() != itemKey }
    }

    private fun List<QueueItem>.syncWithQueue(state: PlaybackQueueState): List<QueueItem> {
        if (isEmpty()) return this
        val activeKeys = state.fullQueue().map { it.stableKey() }.toSet()
        return filter { it.stableKey() in activeKeys }
    }

    private fun QueueItem.stableKey(): String = song.stablePlaybackKey()

    private data class NativeQueue(
        val mediaItems: List<MediaItem>,
        val items: List<QueueItem>,
        val currentIndex: Int
    )

    private fun Song.stablePlaybackKey(): String =
        uri.toString().takeIf { it.isNotBlank() && it != Uri.EMPTY.toString() }
            ?: data.takeIf { it.isNotBlank() }
            ?: "$title\u0000$artist\u0000$duration"

    private fun Song.isPlayableCandidate(): Boolean {
        val uriText = uri.toString()
        if (uriText.isBlank() || uri == Uri.EMPTY) return false
        if (uriText.startsWith("http", ignoreCase = true)) {
            return data.isNotBlank() || YOUTUBE_VIDEO_ID.find(uriText) != null
        }
        if (uri.scheme == "content") return true
        if (data.isBlank()) return false
        return runCatching { File(data).exists() }.getOrDefault(true)
    }

    private fun PlaybackHistoryEntity.toRestoredSong(): Song? {
        val historyUri = songId.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return null
        val videoId = runCatching { historyUri.getQueryParameter("v") }.getOrNull()
            ?: YOUTUBE_VIDEO_ID.find(songId)?.value
        return Song(
            id = (videoId ?: songId).hashCode().toLong(),
            title = title,
            artist = artist,
            data = videoId ?: songId,
            duration = 0L,
            albumId = 0L,
            uri = historyUri,
            artworkUri = artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
        )
    }

    override fun onCleared() {
        playerListener?.let { listener ->
            mediaController?.removeListener(listener)
        }
        playerListener = null
        persistQueueState()
        persistCurrentPlaybackSnapshot()
        sleepTimerJob?.cancel()
        super.onCleared()
        mediaController?.release()
    }

    private companion object {
        private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
