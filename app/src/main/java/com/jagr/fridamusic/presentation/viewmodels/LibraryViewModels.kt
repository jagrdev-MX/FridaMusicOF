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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.MusicDatabase
import com.jagr.fridamusic.data.local.PlaylistEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class LibraryViewModels(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    val settingsManager = SettingsManager(application)
    private val playlistDao = MusicDatabase.getDatabase(application).playlistDao()
    val filterVoiceNotes = MutableStateFlow(settingsManager.filterVoiceNotes)
    val keepScreenOn = MutableStateFlow(settingsManager.keepScreenOn)
    val gaplessPlayback = MutableStateFlow(settingsManager.gaplessPlayback)
    val crossfadeDuration = MutableStateFlow(settingsManager.crossfadeDuration)
    val saveLastPlayback = MutableStateFlow(settingsManager.saveLastPlayback)

    private val _currentTheme = MutableStateFlow(AppTheme.SYSTEM)
    val currentTheme = _currentTheme.asStateFlow()

    val sleepTimerMinutes = MutableStateFlow(0)
    private var sleepTimerJob: Job? = null
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

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _currentAlbumArt = MutableStateFlow<String?>(null)
    val currentAlbumArt = _currentAlbumArt.asStateFlow()

    private val _lyricsLines = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyricsLines = _lyricsLines.asStateFlow()

    val repeatMode = MutableStateFlow(RepeatMode.OFF)
    val isShuffleMode = MutableStateFlow(false)
    private val _youtubeSearchResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
    val youtubeSearchResults = _youtubeSearchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

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
            Playlist(entity.id, entity.name, entity.description, entity.songIds.split(",").filter { it.isNotBlank() }.map { it.toLong() }, entity.createdAt)
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private val imageUrlCache = ConcurrentHashMap<String, String>()

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
        restoreLastPlaybackState()
        _searchHistory.value = settingsManager.searchHistory.split("|").filter { it.isNotBlank() }
    }

    @OptIn(UnstableApi::class)
    private fun createExoPlayer(): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(getApplication(), httpDataSourceFactory)

        return ExoPlayer.Builder(getApplication())
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    @OptIn(UnstableApi::class)
    fun playSong(song: Song) {
        if (_currentSong.value?.id == song.id && exoPlayer != null) {
            togglePlayback()
            return
        }

        saveCurrentPlaybackState()

        exoPlayer?.release()
        exoPlayer = createExoPlayer().apply {
            setMediaItem(MediaItem.fromUri(song.uri))
            repeatMode = when (this@LibraryViewModels.repeatMode.value) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            prepare()
            play()

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                    updateNotification(song, isPlaying)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                        updateNotification(song, _isPlaying.value)
                    }
                    if (state == Player.STATE_ENDED) {
                        _currentPosition.value = 0L
                        skipToNext()
                    }
                }
            })
        }

        _currentSong.value = song
        _isPlaying.value = true
        startProgressUpdate()
        updateNotification(song, true)

        val hasPreloadedArt = song.artworkUri != Uri.EMPTY && song.artworkUri.toString().startsWith("http")
        _currentAlbumArt.value = if (hasPreloadedArt) song.artworkUri.toString() else null

        viewModelScope.launch(Dispatchers.IO) {
            val url = if (hasPreloadedArt) song.artworkUri.toString() else getSongImageUrl(song)
            val lyrics = fetchLyrics(song.artist ?: "", song.title ?: "")

            withContext(Dispatchers.Main) {
                _currentAlbumArt.value = url
                _currentSong.value = _currentSong.value?.copy(lyrics = lyrics)
                _lyricsLines.value = lyrics?.let { LyricsParser.parseLrc(it) } ?: emptyList()
                updateNotification(song, true)
            }
        }
    }

    fun playYouTubeSong(result: YouTubeResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExtracting.value = true
            _errorMessage.value = null

            try {
                val videoUrl = "https://www.youtube.com/watch?v=" + result.videoId
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

                val audioStream = streamInfo.audioStreams.filter { it.format?.name?.contains("OPUS", ignoreCase = true) == true }.maxByOrNull { it.bitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.bitrate }

                if (audioStream != null) {
                    val virtualSong = Song(
                        id = result.videoId.hashCode().toLong(),
                        title = result.title,
                        artist = result.artist,
                        data = audioStream.url ?: "",
                        duration = streamInfo.duration * 1000L,
                        albumId = 0L,
                        uri = Uri.parse(audioStream.url ?: ""),
                        artworkUri = if (result.thumbnailUrl.isNotEmpty()) Uri.parse(result.thumbnailUrl) else Uri.EMPTY
                    )

                    withContext(Dispatchers.Main) {
                        _currentAlbumArt.value = result.thumbnailUrl
                        playSong(virtualSong)
                    }
                } else {
                    _errorMessage.value = "No compatible audio streams found"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error extracting audio: ${e.localizedMessage}"
            } finally {
                _isExtracting.value = false
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
        settingsManager.lastSongArtwork = song.artworkUri.toString()
        settingsManager.lastSongDuration = song.duration
        settingsManager.lastPosition = pos
    }

    @OptIn(UnstableApi::class)
    private fun restoreLastPlaybackState() {
        if (settingsManager.saveLastPlayback && settingsManager.lastSongId != -1L) {
            val savedSong = Song(
                id = settingsManager.lastSongId,
                title = settingsManager.lastSongTitle ?: "Unknown Title",
                artist = settingsManager.lastSongArtist ?: "Unknown Artist",
                data = "",
                duration = settingsManager.lastSongDuration,
                albumId = 0L,
                uri = Uri.parse(settingsManager.lastSongUri ?: ""),
                artworkUri = Uri.parse(settingsManager.lastSongArtwork ?: "")
            )

            _currentSong.value = savedSong
            _currentPosition.value = settingsManager.lastPosition
            _duration.value = settingsManager.lastSongDuration
            _currentAlbumArt.value = settingsManager.lastSongArtwork

            exoPlayer = createExoPlayer().apply {
                val uriString = settingsManager.lastSongUri
                if (!uriString.isNullOrBlank()) {
                    setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
                    prepare()
                    seekTo(settingsManager.lastPosition)
                }

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                        updateNotification(savedSong, isPlaying)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                        }
                        if (state == Player.STATE_ENDED) {
                            _currentPosition.value = 0L
                            skipToNext()
                        }
                    }
                })
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFiles = repository.getAudioFiles(filterVoiceNotes.value)
            _songs.value = audioFiles
        }
    }

    private fun ensureFavoritesPlaylistExists() {
        viewModelScope.launch(Dispatchers.IO) {
            val favoritesName = getApplication<Application>().getString(R.string.favorites_playlist_name)
            val favoritesDesc = getApplication<Application>().getString(R.string.favorites_playlist_description)
            val allPlaylists = playlistDao.getAllPlaylistsOnce()
            if (allPlaylists.none { it.name == favoritesName }) {
                createPlaylist(favoritesName, favoritesDesc)
            }
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = name,
                    description = description ?: "",
                    songIds = "",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun addSongToPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val newIds = (playlist.songIds + songId).distinct().joinToString(",")
            playlistDao.updatePlaylist(PlaylistEntity(id = playlist.id, name = playlist.name, description = playlist.description, songIds = newIds, createdAt = playlist.createdAt))
        }
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val newIds = playlist.songIds.filter { it != songId }.joinToString(",")
            playlistDao.updatePlaylist(PlaylistEntity(id = playlist.id, name = playlist.name, description = playlist.description, songIds = newIds, createdAt = playlist.createdAt))
        }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val favoritesName = getApplication<Application>().getString(R.string.favorites_playlist_name)
            val currentPlaylists = playlists.first()
            var likePlaylist = currentPlaylists.find { it.name == favoritesName }

            if (likePlaylist == null) {
                createPlaylist(favoritesName)
                delay(300)
                likePlaylist = playlists.first().find { it.name == favoritesName }
            }

            likePlaylist?.let { playlist ->
                if (playlist.songIds.contains(song.id)) {
                    removeSongFromPlaylist(playlist, song.id)
                } else {
                    addSongToPlaylist(playlist, song.id)
                }
            }
        }
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                _youtubeSearchResults.value = YouTube.search(query)
            } catch (e: Exception) {
                _youtubeSearchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query)
        currentHistory.add(0, query)
        val newHistory = currentHistory.take(15) // Keep last 15
        _searchHistory.value = newHistory
        settingsManager.searchHistory = newHistory.joinToString("|")
    }

    fun removeFromSearchHistory(query: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query)
        _searchHistory.value = currentHistory
        settingsManager.searchHistory = currentHistory.joinToString("|")
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        settingsManager.searchHistory = ""
    }

    fun setShuffleMode(enabled: Boolean) { isShuffleMode.value = enabled }
    fun toggleShuffleMode() { isShuffleMode.value = !isShuffleMode.value }

    fun toggleRepeatMode() {
        repeatMode.value = when (repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        exoPlayer?.repeatMode = when (repeatMode.value) {
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
                _currentPosition.value = exoPlayer?.currentPosition ?: 0L
                _duration.value = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                delay(500)
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
        val cacheKey = "song_${song.id}"
        if (imageUrlCache.containsKey(cacheKey)) return@withContext imageUrlCache[cacheKey]
        val url = fetchAlbumArt(song.title, song.artist)
        if (url != null) imageUrlCache[cacheKey] = url
        return@withContext url
    }

    suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = "artist_$artistName"
        if (imageUrlCache.containsKey(cacheKey)) return@withContext imageUrlCache[cacheKey]
        val url = fetchAlbumArt(artistName, "")
        if (url != null) imageUrlCache[cacheKey] = url
        return@withContext url
    }

    private fun fetchAlbumArt(title: String, artist: String?): String? {
        return try {
            val cleanQuery = title.lowercase().replace(".mp3", "").replace(".m4a", "").replace(".wav", "").replace("_", " ").replace("-", " ").replace(Regex("\\(.*?\\)"), "").replace(Regex("\\[.*?\\]"), "").replace("official", "", ignoreCase = true).replace("video", "", ignoreCase = true).replace("audio", "", ignoreCase = true).replace("lyrics", "", ignoreCase = true).trim()
            val cleanArtist = if (artist.isNullOrBlank() || artist.contains("unknown", ignoreCase = true)) "" else artist
            val finalQueryText = "$cleanQuery $cleanArtist".trim().replace(Regex("\\s+"), " ")
            val encodedQuery = URLEncoder.encode(finalQueryText, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=1"

            val response = URL(url).readText()
            val json = JSONObject(response)
            val results = json.getJSONArray("results")

            if (results.length() > 0) results.getJSONObject(0).getString("artworkUrl100").replace("100x100bb.jpg", "600x600bb.jpg") else null
        } catch (e: Exception) { null }
    }

    private suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").trim()
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

    override fun onCleared() {
        saveCurrentPlaybackState()
        super.onCleared()
        exoPlayer?.release()
        try { getApplication<Application>().unregisterReceiver(notificationReceiver) } catch (e: Exception) {}
        getApplication<Application>().startService(Intent(getApplication(), MusicService::class.java).apply { action = "STOP_SERVICE" })
    }
}

enum class RepeatMode { OFF, ALL, ONE }
enum class AppTheme(val displayName: String) { SYSTEM("System Default"), LIGHT("Light"), DARK("Dark") }