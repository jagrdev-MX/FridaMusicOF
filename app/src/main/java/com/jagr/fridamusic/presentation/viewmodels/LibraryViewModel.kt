package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.MusicDatabase
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.data.local.PlaylistEntity
import com.jagr.fridamusic.data.repository.*
import com.jagr.fridamusic.domain.lyrics.LyricsResult
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val audioRepository: AudioRepository,
    val settingsManager: SettingsManager,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val artworkRepository: ArtworkRepository,
    private val lyricsRepository: LyricsRepository
) : AndroidViewModel(application) {
    private val playlistDao = MusicDatabase.getDatabase(application).playlistDao()
    private var loadSongsJob: Job? = null
    private var mediaStoreScanJob: Job? = null
    private var mediaStoreObserver: ContentObserver? = null

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _recentHistory = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    val recentHistory = _recentHistory.asStateFlow()

    private val _fullHistory = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    val fullHistory = _fullHistory.asStateFlow()

    private val _mostPlayedHistory = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    val mostPlayedHistory = _mostPlayedHistory.asStateFlow()

    private val _playlistCoverUris = MutableStateFlow<Map<Long, String>>(emptyMap())
    val playlistCoverUris = _playlistCoverUris.asStateFlow()

    private val _followedArtists = MutableStateFlow(settingsManager.followedArtists)
    val followedArtists = _followedArtists.asStateFlow()

    val playlists = playlistDao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            Playlist(entity.id, entity.name, entity.description ?: "", entity.songIds.split(",").filter { it.isNotBlank() }.map { it.toLong() }, entity.createdAt)
        }
    }

    init {
        startMediaStoreObserver()
        loadSongs()
        observePlaybackHistory()
        refreshPlaylistCoverUris()
    }

    fun loadSongs() {
        loadSongsJob?.cancel()
        loadSongsJob = viewModelScope.launch(Dispatchers.IO) {
            val loadedSongs = audioRepository.getAudioFiles(settingsManager.filterVoiceNotes)
            if (loadedSongs.mediaSignature() != _songs.value.mediaSignature()) {
                _songs.value = loadedSongs
                refreshPlaylistCoverUris()
            }
        }
    }

    private fun startMediaStoreObserver() {
        if (mediaStoreObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scheduleMediaStoreRefresh()
            }

            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                scheduleMediaStoreRefresh()
            }
        }
        getApplication<Application>().contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        mediaStoreObserver = observer
    }

    private fun scheduleMediaStoreRefresh() {
        mediaStoreScanJob?.cancel()
        mediaStoreScanJob = viewModelScope.launch {
            delay(900)
            loadSongs()
        }
    }

    fun loadRecentHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _recentHistory.value = playbackHistoryRepository.getRecentHistory(10)
            _fullHistory.value = playbackHistoryRepository.getFullHistory()
            _mostPlayedHistory.value = playbackHistoryRepository.getMostPlayed(20)
        }
    }

    private fun observePlaybackHistory() {
        viewModelScope.launch {
            playbackHistoryRepository.observeRecentHistory(10)
                .flowOn(Dispatchers.IO)
                .collect { _recentHistory.value = it }
        }
        viewModelScope.launch {
            playbackHistoryRepository.observeFullHistory()
                .flowOn(Dispatchers.IO)
                .collect { _fullHistory.value = it }
        }
        viewModelScope.launch {
            playbackHistoryRepository.observeMostPlayed(20)
                .flowOn(Dispatchers.IO)
                .collect { _mostPlayedHistory.value = it }
        }
    }

    fun refreshPlaylistCoverUris() {
        viewModelScope.launch(Dispatchers.IO) {
            val allPlaylists = playlistDao.getAllPlaylistsOnce()
            val coverMap = mutableMapOf<Long, String>()
            val currentSongs = _songs.value.associateBy { it.id }

            allPlaylists.forEach { entity ->
                val songIds = entity.songIds.split(",").filter { it.isNotBlank() }.map { it.toLong() }
                val firstSong = songIds.firstNotNullOfOrNull { currentSongs[it] }
                firstSong?.artworkUri?.toString()?.let { coverMap[entity.id] = it }
            }
            _playlistCoverUris.value = coverMap
        }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentIds = playlist.songIds.toMutableList()
            if (!currentIds.contains(song.id)) {
                currentIds.add(song.id)
                val entity = PlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    songIds = currentIds.joinToString(","),
                    createdAt = playlist.createdAt
                )
                playlistDao.updatePlaylist(entity)
                refreshPlaylistCoverUris()
            }
        }
    }

    fun createPlaylistWithSong(name: String, description: String?, song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = PlaylistEntity(
                name = name,
                description = description,
                songIds = song.id.toString(),
                createdAt = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(entity)
            refreshPlaylistCoverUris()
        }
    }
    
    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = PlaylistEntity(
                name = name,
                description = description ?: "",
                songIds = "",
                createdAt = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(entity)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylistById(playlist.id)
            refreshPlaylistCoverUris()
        }
    }

    fun songsForPlaylist(playlist: Playlist): List<Song> {
        val currentSongs = _songs.value.associateBy { it.id }
        return playlist.songIds.mapNotNull { currentSongs[it] }
    }

    fun toggleLike(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val allPlaylists = playlistDao.getAllPlaylistsOnce()
            var likePlaylistEntity = allPlaylists.find { it.name in setOf("Me gusta", "Favorites", getApplication<Application>().getString(R.string.favorites_playlist_name)) }

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
                likePlaylistEntity = updatedPlaylists.find { it.name in setOf("Me gusta", "Favorites", getApplication<Application>().getString(R.string.favorites_playlist_name)) }
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
                refreshPlaylistCoverUris()
            }
        }
    }
    
    fun removeSongFromPlaylist(playlist: Playlist, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentIds = playlist.songIds.toMutableList()
            currentIds.remove(songId)
            updatePlaylistSongs(playlist, currentIds)
        }
    }

    fun moveSongInPlaylist(playlist: Playlist, songId: Long, direction: Int) {
        val currentIds = playlist.songIds.toMutableList()
        val index = currentIds.indexOf(songId)
        if (index == -1) return
        val targetIndex = (index + direction).coerceIn(0, currentIds.lastIndex)
        if (index == targetIndex) return

        currentIds.removeAt(index)
        currentIds.add(targetIndex, songId)

        updatePlaylistSongs(playlist, currentIds)
    }

    fun updatePlaylistSongs(playlist: Playlist, songIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                songIds = songIds.joinToString(","),
                createdAt = playlist.createdAt
            )
            playlistDao.updatePlaylist(entity)
            refreshPlaylistCoverUris()
        }
    }
    
    fun updatePlaylistDetails(playlist: Playlist, name: String, description: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = PlaylistEntity(
                id = playlist.id,
                name = name,
                description = description,
                songIds = playlist.songIds.joinToString(","),
                createdAt = playlist.createdAt
            )
            playlistDao.updatePlaylist(entity)
        }
    }

    fun playlistCoverUri(playlistId: Long): String? = settingsManager.playlistCoverUri(playlistId)

    fun setPlaylistCoverUri(playlistId: Long, uri: String?) {
        settingsManager.setPlaylistCoverUri(playlistId, uri)
    }
    
    fun localLyrics(song: Song): String? = settingsManager.localLyrics(song.id)

    suspend fun getLyricsResult(song: Song): LyricsResult = lyricsRepository.getLyricsResult(song)

    fun saveLocalLyrics(song: Song, lyrics: String) {
        settingsManager.setLocalLyrics(song.id, lyrics)
    }

    fun toggleBlacklist(song: Song) {
        val current = settingsManager.blacklistedSongIds.toMutableSet()
        val id = song.id.toString()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        settingsManager.blacklistedSongIds = current
        loadSongs()
    }

    fun resolveShareUrl(song: Song): String = "https://www.youtube.com/watch?v=${song.data}"

    fun resolveShareUrl(title: String, artist: String): String? = null

    suspend fun getSongImageUrl(song: Song): String? = artworkRepository.getSongImageUrl(song)

    fun getHistoryImageUrl(history: PlaybackHistoryEntity): String? {
        return history.artworkUrl
    }

    suspend fun getArtistImageUrl(artistName: String): String? = artworkRepository.getArtistImageUrl(artistName)

    fun toggleFollowArtist(artistName: String) {
        val current = _followedArtists.value.toMutableSet()
        if (current.contains(artistName)) {
            current.remove(artistName)
        } else {
            current.add(artistName)
        }
        _followedArtists.value = current
        settingsManager.followedArtists = current
    }

    suspend fun getDistinctSongImageUrls(songs: List<Song>, limit: Int = 4): List<String> {
        return songs.distinctBy { it.album }
            .take(limit)
            .mapNotNull { getSongImageUrl(it) }
    }

    fun clearCaches() {
        artworkRepository.clearCache()
    }

    fun removeFromHistory(item: PlaybackHistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            playbackHistoryRepository.removeFromHistory(item)
        }
    }

    override fun onCleared() {
        mediaStoreScanJob?.cancel()
        loadSongsJob?.cancel()
        mediaStoreObserver?.let { observer ->
            getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        }
        mediaStoreObserver = null
        super.onCleared()
    }

    private fun List<Song>.mediaSignature(): List<String> =
        map { "${it.id}:${it.dateAdded}:${it.duration}:${it.uri}" }

}
