    package com.jagr.fridamusic.presentation.viewmodels

    import android.net.Uri


    import android.app.Application
    import android.content.BroadcastReceiver
    import android.content.Context
    import android.content.Intent
    import android.content.IntentFilter
    import android.media.audiofx.AudioEffect
    import android.os.Build
    import androidx.annotation.OptIn
    import androidx.core.content.ContextCompat
    import androidx.media3.common.MediaItem
    import androidx.media3.common.Player
    import androidx.media3.common.util.UnstableApi
    import androidx.media3.exoplayer.ExoPlayer
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.jagr.fridamusic.data.remote.innertube.YouTube
    import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
    import com.jagr.fridamusic.domain.lyrics.LyricsLine
    import org.schabi.newpipe.extractor.stream.StreamInfo
    import org.schabi.newpipe.extractor.ServiceList
    import com.jagr.fridamusic.domain.lyrics.LyricsParser
    import com.jagr.fridamusic.data.repository.AudioRepository
    import com.jagr.fridamusic.data.repository.SettingsManager
    import com.jagr.fridamusic.domain.model.Song
    import com.jagr.fridamusic.presentation.service.MusicService
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.isActive
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import org.json.JSONObject
    import java.net.URL
    import java.net.URLEncoder
    import java.util.concurrent.ConcurrentHashMap
    import com.jagr.fridamusic.data.local.MusicDatabase
    import com.jagr.fridamusic.data.local.PlaylistEntity
    import com.jagr.fridamusic.domain.model.Playlist
    import kotlinx.coroutines.flow.map

    class LibraryViewModels(application: Application) : AndroidViewModel(application) {

        private val repository = AudioRepository(application)
        val settingsManager = SettingsManager(application)

        val filterVoiceNotes = MutableStateFlow(settingsManager.filterVoiceNotes)
        val keepScreenOn = MutableStateFlow(settingsManager.keepScreenOn)
        val gaplessPlayback = MutableStateFlow(settingsManager.gaplessPlayback)
        val crossfadeDuration = MutableStateFlow(settingsManager.crossfadeDuration)

        val sleepTimerMinutes = MutableStateFlow(0)
        private var sleepTimerJob: Job? = null

        private val _isExtracting = MutableStateFlow(false)
        val isExtracting = _isExtracting.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage = _errorMessage.asStateFlow()

        private val _songs = MutableStateFlow<List<Song>>(emptyList())
        val songs: StateFlow<List<Song>> = _songs.asStateFlow()

        private var exoPlayer: ExoPlayer? = null
        private var progressJob: Job? = null

        private val _currentSong = MutableStateFlow<Song?>(null)
        val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val repeatMode = MutableStateFlow(RepeatMode.OFF)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

        private val _currentAlbumArt = MutableStateFlow<String?>(null)
        val currentAlbumArt: StateFlow<String?> = _currentAlbumArt.asStateFlow()

        private val _duration = MutableStateFlow(0L)
        val duration: StateFlow<Long> = _duration.asStateFlow()

        private val _lyricsLines = MutableStateFlow<List<LyricsLine>>(emptyList())
        val lyricsLines: StateFlow<List<LyricsLine>> = _lyricsLines.asStateFlow()

        private val _youtubeSearchResults = MutableStateFlow<List<YouTubeResult>>(emptyList())
        val youtubeSearchResults: StateFlow<List<YouTubeResult>> = _youtubeSearchResults.asStateFlow()

        private val playlistDao = MusicDatabase.getDatabase(application).playlistDao()
        val playlists = playlistDao.getAllPlaylists().map { entities ->
            entities.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    description = entity.description,
                    songIds = entity.songIds.split(",").filter { it.isNotBlank() }.map { it.toLong() },
                    createdAt = entity.createdAt
                )
            }
        }

        private val imageUrlCache = ConcurrentHashMap<String, String>()

        private val notificationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "ACTION_PLAY_PAUSE" -> togglePlayback()
                }
            }
        }

        init {
            val filter = IntentFilter().apply {
                addAction("ACTION_PLAY_PAUSE")
                addAction("ACTION_PREV")
                addAction("ACTION_NEXT")
            }
            ContextCompat.registerReceiver(
                getApplication(),
                notificationReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            ensureFavoritesPlaylistExists()
        }

        private fun ensureFavoritesPlaylistExists() {
            viewModelScope.launch(Dispatchers.IO) {
                val allPlaylists = playlistDao.getAllPlaylistsOnce()
                if (allPlaylists.none { it.name == "Favorites" }) {
                    createPlaylist("Favorites", "Your automatically liked songs")
                }
            }
        }

        fun searchYouTube(query: String) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val results = YouTube.search(query)
                    _youtubeSearchResults.value = results
                } catch (e: Exception) {
                    e.printStackTrace()
                    _youtubeSearchResults.value = emptyList()
                }
            }
        }

        fun loadSongs() {
            viewModelScope.launch(Dispatchers.IO) {
                val audioFiles = repository.getAudioFiles(filterVoiceNotes.value)
                _songs.value = audioFiles
            }
        }

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

        @OptIn(UnstableApi::class)
        fun playSong(song: Song) {
            if (_currentSong.value?.id == song.id && exoPlayer != null) {
                togglePlayback()
                return
            }

            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                val mediaItem = MediaItem.fromUri(song.uri)
                setMediaItem(mediaItem)
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

            _currentAlbumArt.value = null
            viewModelScope.launch(Dispatchers.IO) {
                val url = getSongImageUrl(song)
                _currentAlbumArt.value = url

                val lyrics = fetchLyrics(song.artist ?: "", song.title ?: "")
                _currentSong.value = _currentSong.value?.copy(lyrics = lyrics)
                _lyricsLines.value = lyrics?.let { LyricsParser.parseLrc(it) } ?: emptyList()

                updateNotification(song, true)
            }
        }

        fun createPlaylist(name: String, description: String? = null) {
            viewModelScope.launch(Dispatchers.IO) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = name,
                        description = description,
                        songIds = "",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        fun addSongToPlaylist(playlist: Playlist, songId: Long) {
            viewModelScope.launch(Dispatchers.IO) {
                val newIds = (playlist.songIds + songId).distinct().joinToString(",")
                playlistDao.updatePlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        description = playlist.description,
                        songIds = newIds,
                        createdAt = playlist.createdAt
                    )
                )
            }
        }

        private suspend fun fetchLyrics(artist: String, title: String): String? = withContext(Dispatchers.IO) {
            try {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                val url = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
                val response = URL(url).readText()
                val json = JSONObject(response)
                json.optString("lyrics").takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }

        fun togglePlayback() {
            exoPlayer?.let { player ->
                if (player.isPlaying) player.pause() else player.play()
                _isPlaying.value = player.isPlaying
                _currentSong.value?.let { song -> updateNotification(song, player.isPlaying) }
            }
        }

        fun seekTo(position: Long) {
            exoPlayer?.seekTo(position)
        }


        fun playYouTubeSong(result: YouTubeResult) {
            viewModelScope.launch(Dispatchers.IO) {
                _isExtracting.value = true
                _errorMessage.value = null

                try {
                    // High-performance extraction with NewPipe
                    val videoUrl = "https://www.youtube.com/watch?v=" + result.videoId
                    val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

                    // Motor Logic: Prioritize OPUS (WebM) > M4A > anything else with high bitrate
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
                    e.printStackTrace()
                    _errorMessage.value = "Error extracting audio: ${e.localizedMessage}"
                } finally {
                    _isExtracting.value = false
                }
            }
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

        private fun stopProgressUpdate() {
            progressJob?.cancel()
        }

        fun skipToNext() {
            val currentId = _currentSong.value?.id ?: return

            // 1. Modo YouTube: Verificamos si la canción actual es un resultado de YouTube
            val ytList = _youtubeSearchResults.value
            val ytIndex = ytList.indexOfFirst { it.videoId.hashCode().toLong() == currentId }

            if (ytIndex != -1) {
                // Es de YouTube: saltamos al siguiente resultado de la búsqueda
                val nextIndex = if (ytIndex == ytList.size - 1) 0 else ytIndex + 1
                playYouTubeSong(ytList[nextIndex])
                return
            }

            // 2. Modo Local: Si no estaba en YouTube, avanzamos en la biblioteca local
            val localList = _songs.value
            if (localList.isEmpty()) return

            val localIndex = localList.indexOfFirst { it.id == currentId }
            val nextIndex = if (localIndex == -1 || localIndex == localList.size - 1) 0 else localIndex + 1
            playSong(localList[nextIndex])
        }

        fun skipToPrevious() {
            val currentId = _currentSong.value?.id ?: return

            val ytList = _youtubeSearchResults.value
            val ytIndex = ytList.indexOfFirst { it.videoId.hashCode().toLong() == currentId }

            if (ytIndex != -1) {
                val prevIndex = if (ytIndex <= 0) ytList.size - 1 else ytIndex - 1
                playYouTubeSong(ytList[prevIndex])
                return
            }

            val localList = _songs.value
            if (localList.isEmpty()) return

            val localIndex = localList.indexOfFirst { it.id == currentId }
            val prevIndex = if (localIndex <= 0) localList.size - 1 else localIndex - 1
            playSong(localList[prevIndex])
        }

        fun toggleFavorite(song: Song) {
            viewModelScope.launch(Dispatchers.IO) {
                val isAdding = !song.isFavorite

                // Update state
                _songs.value = _songs.value.map {
                    if (it.id == song.id) it.copy(isFavorite = isAdding) else it
                }
                if (_currentSong.value?.id == song.id) {
                    _currentSong.value = _currentSong.value?.copy(isFavorite = isAdding)
                }

                // Sync with Favorites Playlist
                val allPlaylists = playlistDao.getAllPlaylistsOnce()
                val favoritesPlaylist = allPlaylists.find { it.name == "Favorites" }
                favoritesPlaylist?.let {
                    if (isAdding) {
                        addSongToPlaylist(
                            Playlist(it.id, it.name, it.description, it.songIds.split(",").filter { id -> id.isNotBlank() }.map { id -> id.toLong() }),
                            song.id
                        )
                    } else {
                        removeSongFromPlaylist(
                            Playlist(it.id, it.name, it.description, it.songIds.split(",").filter { id -> id.isNotBlank() }.map { id -> id.toLong() }),
                            song.id
                        )
                    }
                }
            }
        }

        fun removeSongFromPlaylist(playlist: Playlist, songId: Long) {
            viewModelScope.launch(Dispatchers.IO) {
                val newIds = playlist.songIds.filter { it != songId }.joinToString(",")
                playlistDao.updatePlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        description = playlist.description,
                        songIds = newIds,
                        createdAt = playlist.createdAt
                    )
                )
            }
        }

        private fun updateNotification(song: Song, isPlaying: Boolean) {
            val intent = Intent(getApplication(), MusicService::class.java).apply {
                putExtra("TITLE", song.title)
                putExtra("ARTIST", song.artist)
                putExtra("IS_PLAYING", isPlaying)
                putExtra("ALBUM_ART_URL", _currentAlbumArt.value)
                putExtra("CURRENT_POSITION", _currentPosition.value)
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
            if (url != null) {
                imageUrlCache[cacheKey] = url
            }
            return@withContext url
        }

        suspend fun getArtistImageUrl(artistName: String): String? = withContext(Dispatchers.IO) {
            val cacheKey = "artist_$artistName"
            if (imageUrlCache.containsKey(cacheKey)) return@withContext imageUrlCache[cacheKey]

            val url = fetchAlbumArt(artistName, "")
            if (url != null) {
                imageUrlCache[cacheKey] = url
            }
            return@withContext url
        }

        private fun fetchAlbumArt(title: String, artist: String?): String? {
            return try {
                var cleanQuery = title.lowercase()
                    .replace(".mp3", "").replace(".m4a", "").replace(".wav", "")
                    .replace("_", " ").replace("-", " ")
                    .replace(Regex("\\(.*?\\)"), "")
                    .replace(Regex("\\[.*?\\]"), "")
                    .replace("official", "", ignoreCase = true)
                    .replace("video", "", ignoreCase = true)
                    .replace("audio", "", ignoreCase = true)
                    .replace("lyrics", "", ignoreCase = true)
                    .trim()

                val cleanArtist = if (artist.isNullOrBlank() || artist.contains("unknown", ignoreCase = true)) {
                    ""
                } else {
                    artist
                }

                val finalQueryText = "$cleanQuery $cleanArtist".trim().replace(Regex("\\s+"), " ")
                val encodedQuery = URLEncoder.encode(finalQueryText, "UTF-8")
                val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=1"

                val response = URL(url).readText()
                val json = JSONObject(response)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    firstResult.getString("artworkUrl100").replace("100x100bb.jpg", "600x600bb.jpg")
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
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
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0) // ExoPlayer doesn't easily expose session ID without more setup
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getApplication<Application>().packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            intentLauncher(intent)
        }

        override fun onCleared() {
            super.onCleared()
            exoPlayer?.release()
            try {
                getApplication<Application>().unregisterReceiver(notificationReceiver)
            } catch (e: Exception) {}

            val intent = Intent(getApplication(), MusicService::class.java).apply { action = "STOP_SERVICE" }
            getApplication<Application>().startService(intent)
        }
    }

    enum class RepeatMode { OFF, ALL, ONE}