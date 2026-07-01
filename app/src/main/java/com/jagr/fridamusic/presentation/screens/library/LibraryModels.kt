package com.jagr.fridamusic.presentation.screens.library

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song

internal enum class LibraryTab {
    ALL,
    HISTORY,
    PLAYLISTS,
    ALBUMS,
    SONGS,
    ARTISTS
}

internal enum class LibrarySortOption {
    TITLE,
    DATE,
    ARTIST,
    ALBUM,
    DURATION,
    SONG_COUNT,
    ALBUM_COUNT
}

internal enum class PlaylistSongSortOption {
    DEFAULT,
    DATE,
    ARTIST,
    TITLE,
    CUSTOM
}

internal data class LibraryAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val representativeSong: Song,
    val newestDateAdded: Long,
    val songCount: Int,
    val songs: List<Song>
)

internal data class LibraryArtist(
    val name: String,
    val songs: List<Song>
) {
    val newestDateAdded: Long = songs.maxOfOrNull { it.dateAdded } ?: 0L
}

internal data class HistorySection(
    val titleRes: Int,
    val items: List<PlaybackHistoryEntity>
)

internal data class ActionSpec(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

internal sealed interface LibraryDetail {
    data class PlaylistDetail(val playlist: Playlist) : LibraryDetail
    data class AlbumDetail(val album: LibraryAlbum) : LibraryDetail
    data class ArtistDetail(val artist: LibraryArtist) : LibraryDetail
    data class SmartSongs(val title: String, val songs: List<Song>) : LibraryDetail
}

internal const val LIBRARY_PAGER_WINDOW = 500
internal val LibraryCardRadius = 18.dp