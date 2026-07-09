package com.jagr.fridamusic.presentation.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.ModernFilterChip
import com.jagr.fridamusic.presentation.components.ModernLibraryItem
import com.jagr.fridamusic.presentation.components.FridaEmptyState
import com.jagr.fridamusic.presentation.viewmodels.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.min


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    reselectSignal: Int,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    initialSection: String? = null
) {
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val fullHistory by viewModel.fullHistory.collectAsState()
    val playlistCoverUris by viewModel.playlistCoverUris.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val mostPlayedHistory by viewModel.mostPlayedHistory.collectAsState()

    val tabs = listOf(
        LibraryTab.ALL to stringResource(R.string.all_tab),
        LibraryTab.HISTORY to stringResource(R.string.history_tab),
        LibraryTab.PLAYLISTS to stringResource(R.string.playlists_tab),
        LibraryTab.ALBUMS to stringResource(R.string.albums_tab),
        LibraryTab.SONGS to stringResource(R.string.songs_tab),
        LibraryTab.ARTISTS to stringResource(R.string.artists_tab)
    )

    val pagerStartPage = remember {
        val middle = LIBRARY_PAGER_WINDOW / 2
        middle - (middle % tabs.size)
    }
    val pagerState = rememberPagerState(
        initialPage = pagerStartPage,
        pageCount = { LIBRARY_PAGER_WINDOW }
    )
    val scope = rememberCoroutineScope()
    val allListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val songsListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()
    val pageStates = listOf(
        allListState,
        historyListState,
        playlistsListState,
        albumsListState,
        songsListState,
        artistsListState
    )

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var appliedSearchQuery by rememberSaveable { mutableStateOf("") }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LibraryDetail?>(null) }
    var initialSectionConsumed by rememberSaveable(initialSection) { mutableStateOf(false) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val currentLogicalPage = pagerState.currentPage.floorMod(tabs.size)
    val currentListState = pageStates[currentLogicalPage]
    val headerOffsetPx by remember(currentLogicalPage, headerHeightPx) {
        derivedStateOf {
            if (currentListState.firstVisibleItemIndex > 0) {
                headerHeightPx
            } else {
                min(headerHeightPx, currentListState.firstVisibleItemScrollOffset)
            }
        }
    }
    val headerSpacerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }

    val savedSortOption = remember {
        runCatching { LibrarySortOption.valueOf(viewModel.settingsManager.librarySortOption) }
            .getOrDefault(LibrarySortOption.DATE)
    }
    var appliedSortName by rememberSaveable {
        mutableStateOf(
            if (viewModel.settingsManager.saveLibrarySort) {
                savedSortOption.name
            } else {
                LibrarySortOption.DATE.name
            }
        )
    }
    var appliedReversed by rememberSaveable {
        mutableStateOf(
            if (viewModel.settingsManager.saveLibrarySort) {
                viewModel.settingsManager.librarySortReversed
            } else {
                false
            }
        )
    }
    var appliedSaveSort by rememberSaveable {
        mutableStateOf(viewModel.settingsManager.saveLibrarySort)
    }

    var draftSortName by rememberSaveable { mutableStateOf(appliedSortName) }
    var draftReversed by rememberSaveable { mutableStateOf(appliedReversed) }
    var draftSaveSort by rememberSaveable { mutableStateOf(appliedSaveSort) }

    val activeSort = remember(appliedSortName) {
        runCatching { LibrarySortOption.valueOf(appliedSortName) }
            .getOrDefault(LibrarySortOption.DATE)
    }

    LaunchedEffect(showSortSheet, currentLogicalPage) {
        if (showSortSheet) {
            val tab = tabs[currentLogicalPage].first
            val availableSorts = sortOptionsFor(tab)
            draftSortName = appliedSortName.takeIf { saved ->
                availableSorts.any { it.name == saved }
            } ?: defaultSortFor(tab).name
            draftReversed = appliedReversed
            draftSaveSort = appliedSaveSort
        }
    }

    LaunchedEffect(reselectSignal) {
        if (reselectSignal > 0) {
            if (detail != null) {
                detail = null
            } else {
                currentListState.animateScrollToItem(0)
            }
        }
    }

    BackHandler(enabled = detail != null && initialSection == null) {
        detail = null
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            appliedSearchQuery = ""
        } else {
            delay(180)
            appliedSearchQuery = searchQuery.trim()
        }
    }

    val normalizedQuery = appliedSearchQuery

    val visibleSongs by produceState(
        initialValue = emptyList<Song>(),
        songs, normalizedQuery, activeSort, appliedReversed
    ) {
        value = withContext(Dispatchers.Default) {
            songs
                .filter { song ->
                    normalizedQuery.isBlank() ||
                            song.title.contains(normalizedQuery, ignoreCase = true) ||
                            song.artist.contains(normalizedQuery, ignoreCase = true) ||
                            song.album.contains(normalizedQuery, ignoreCase = true)
                }
                .sortedSongs(activeSort, appliedReversed)
        }
    }

    val visibleHistory by produceState(
        initialValue = emptyList<PlaybackHistoryEntity>(),
        fullHistory, normalizedQuery, activeSort, appliedReversed
    ) {
        value = withContext(Dispatchers.Default) {
            fullHistory
                .filter { history ->
                    normalizedQuery.isBlank() ||
                            history.title.contains(normalizedQuery, ignoreCase = true) ||
                            history.artist.contains(normalizedQuery, ignoreCase = true)
                }
                .sortedHistory(activeSort, appliedReversed)
        }
    }

    val visiblePlaylists by produceState(
        initialValue = emptyList<Playlist>(),
        playlists, normalizedQuery, activeSort, appliedReversed
    ) {
        value = withContext(Dispatchers.Default) {
            playlists
                .filter { playlist ->
                    normalizedQuery.isBlank() ||
                            playlist.name.contains(normalizedQuery, ignoreCase = true) ||
                            playlist.description.orEmpty().contains(normalizedQuery, ignoreCase = true)
                }
                .sortedPlaylists(activeSort, appliedReversed)
        }
    }

    val unknownAlbum = stringResource(R.string.unknown_album)
    val visibleAlbums by produceState(
        initialValue = emptyList<LibraryAlbum>(),
        songs, normalizedQuery, activeSort, appliedReversed, unknownAlbum
    ) {
        value = withContext(Dispatchers.Default) {
            songs
                .groupBy { song -> if (song.album.isBlank()) "${song.albumId}" else song.album }
                .map { (_, albumSongs) ->
                    val representative = albumSongs.maxByOrNull { it.dateAdded } ?: albumSongs.first()
                    LibraryAlbum(
                        id = representative.albumId,
                        title = representative.album.ifBlank { unknownAlbum },
                        artist = representative.artist,
                        representativeSong = representative,
                        newestDateAdded = albumSongs.maxOfOrNull { it.dateAdded } ?: 0L,
                        songCount = albumSongs.size,
                        songs = albumSongs.sortedBy { it.title.lowercase() }
                    )
                }
                .filter { album ->
                    normalizedQuery.isBlank() ||
                            album.title.contains(normalizedQuery, ignoreCase = true) ||
                            album.artist.contains(normalizedQuery, ignoreCase = true)
                }
                .sortedAlbums(activeSort, appliedReversed)
        }
    }

    val unknownArtist = stringResource(R.string.unknown_artist)
    val visibleArtists by produceState(
        initialValue = emptyList<LibraryArtist>(),
        songs, normalizedQuery, activeSort, appliedReversed, unknownArtist
    ) {
        value = withContext(Dispatchers.Default) {
            songs
                .groupBy { it.artist.ifBlank { unknownArtist } }
                .map { (artist, artistSongs) -> LibraryArtist(artist, artistSongs) }
                .filter { artist ->
                    normalizedQuery.isBlank() ||
                            artist.name.contains(normalizedQuery, ignoreCase = true)
                }
                .sortedArtists(activeSort, appliedReversed)
        }
    }

    val mostPlayedSongs = remember(mostPlayedHistory, songs) {
        val songsByUri = songs.associateBy { it.uri.toString() }
        val songsByMetadata = songs.associateBy { "${it.title}\u0000${it.artist}" }
        mostPlayedHistory.mapNotNull { item ->
            songsByUri[item.songId] ?: songsByMetadata["${item.title}\u0000${item.artist}"]
        }
    }
    val favoritesName = stringResource(R.string.favorites_playlist_name)
    val mostPlayedTitle = stringResource(R.string.most_played)

    LaunchedEffect(initialSection, playlists, mostPlayedSongs, fullHistory) {
        if (initialSectionConsumed) return@LaunchedEffect
        when (initialSection) {
            "HISTORY", "ALBUMS", "SONGS", "ARTISTS", "PLAYLISTS" -> {
                val target = tabs.indexOfFirst { it.first.name == initialSection }
                if (target >= 0) {
                    pagerState.scrollToPage(pagerState.currentPage.nearestPageForTab(target, tabs.size))
                    initialSectionConsumed = true
                }
            }
            "FAVORITES" -> {
                playlists.firstOrNull {
                    it.name == favoritesName || it.name == "Favorites" || it.name == "Me gusta"
                }?.let {
                    detail = LibraryDetail.PlaylistDetail(it)
                    initialSectionConsumed = true
                }
            }
            "MOST_PLAYED" -> {
                detail = LibraryDetail.SmartSongs(
                    title = mostPlayedTitle,
                    songs = mostPlayedSongs
                )
                if (mostPlayedSongs.isNotEmpty() || fullHistory.isNotEmpty()) {
                    initialSectionConsumed = true
                }
            }
        }
    }

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete_playlist_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_playlist_message,
                        playlistToDelete?.name.orEmpty()
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it) }
                        playlistToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SortAndFilterSheet(
                tab = tabs[currentLogicalPage].first,
                selectedSortName = draftSortName,
                reversed = draftReversed,
                saveSort = draftSaveSort,
                onSortSelected = { draftSortName = it.name },
                onReversedChange = { draftReversed = it },
                onSaveSortChange = { draftSaveSort = it },
                onReset = {
                    draftSortName = defaultSortFor(tabs[currentLogicalPage].first).name
                    draftReversed = false
                    draftSaveSort = false
                },
                onApply = {
                    appliedSortName = draftSortName
                    appliedReversed = draftReversed
                    appliedSaveSort = draftSaveSort

                    viewModel.settingsManager.saveLibrarySort = draftSaveSort
                    if (draftSaveSort) {
                        viewModel.settingsManager.librarySortOption = draftSortName
                        viewModel.settingsManager.librarySortReversed = draftReversed
                    }

                    showSortSheet = false
                }
            )
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CreatePlaylistSheet(
                onDismiss = { showCreateSheet = false },
                onCreate = { name, description ->
                    viewModel.createPlaylist(name, description)
                    showCreateSheet = false
                }
            )
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        if (detail != null) {
            when (val currentDetail = detail) {
                is LibraryDetail.PlaylistDetail -> {
                    val currentPlaylist = playlists.firstOrNull { it.id == currentDetail.playlist.id }
                        ?: currentDetail.playlist
                    PlaylistDetailPage(
                        playlist = currentPlaylist,
                        songs = viewModel.songsForPlaylist(currentPlaylist),
                        playlists = playlists,
                        customCoverUri = playlistCoverUris[currentPlaylist.id],
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        onBack = { detail = null }
                    )
                }

                is LibraryDetail.AlbumDetail -> AlbumDetailPage(
                    album = currentDetail.album,
                    playlists = playlists,
                    paddingValues = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + navBarPadding
                    ),
                    viewModel = viewModel,
                    playbackViewModel = playbackViewModel,
                    onBack = { detail = null },
                    onOpenArtist = visibleArtists
                        .firstOrNull { it.name.equals(currentDetail.album.artist, ignoreCase = true) }
                        ?.let { artist -> { detail = LibraryDetail.ArtistDetail(artist) } }
                )

                is LibraryDetail.ArtistDetail -> ArtistDetailPage(
                    artist = currentDetail.artist,
                    allArtists = visibleArtists,
                    playlists = playlists,
                    isFollowed = currentDetail.artist.name in followedArtists,
                    paddingValues = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + navBarPadding
                    ),
                    viewModel = viewModel,
                    playbackViewModel = playbackViewModel,
                    onBack = { detail = null },
                    onOpenAlbum = { detail = LibraryDetail.AlbumDetail(it) },
                    onOpenArtist = { detail = LibraryDetail.ArtistDetail(it) }
                )

                is LibraryDetail.SmartSongs -> SmartSongsDetailPage(
                    title = currentDetail.title,
                    songs = currentDetail.songs,
                    playlists = playlists,
                    paddingValues = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding() + navBarPadding
                    ),
                    viewModel = viewModel,
                    playbackViewModel = playbackViewModel,
                    onBack = { detail = null }
                )

                null -> Unit
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (tabs[page.floorMod(tabs.size)].first) {
                    LibraryTab.ALL -> AllPage(
                        playlists = visiblePlaylists,
                        songs = visibleSongs,
                        artists = visibleArtists,
                        playlistCoverUris = playlistCoverUris,
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        listState = allListState,
                        headerSpacerHeight = headerSpacerHeight,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        onOpenTab = { target ->
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage.nearestPageForTab(target, tabs.size)
                                )
                            }
                        },
                        onDelete = { playlistToDelete = it }
                    )

                    LibraryTab.HISTORY -> HistoryPage(
                        history = visibleHistory,
                        songs = songs,
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        listState = historyListState,
                        headerSpacerHeight = headerSpacerHeight,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        playlists = playlists
                    )

                    LibraryTab.PLAYLISTS -> PlaylistsPage(
                        playlists = visiblePlaylists,
                        songs = songs,
                        playlistCoverUris = playlistCoverUris,
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        listState = playlistsListState,
                        headerSpacerHeight = headerSpacerHeight,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        onOpenPlaylist = { detail = LibraryDetail.PlaylistDetail(it) },
                        onOpenHistory = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage.nearestPageForTab(
                                        tabs.indexOfFirst { it.first == LibraryTab.HISTORY },
                                        tabs.size
                                    )
                                )
                            }
                        },
                        onDelete = { playlistToDelete = it }
                    )

                    LibraryTab.ALBUMS -> AlbumsPage(
                        albums = visibleAlbums,
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        listState = albumsListState,
                        headerSpacerHeight = headerSpacerHeight,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        onOpenAlbum = { detail = LibraryDetail.AlbumDetail(it) }
                    )

                    LibraryTab.SONGS -> SongsPage(
                        songs = visibleSongs,
                        albums = visibleAlbums,
                        artists = visibleArtists,
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        listState = songsListState,
                        headerSpacerHeight = headerSpacerHeight,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        playlists = playlists,
                        onOpenAlbum = { detail = LibraryDetail.AlbumDetail(it) },
                        onOpenArtist = { detail = LibraryDetail.ArtistDetail(it) }
                    )

                    LibraryTab.ARTISTS -> ArtistsPage(
                        artists = visibleArtists,
                        paddingValues = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + navBarPadding
                        ),
                        listState = artistsListState,
                        headerSpacerHeight = headerSpacerHeight,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        onOpenArtist = { detail = LibraryDetail.ArtistDetail(it) }
                    )
                }
            }

            LibraryHeader(
                tabs = tabs,
                selectedPage = currentLogicalPage,
                searchVisible = searchVisible,
                searchQuery = searchQuery,
                sortSheetVisible = showSortSheet,
                onTabSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage.nearestPageForTab(index, tabs.size)
                        )
                    }
                },
                onToggleSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) searchQuery = ""
                },
                onSearchQueryChange = { searchQuery = it },
                onOpenSort = { showSortSheet = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, -headerOffsetPx) }
                    .onGloballyPositioned { headerHeightPx = it.size.height }
            )
        }

        if (detail == null) ExtendedFloatingActionButton(
            onClick = { showCreateSheet = true },
            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
            text = { Text(stringResource(R.string.create)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = paddingValues.calculateBottomPadding() + navBarPadding + 20.dp
                )
        )
    }
}


@Composable
private fun LibraryActionButton(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}


@Composable
private fun LibraryHeader(
    tabs: List<Pair<LibraryTab, String>>,
    selectedPage: Int,
    searchVisible: Boolean,
    searchQuery: String,
    sortSheetVisible: Boolean,
    onTabSelected: (Int) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = 8.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Menu */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }

            Text(
                text = "Frida Music",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = { onToggleSearch() }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(tabs.size) { index ->
                val (tab, title) = tabs[index]
                ModernFilterChip(
                    label = title,
                    isSelected = selectedPage == index,
                    onClick = { onTabSelected(index) }
                )
            }
        }

        if (searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}


@Composable
private fun AllPage(
    playlists: List<Playlist>,
    songs: List<Song>,
    artists: List<LibraryArtist>,
    playlistCoverUris: Map<Long, String>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onOpenTab: (Int) -> Unit,
    onDelete: (Playlist) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = paddingValues.calculateBottomPadding() + 140.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-spacer") { Spacer(modifier = Modifier.height(headerSpacerHeight)) }

        if (playlists.isEmpty() && songs.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.no_local_songs)
                )
            }
        } else {
            if (playlists.isNotEmpty()) {
                item {
                    LibraryCollectionCard(
                        title = stringResource(R.string.playlists_tab),
                        subtitle = stringResource(R.string.playlists_count, playlists.size),
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        viewModel = viewModel,
                        coverSongs = playlists.flatMap { playlist ->
                            viewModel.songsForPlaylist(playlist)
                        }.distinctBy { it.id },
                        customCoverUri = playlists.firstNotNullOfOrNull { playlistCoverUris[it.id] },
                        onOpen = { onOpenTab(LibraryTab.PLAYLISTS.ordinal) },
                        onPlay = {
                            playlists.firstOrNull()?.let {
                                playbackViewModel.playPlaylist(it, viewModel.songsForPlaylist(it))
                            }
                        },
                        onShuffle = {
                            playlists.firstOrNull { it.songIds.isNotEmpty() }?.let {
                                playbackViewModel.playPlaylist(it, viewModel.songsForPlaylist(it), shuffle = true)
                            }
                        }
                    )
                }
            }

            if (songs.isNotEmpty()) {
                item {
                    LibraryCollectionCard(
                        title = stringResource(R.string.local_songs),
                        subtitle = pluralStringResource(R.plurals.library_songs_count, songs.size, songs.size),
                        icon = Icons.Default.MusicNote,
                        viewModel = viewModel,
                        coverSongs = songs,
                        onOpen = { onOpenTab(LibraryTab.SONGS.ordinal) },
                        onPlay = { playbackViewModel.playSongs(songs) },
                        onShuffle = { playbackViewModel.playSongs(songs, shuffle = true) }
                    )
                }
            }

            if (artists.isNotEmpty()) {
                item {
                    LibraryCollectionCard(
                        title = stringResource(R.string.artists_tab),
                        subtitle = stringResource(R.string.artists_count, artists.size),
                        icon = Icons.Default.Person,
                        viewModel = viewModel,
                        coverSongs = artists.mapNotNull { it.songs.firstOrNull() },
                        onOpen = { onOpenTab(LibraryTab.ARTISTS.ordinal) },
                        onPlay = { playbackViewModel.playSongs(artists.flatMap { it.songs }) },
                        onShuffle = { playbackViewModel.playSongs(artists.flatMap { it.songs }, shuffle = true) }
                    )
                }
            }
        }
    }
}


@Composable
private fun SongsPage(
    songs: List<Song>,
    albums: List<LibraryAlbum>,
    artists: List<LibraryArtist>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    playlists: List<Playlist>,
    onOpenAlbum: (LibraryAlbum) -> Unit,
    onOpenArtist: (LibraryArtist) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-spacer") { Spacer(modifier = Modifier.height(headerSpacerHeight)) }

        if (songs.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.no_local_songs)
                )
            }
        } else {
            items(
                items = songs,
                key = { "local_${it.id}" },
                contentType = { "song_row" }
            ) { song ->
                val artworkUrl by produceState<String?>(initialValue = null, song) {
                    value = viewModel.getSongImageUrl(song)
                }
                ModernLibraryItem(
                    title = song.title,
                    subtitle = song.artist,
                    imageUrl = artworkUrl,
                    icon = Icons.Default.Folder, // Placeholder icon
                    onClick = { playbackViewModel.playSong(song) }
                )
            }
        }
    }
}


@Composable
private fun HistoryPage(
    history: List<PlaybackHistoryEntity>,
    songs: List<Song>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    playlists: List<Playlist>
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-spacer") { Spacer(modifier = Modifier.height(headerSpacerHeight)) }

        if (history.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.no_history)
                )
            }
        } else {
            items(
                items = history,
                key = { "history_${it.id}" },
                contentType = { "history_row" }
            ) { item ->
                ModernLibraryItem(
                    title = item.title,
                    subtitle = item.artist,
                    imageUrl = item.artworkUrl,
                    icon = Icons.Default.History,
                    onClick = { playbackViewModel.playHistoryItem(item, songs) }
                )
            }
        }
    }
}


@Composable
private fun PlaylistsPage(
    playlists: List<Playlist>,
    songs: List<Song>,
    playlistCoverUris: Map<Long, String>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenHistory: () -> Unit,
    onDelete: (Playlist) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-spacer") { Spacer(modifier = Modifier.height(headerSpacerHeight)) }

        if (playlists.isEmpty()) {
            item { FridaEmptyState(title = stringResource(R.string.no_playlists)) }
        } else {
            items(
                items = playlists,
                key = { it.id },
                contentType = { "playlist_row" }
            ) { playlist ->
                val playlistSongs = remember(playlist, songs) {
                    viewModel.songsForPlaylist(playlist)
                }
                val artworkUrl by produceState<String?>(initialValue = playlistCoverUris[playlist.id], playlist.id) {
                    value = playlistCoverUris[playlist.id] ?: playlistSongs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
                }
                ModernLibraryItem(
                    title = playlist.name,
                    subtitle = pluralStringResource(R.plurals.library_songs_count, playlist.songIds.size, playlist.songIds.size),
                    imageUrl = artworkUrl,
                    icon = Icons.Default.Favorite, // Placeholder icon
                    onClick = { onOpenPlaylist(playlist) }
                )
            }
        }
    }
}


@Composable
private fun AlbumsPage(
    albums: List<LibraryAlbum>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onOpenAlbum: (LibraryAlbum) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-spacer") { Spacer(modifier = Modifier.height(headerSpacerHeight)) }

        if (albums.isEmpty()) {
            item { FridaEmptyState(title = stringResource(R.string.no_albums)) }
        } else {
            items(
                items = albums,
                key = { it.id },
                contentType = { "album_row" }
            ) { album ->
                val artworkUrl by produceState<String?>(initialValue = null, album.id) {
                    value = viewModel.getSongImageUrl(album.representativeSong)
                }
                ModernLibraryItem(
                    title = album.title,
                    subtitle = album.artist,
                    imageUrl = artworkUrl,
                    icon = Icons.Default.Album,
                    onClick = { onOpenAlbum(album) }
                )
            }
        }
    }
}


@Composable
private fun ArtistsPage(
    artists: List<LibraryArtist>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onOpenArtist: (LibraryArtist) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-spacer") { Spacer(modifier = Modifier.height(headerSpacerHeight)) }

        if (artists.isEmpty()) {
            item { FridaEmptyState(title = stringResource(R.string.no_artists)) }
        } else {
            items(
                items = artists,
                key = { it.name },
                contentType = { "artist_row" }
            ) { artist ->
                val artworkUrl by produceState<String?>(initialValue = null, artist.name) {
                    value = viewModel.getArtistImageUrl(artist.name) ?: artist.songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
                }
                ModernLibraryItem(
                    title = artist.name,
                    subtitle = pluralStringResource(R.plurals.library_songs_count, artist.songs.size, artist.songs.size),
                    imageUrl = artworkUrl,
                    icon = Icons.Default.Person,
                    onClick = { onOpenArtist(artist) }
                )
            }
        }
    }
}


@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
