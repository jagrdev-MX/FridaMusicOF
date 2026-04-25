package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.data.repository.AudioRepository
import com.jagr.fridamusic.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.media.MediaPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class LibraryViewModels(application: Application) : AndroidViewModel(application) {

    private val repository = AudioRepository(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun loadSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFiles = repository.getAudioFiles()
            _songs.value = audioFiles
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

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
            }
        }
        _currentSong.value = song
        _isPlaying.value = true
        startProgressUpdate()
    }

    fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressUpdate()
            } else {
                it.start()
                _isPlaying.value = true
                startProgressUpdate()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}