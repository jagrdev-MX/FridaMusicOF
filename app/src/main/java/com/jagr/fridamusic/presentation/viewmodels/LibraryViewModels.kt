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
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.service.MusicService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URL
import java.net.URLEncoder
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
    private val _manualQueue = MutableStateFlow<List<Song>>(emptyList())
    private val _playlistCoverUris = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _followedArtists = MutableStateFlow(settingsManager.followedArtists)

    val recentHistory = _recentHistory.asStateFlow()
    val fullHistory = _fullHistory.asStateFlow()
    val searchHistory = _searchHistory.asStateFlow()
    val manualQueue = _manualQueue.asStateFlow()
    val playlistCoverUris = _playlistCoverUris.asStateFlow()
    val followedArtists = _followedArtists.asStateFlow()

    val isAutoPlayEnabled = MutableStateFlow(false)

    fun toggleAutoplay(enabled: Boolean) {
        isAutoPlayEnabled.value = enabled
    }

    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        val current = _searchHistory.value.toMutableList()
        current.remove(query)
        current.add(0, query)
        if (current.size > 10) current.removeAt(current.lastIndex)

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

    private val _repeatMode = MutableStateFlow<RepeatMode>(RepeatMode.OFF)
    val repeatMode = _repeatMode.asStateFlow()
    val isShuffleMode = MutableStateFlow(false)

    private val _youtubeSearchResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
    val youtubeSearchResults = _youtubeSearchResults.asStateFlow()

    val artistSongs = _youtubeSearchResults.map { results ->
        results.filter { it.type == ResultType.SONG }.map { result ->
            Song(result.videoId.hashCode().toLong(), result.title, result.artist, result.videoId, 0L, 0L, Uri.parse(""), Uri.parse(result.thumbnailUrl))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artistPlaylists = _youtubeSearchResults.map { results ->
        results.filter { it.type == ResultType.PLAYLIST }.map { result ->
            Song(result.videoId.hashCode().toLong(), result.title, result.artist, result.videoId, 0L, 0L, Uri.parse(""), Uri.parse(result.thumbnailUrl))
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
    private val imageUrlCache = ConcurrentHashMap<String, String>()
    private val audioStreamCache = ConcurrentHashMap<String, String>()
    private val deezerToYoutubeMap = ConcurrentHashMap<String, String>()

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

                            if (isAutoPlayEnabled.value) {
                                skipToNext()
                            } else {
                                _isPlaying.value = false
                                exoPlayer?.seekTo(0)
                                exoPlayer?.pause()
                                _currentSong.value?.let { updateNotification(it, false) }
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

                exoPlayer?.apply {
                    val uriString = savedSong.uri.toString()
                    if (uriString.isNotBlank()) {
                        try {
                            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                            prepare()
                            seekTo(lastPos)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun playSong(song: Song) {
        if (_currentSong.value?.id == song.id && exoPlayer != null) {
            togglePlayback()
            return
        }

        _errorMessage.value = null
        saveCurrentPlaybackState()

        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(song.uri))
            repeatMode = when (_repeatMode.value) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            prepare()
            play()
        }

        _currentSong.value = song
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

    fun playHistoryItem(history: PlaybackHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val isRemoteHistoryItem = history.songId.startsWith("http", ignoreCase = true)

            if (isRemoteHistoryItem) {
                val match = YouTube.search("${history.title} ${history.artist} audio")
                    .firstOrNull { it.type == ResultType.SONG }

                if (match != null) {
                    withContext(Dispatchers.Main) {
                        playYouTubeSong(
                            YouTubeResult(
                                videoId = match.videoId,
                                title = history.title,
                                artist = history.artist,
                                thumbnailUrl = history.artworkUrl.orEmpty()
                            )
                        )
                    }
                } else {
                    _errorMessage.value = "No se pudo volver a cargar esta cancion del historial"
                }
                return@launch
            }

            val localSong = _songs.value.firstOrNull { song ->
                song.uri.toString() == history.songId ||
                    (song.title == history.title && song.artist == history.artist)
            }

            if (localSong != null) {
                withContext(Dispatchers.Main) { playSong(localSong) }
            } else {
                _errorMessage.value = "Esta cancion ya no esta disponible en el dispositivo"
            }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        playSongs(songsForPlaylist(playlist), shuffle)
    }

    fun playSongs(collection: List<Song>, shuffle: Boolean = false) {
        val playableSongs = if (shuffle) collection.shuffled() else collection
        val nextSong = playableSongs.firstOrNull()

        if (nextSong != null) {
            setShuffleMode(shuffle)
            _manualQueue.value = playableSongs.drop(1)
            playSongFromLibrary(nextSong)
        } else {
            _errorMessage.value = "No hay canciones disponibles para reproducir"
        }
    }

    fun playSongFromLibrary(song: Song) {
        if (song.requiresRemoteExtraction()) {
            playYouTubeSong(
                YouTubeResult(
                    videoId = song.data,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.artworkUri.toString(),
                    type = ResultType.SONG
                )
            )
        } else {
            playSong(song)
        }
    }

    fun addSongNext(song: Song) {
        _manualQueue.value = listOf(song) + _manualQueue.value
    }

    fun addSongToQueue(song: Song) {
        _manualQueue.value = _manualQueue.value + song
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        val queuedSongs = songsForPlaylist(playlist)
        _manualQueue.value = _manualQueue.value + queuedSongs
    }

    fun addSongsToQueue(collection: List<Song>) {
        _manualQueue.value = _manualQueue.value + collection
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
        _manualQueue.value = emptyList()
    }

    fun playYouTubeSong(result: YouTubeResult) {
        extractionJob?.cancel()

        extractionJob = viewModelScope.launch(Dispatchers.IO) {
            _isExtracting.value = true
            _errorMessage.value = null

            try {
                var realYtId = deezerToYoutubeMap[result.videoId]

                if (realYtId == null) {
                    val ytMatch = YouTube.search("${result.title} ${result.artist} audio").firstOrNull { it.type == ResultType.SONG }
                    realYtId = ytMatch?.videoId ?: throw Exception("No se encontró versión en YouTube")
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
                        playSong(virtualSong)

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
                        playSong(virtualSong)
                        prefetchNextSongInList(result.videoId)
                    }
                } else {
                    _errorMessage.value = "No compatible audio streams found"
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _errorMessage.value = "Error extracting audio: ${e.localizedMessage}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isExtracting.value = false
                }
            }
        }
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
        val queuedSong = _manualQueue.value.firstOrNull()
        if (queuedSong != null) {
            _manualQueue.value = _manualQueue.value.drop(1)
            playSongFromLibrary(queuedSong)
            return
        }

        val currentId = _currentSong.value?.id ?: return
        val ytList = _youtubeSearchResults.value
        val ytIndex = ytList.indexOfFirst { it.videoId.hashCode().toLong() == currentId }

        if (ytIndex != -1) {
            if (isShuffleMode.value && ytList.size > 1) {
                var randomResult: YouTubeResult
                do { randomResult = ytList.random() } while (randomResult.videoId.hashCode().toLong() == currentId)
                playYouTubeSong(randomResult)
            } else {
                val nextIndex = if (ytIndex == ytList.size - 1) 0 else ytIndex + 1
                playYouTubeSong(ytList[nextIndex])
            }
            return
        }

        val localList = _songs.value
        if (localList.isEmpty()) return

        val localIndex = localList.indexOfFirst { it.id == currentId }
        if (isShuffleMode.value && localList.size > 1) {
            var randomSong: Song
            do { randomSong = localList.random() } while (randomSong.id == currentId)
            playSong(randomSong)
        } else {
            val nextIndex = if (localIndex == -1 || localIndex == localList.size - 1) 0 else localIndex + 1
            playSong(localList[nextIndex])
        }
    }

    fun skipToPrevious() {
        val currentId = _currentSong.value?.id ?: return
        val ytList = _youtubeSearchResults.value
        val ytIndex = ytList.indexOfFirst { it.videoId.hashCode().toLong() == currentId }

        if (ytIndex != -1) {
            if (isShuffleMode.value && ytList.size > 1) {
                var randomResult: YouTubeResult
                do { randomResult = ytList.random() } while (randomResult.videoId.hashCode().toLong() == currentId)
                playYouTubeSong(randomResult)
            } else {
                val prevIndex = if (ytIndex <= 0) ytList.size - 1 else ytIndex - 1
                playYouTubeSong(ytList[prevIndex])
            }
            return
        }

        val localList = _songs.value
        if (localList.isEmpty()) return

        val localIndex = localList.indexOfFirst { it.id == currentId }
        if (isShuffleMode.value && localList.size > 1) {
            var randomSong: Song
            do { randomSong = localList.random() } while (randomSong.id == currentId)
            playSong(randomSong)
        } else {
            val prevIndex = if (localIndex <= 0) localList.size - 1 else localIndex - 1
            playSong(localList[prevIndex])
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
            if (allPlaylists.none { it.name == "Me gusta" }) {
                createPlaylist("Me gusta", "Tus canciones favoritas")
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
            var likePlaylistEntity = allPlaylists.find { it.name == "Me gusta" }

            if (likePlaylistEntity == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Me gusta",
                        description = "Tus canciones favoritas",
                        songIds = "",
                        createdAt = System.currentTimeMillis()
                    )
                )
                delay(200)
                val updatedPlaylists = playlistDao.getAllPlaylistsOnce()
                likePlaylistEntity = updatedPlaylists.find { it.name == "Me gusta" }
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
        if (query.isBlank()) return
        searchJob?.cancel()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                delay(300)
                if (!isActive) return@launch

                val results = DeezerApi.search(query)

                if (isActive && results.isNotEmpty()) {
                    _youtubeSearchResults.value = results
                    addToSearchHistory(query)

                    launch(Dispatchers.IO) {
                        results.take(5).forEach { deezerResult ->
                            try {
                                val ytSearchQuery = "${deezerResult.title} ${deezerResult.artist} audio"
                                val ytMatch = YouTube.search(ytSearchQuery).firstOrNull { it.type == ResultType.SONG }

                                if (ytMatch != null) {
                                    deezerToYoutubeMap[deezerResult.videoId] = ytMatch.videoId
                                    prefetchYouTubeAudio(ytMatch.videoId)
                                }
                            } catch (e: Exception) {}
                        }
                    }
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

    fun setShuffleMode(enabled: Boolean) { isShuffleMode.value = enabled }
    fun toggleShuffleMode() { isShuffleMode.value = !isShuffleMode.value }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        exoPlayer?.repeatMode = when (_repeatMode.value) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
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
