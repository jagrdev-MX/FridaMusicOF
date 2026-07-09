package com.jagr.fridamusic.presentation.screens.library

import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import java.util.Calendar


internal fun sortOptionsFor(tab: LibraryTab): List<LibrarySortOption> = when (tab) {
    LibraryTab.PLAYLISTS -> listOf(LibrarySortOption.TITLE, LibrarySortOption.SONG_COUNT)
    LibraryTab.ALBUMS -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.SONG_COUNT,
        LibrarySortOption.DATE
    )
    LibraryTab.SONGS -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.ALBUM,
        LibrarySortOption.ARTIST,
        LibrarySortOption.DURATION,
        LibrarySortOption.DATE
    )
    LibraryTab.ARTISTS -> listOf(
        LibrarySortOption.TITLE,
        LibrarySortOption.SONG_COUNT,
        LibrarySortOption.ALBUM_COUNT
    )
    LibraryTab.ALL,
    LibraryTab.HISTORY -> listOf(LibrarySortOption.TITLE, LibrarySortOption.DATE, LibrarySortOption.ARTIST)
}


internal fun defaultSortFor(tab: LibraryTab): LibrarySortOption = when (tab) {
    LibraryTab.PLAYLISTS,
    LibraryTab.ARTISTS -> LibrarySortOption.TITLE
    LibraryTab.ALL,
    LibraryTab.HISTORY,
    LibraryTab.ALBUMS,
    LibraryTab.SONGS -> LibrarySortOption.DATE
}

internal fun List<Song>.sortedPlaylistSongs(sortOption: PlaylistSongSortOption): List<Song> {
    return when (sortOption) {
        PlaylistSongSortOption.DEFAULT,
        PlaylistSongSortOption.CUSTOM -> this
        PlaylistSongSortOption.DATE -> sortedByDescending { it.dateAdded }
        PlaylistSongSortOption.ARTIST -> sortedWith(
            compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
        PlaylistSongSortOption.TITLE -> sortedBy { it.title.lowercase() }
    }
}


internal fun List<Song>.sortedSongs(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<Song> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE -> sortedBy { it.title.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.dateAdded }
        LibrarySortOption.ARTIST -> sortedWith(
            compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
        LibrarySortOption.ALBUM -> sortedWith(
            compareBy<Song> { it.album.lowercase() }.thenBy { it.title.lowercase() }
        )
        LibrarySortOption.DURATION -> sortedBy { it.duration }
        LibrarySortOption.SONG_COUNT,
        LibrarySortOption.ALBUM_COUNT -> sortedBy { it.title.lowercase() }
    }
    return if (reversed) sorted.reversed() else sorted
}


internal fun List<PlaybackHistoryEntity>.sortedHistory(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<PlaybackHistoryEntity> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE -> sortedBy { it.title.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.playedAt }
        LibrarySortOption.ARTIST -> sortedWith(
            compareBy<PlaybackHistoryEntity> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
        LibrarySortOption.ALBUM,
        LibrarySortOption.DURATION,
        LibrarySortOption.SONG_COUNT,
        LibrarySortOption.ALBUM_COUNT -> sortedByDescending { it.playedAt }
    }
    return if (reversed) sorted.reversed() else sorted
}


internal fun List<Playlist>.sortedPlaylists(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<Playlist> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.ALBUM,
        LibrarySortOption.DURATION,
        LibrarySortOption.ALBUM_COUNT -> sortedBy { it.name.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.createdAt }
        LibrarySortOption.SONG_COUNT -> sortedByDescending { it.songIds.size }
    }
    return if (reversed) sorted.reversed() else sorted
}


internal fun List<LibraryAlbum>.sortedAlbums(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<LibraryAlbum> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE -> sortedBy { it.title.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.newestDateAdded }
        LibrarySortOption.ARTIST -> sortedWith(
            compareBy<LibraryAlbum> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
        LibrarySortOption.SONG_COUNT -> sortedByDescending { it.songCount }
        LibrarySortOption.ALBUM,
        LibrarySortOption.DURATION,
        LibrarySortOption.ALBUM_COUNT -> sortedBy { it.title.lowercase() }
    }
    return if (reversed) sorted.reversed() else sorted
}


internal fun List<LibraryArtist>.sortedArtists(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<LibraryArtist> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST,
        LibrarySortOption.ALBUM,
        LibrarySortOption.DURATION -> sortedBy { it.name.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.newestDateAdded }
        LibrarySortOption.SONG_COUNT -> sortedByDescending { it.songs.size }
        LibrarySortOption.ALBUM_COUNT -> sortedByDescending {
                artist -> artist.songs.map { it.albumId }.distinct().size
        }
    }
    return if (reversed) sorted.reversed() else sorted
}


internal fun List<PlaybackHistoryEntity>.toHistorySections(): List<HistorySection> {
    val now = Calendar.getInstance()
    val todayStart = now.startOfDayMillis()
    val yesterdayStart = now.copy().apply { add(Calendar.DAY_OF_YEAR, -1) }.startOfDayMillis()
    val lastWeekStart = now.copy().apply { add(Calendar.DAY_OF_YEAR, -7) }.startOfDayMillis()

    val today = filter { it.playedAt >= todayStart }
    val yesterday = filter { it.playedAt in yesterdayStart until todayStart }
    val lastWeek = filter { it.playedAt in lastWeekStart until yesterdayStart }
    val older = filter { it.playedAt < lastWeekStart }

    return buildList {
        if (today.isNotEmpty()) add(HistorySection(R.string.today, today))
        if (yesterday.isNotEmpty()) add(HistorySection(R.string.yesterday, yesterday))
        if (lastWeek.isNotEmpty()) add(HistorySection(R.string.last_week, lastWeek))
        if (older.isNotEmpty()) add(HistorySection(R.string.older, older))
    }
}


private fun Calendar.copy(): Calendar = clone() as Calendar


private fun Calendar.startOfDayMillis(): Long =
    copy().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis