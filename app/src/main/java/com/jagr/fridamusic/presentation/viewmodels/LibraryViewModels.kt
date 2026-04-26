package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class LibraryViewModels(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)
    val settingsManager = SettingsManager(application)

    val filterVoiceNotes = MutableStateFlow(settingsManager.filterVoiceNotes)
    val keepScreenOn = MutableStateFlow(settingsManager.keepScreenOn)
    val gaplessPlayback = MutableStateFlow(settingsManager.gaplessPlayback)
    val crossfadeDuration = MutableStateFlow(settingsManager.crossfadeDuration)

    val sleepTimerMinutes = MutableStateFlow(0)
    private var sleepTimerJob: Job? = null

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentAlbumArt = MutableStateFlow<String?>(null)
    val currentAlbumArt: StateFlow<String?> = _currentAlbumArt.asStateFlow()

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
    }

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFiles = repository.getAudioFiles(filterVoiceNotes.value)
            _songs.value = audioFiles
        }
    }

    fun playSong(song: Song) {
        if (_currentSong.value?.id == song.id) {
            togglePlayback()
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(getApplication(), song.uri)
            prepare()
            start()
            setOnCompletionListener {
                _isPlaying.value = false
                stopProgressUpdate()
                _currentPosition.value = 0L
                updateNotification(song, false)
            }
        }
        _currentSong.value = song
        _isPlaying.value = true
        startProgressUpdate()

        updateNotification(song, true)

        _currentAlbumArt.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val url = getSongImageUrl(song)
            _currentAlbumArt.value = url
            updateNotification(song, true)
        }
    }

    fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressUpdate()
                _currentSong.value?.let { song -> updateNotification(song, false) }
            } else {
                it.start()
                _isPlaying.value = true
                startProgressUpdate()
                _currentSong.value?.let { song -> updateNotification(song, true) }
            }
        }
    }

    fun seekTo(position: Long) {
        mediaPlayer?.let {
            it.seekTo(position.toInt())
            _currentPosition.value = position
            _currentSong.value?.let { song -> updateNotification(song, _isPlaying.value) }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPosition.value = it.currentPosition.toLong()
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
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
                mediaPlayer?.pause()
                _isPlaying.value = false
                stopProgressUpdate()
                sleepTimerMinutes.value = 0
                _currentSong.value?.let { song -> updateNotification(song, false) }
            }
        }
    }

    fun openSystemEqualizer(intentLauncher: (Intent) -> Unit) {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mediaPlayer?.audioSessionId ?: 0)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getApplication<Application>().packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        intentLauncher(intent)
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        try {
            getApplication<Application>().unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {}

        val intent = Intent(getApplication(), MusicService::class.java).apply { action = "STOP_SERVICE" }
        getApplication<Application>().startService(intent)
    }
}