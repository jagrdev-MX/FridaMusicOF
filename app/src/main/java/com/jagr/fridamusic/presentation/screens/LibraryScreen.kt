package com.jagr.fridamusic.presentation.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlin.math.min

private enum class LibraryTab {
    ALL,
    HISTORY,
    PLAYLISTS,
    SONGS,
    ARTISTS
}

private enum class LibrarySortOption {
    TITLE,
    DATE,
    ARTIST
}

private data class LibraryAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val representativeSong: Song,
    val newestDateAdded: Long,
    val songCount: Int
)

private data class LibraryArtist(
    val name: String,
    val songs: List<Song>
) {
    val newestDateAdded: Long = songs.maxOfOrNull { it.dateAdded } ?: 0L
}

private data class HistorySection(
    val titleRes: Int,
    val items: List<PlaybackHistoryEntity>
)

private data class ActionSpec(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

private const val LIBRARY_PAGER_WINDOW = 10_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    reselectSignal: Int,
    viewModel: LibraryViewModels
) {
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val fullHistory by viewModel.fullHistory.collectAsState()

    val tabs = listOf(
        LibraryTab.ALL to stringResource(R.string.all_tab),
        LibraryTab.HISTORY to stringResource(R.string.history_tab),
        LibraryTab.PLAYLISTS to stringResource(R.string.playlists_tab),
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
    val songsListState = rememberLazyListState()
    val artistsListState = rememberLazyListState()
    val pageStates = listOf(
        allListState,
        historyListState,
        playlistsListState,
        songsListState,
        artistsListState
    )

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
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

    LaunchedEffect(showSortSheet) {
        if (showSortSheet) {
            draftSortName = appliedSortName
            draftReversed = appliedReversed
            draftSaveSort = appliedSaveSort
        }
    }

    LaunchedEffect(reselectSignal) {
        if (reselectSignal > 0) {
            currentListState.animateScrollToItem(0)
        }
    }

    val normalizedQuery = searchQuery.trim()

    val visibleSongs = remember(songs, normalizedQuery, activeSort, appliedReversed) {
        songs
            .filter { song ->
                normalizedQuery.isBlank() ||
                    song.title.contains(normalizedQuery, ignoreCase = true) ||
                    song.artist.contains(normalizedQuery, ignoreCase = true) ||
                    song.album.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedSongs(activeSort, appliedReversed)
    }

    val visibleHistory = remember(fullHistory, normalizedQuery, activeSort, appliedReversed) {
        fullHistory
            .filter { history ->
                normalizedQuery.isBlank() ||
                    history.title.contains(normalizedQuery, ignoreCase = true) ||
                    history.artist.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedHistory(activeSort, appliedReversed)
    }

    val visiblePlaylists = remember(playlists, normalizedQuery, activeSort, appliedReversed) {
        playlists
            .filter { playlist ->
                normalizedQuery.isBlank() ||
                    playlist.name.contains(normalizedQuery, ignoreCase = true) ||
                    playlist.description.orEmpty().contains(normalizedQuery, ignoreCase = true)
            }
            .sortedPlaylists(activeSort, appliedReversed)
    }

    val unknownAlbum = stringResource(R.string.unknown_album)
    val visibleAlbums = remember(songs, normalizedQuery, activeSort, appliedReversed, unknownAlbum) {
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
                    songCount = albumSongs.size
                )
            }
            .filter { album ->
                normalizedQuery.isBlank() ||
                    album.title.contains(normalizedQuery, ignoreCase = true) ||
                    album.artist.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedAlbums(activeSort, appliedReversed)
    }

    val unknownArtist = stringResource(R.string.unknown_artist)
    val visibleArtists = remember(songs, normalizedQuery, activeSort, appliedReversed, unknownArtist) {
        songs
            .groupBy { it.artist.ifBlank { unknownArtist } }
            .map { (artist, artistSongs) -> LibraryArtist(artist, artistSongs) }
            .filter { artist ->
                normalizedQuery.isBlank() ||
                    artist.name.contains(normalizedQuery, ignoreCase = true)
            }
            .sortedArtists(activeSort, appliedReversed)
    }

    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete_playlist_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_playlist_message,
                        playlistToDelete?.name.orEmpty()
                    )
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
                selectedSortName = draftSortName,
                reversed = draftReversed,
                saveSort = draftSaveSort,
                onSortSelected = { draftSortName = it.name },
                onReversedChange = { draftReversed = it },
                onSaveSortChange = { draftSaveSort = it },
                onReset = {
                    draftSortName = LibrarySortOption.DATE.name
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

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (tabs[page.floorMod(tabs.size)].first) {
                LibraryTab.ALL -> AllPage(
                    playlists = visiblePlaylists,
                    songs = visibleSongs,
                    artists = visibleArtists,
                    paddingValues = paddingValues,
                    listState = allListState,
                    headerSpacerHeight = headerSpacerHeight,
                    viewModel = viewModel,
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
                    paddingValues = paddingValues,
                    listState = historyListState,
                    headerSpacerHeight = headerSpacerHeight,
                    viewModel = viewModel,
                    playlists = playlists
                )

                LibraryTab.PLAYLISTS -> PlaylistsPage(
                    playlists = visiblePlaylists,
                    paddingValues = paddingValues,
                    listState = playlistsListState,
                    headerSpacerHeight = headerSpacerHeight,
                    viewModel = viewModel,
                    onDelete = { playlistToDelete = it }
                )

                LibraryTab.SONGS -> SongsPage(
                    songs = visibleSongs,
                    paddingValues = paddingValues,
                    listState = songsListState,
                    headerSpacerHeight = headerSpacerHeight,
                    viewModel = viewModel,
                    playlists = playlists
                )

                LibraryTab.ARTISTS -> ArtistsPage(
                    artists = visibleArtists,
                    paddingValues = paddingValues,
                    listState = artistsListState,
                    headerSpacerHeight = headerSpacerHeight,
                    viewModel = viewModel
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

        ExtendedFloatingActionButton(
            onClick = { showCreateSheet = true },
            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
            text = { Text(stringResource(R.string.create)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = paddingValues.calculateBottomPadding() + 20.dp
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
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
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
    val tabsState = rememberLazyListState()

    LaunchedEffect(selectedPage) {
        tabsState.animateScrollToItem(selectedPage)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp)
    ) {
        Text(
            text = stringResource(R.string.library),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LazyRow(
            state = tabsState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(tabs, key = { it.first.name }) { (tab, title) ->
                val index = tabs.indexOfFirst { it.first == tab }
                val selected = selectedPage == index
                val selectedAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.08f,
                    label = "tab-selection-alpha"
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = selectedAlpha)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            }
                        )
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibraryActionButton(
                icon = Icons.Default.Search,
                selected = searchVisible,
                contentDescription = stringResource(R.string.search)
            ) {
                onToggleSearch()
            }

            Spacer(modifier = Modifier.width(10.dp))

            LibraryActionButton(
                icon = Icons.Default.FilterList,
                selected = sortSheetVisible,
                contentDescription = stringResource(R.string.sort_and_filter)
            ) {
                onOpenSort()
            }
        }

        AnimatedVisibility(visible = searchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_all))
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun AllPage(
    playlists: List<Playlist>,
    songs: List<Song>,
    artists: List<LibraryArtist>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModels,
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
                        onOpen = { onOpenTab(LibraryTab.PLAYLISTS.ordinal) },
                        onPlay = { playlists.firstOrNull()?.let(viewModel::playPlaylist) },
                        onShuffle = {
                            playlists.firstOrNull { it.songIds.isNotEmpty() }?.let {
                                viewModel.playPlaylist(it, shuffle = true)
                            }
                        }
                    )
                }
            }

            if (songs.isNotEmpty()) {
                item {
                    LibraryCollectionCard(
                        title = stringResource(R.string.local_songs),
                        subtitle = stringResource(R.string.songs_count, songs.size),
                        icon = Icons.Default.MusicNote,
                        onOpen = { onOpenTab(LibraryTab.SONGS.ordinal) },
                        onPlay = { songs.firstOrNull()?.let(viewModel::playSong) },
                        onShuffle = { songs.randomOrNull()?.let(viewModel::playSong) }
                    )
                }
            }

            if (artists.isNotEmpty()) {
                item {
                    LibraryCollectionCard(
                        title = stringResource(R.string.artists_tab),
                        subtitle = stringResource(R.string.artists_count, artists.size),
                        icon = Icons.Default.Person,
                        onOpen = { onOpenTab(LibraryTab.ARTISTS.ordinal) },
                        onPlay = { artists.firstOrNull()?.songs?.firstOrNull()?.let(viewModel::playSong) },
                        onShuffle = {
                            artists.flatMap { it.songs }.randomOrNull()?.let(viewModel::playSong)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongsPage(
    songs: List<Song>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModels,
    playlists: List<Playlist>
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
        item { SectionHeader(text = stringResource(R.string.local_songs)) }

        if (songs.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.MusicNote,
                    title = stringResource(R.string.no_local_songs)
                )
            }
        } else {
            items(songs, key = { "local_${it.id}" }) { song ->
                LibrarySongItem(song = song, viewModel = viewModel, playlists = playlists) {
                    viewModel.playSong(song)
                }
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
    viewModel: LibraryViewModels,
    playlists: List<Playlist>
) {
    val sections = remember(history) { history.toHistorySections() }

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

        if (history.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.no_history)
                )
            }
        } else {
            sections.forEach { section ->
                item { SectionHeader(text = stringResource(section.titleRes)) }
                items(section.items, key = { "history_${it.id}" }) { item ->
                    HistorySongItem(
                        item = item,
                        songs = songs,
                        viewModel = viewModel,
                        playlists = playlists,
                        onClick = { viewModel.playHistoryItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsPage(
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModels,
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
        item { SectionHeader(text = stringResource(R.string.playlists_tab)) }

        if (playlists.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(R.string.no_playlists)
                )
            }
        } else {
                items(playlists, key = { "playlist_${it.id}" }) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        onPlay = { viewModel.playPlaylist(playlist) },
                        onShuffle = { viewModel.playPlaylist(playlist, shuffle = true) },
                        onAddToQueue = { viewModel.addPlaylistToQueue(playlist) },
                        onDelete = { onDelete(playlist) }
                    )
                }
        }
    }
}

@Composable
private fun AlbumsPage(
    albums: List<LibraryAlbum>,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModels
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = paddingValues.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeader(text = stringResource(R.string.albums_tab)) }

        if (albums.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.Album,
                    title = stringResource(R.string.no_albums)
                )
            }
        } else {
            items(
                albums.chunked(2),
                key = { row -> row.joinToString("_") { it.id.toString() } }
            ) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { album ->
                        LibraryAlbumCard(
                            album = album,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
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
    viewModel: LibraryViewModels
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
        item { SectionHeader(text = stringResource(R.string.artists_tab)) }

        if (artists.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.no_artists)
                )
            }
        } else {
            items(artists, key = { "artist_${it.name}" }) { artist ->
                ArtistListItem(
                    artist = artist,
                    onPlay = { artist.songs.firstOrNull()?.let(viewModel::playSong) }
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

@Composable
private fun EmptyLibraryState(
    icon: ImageVector,
    title: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun LibraryCollectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(14.dp)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onOpen()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onShuffle()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(12.dp)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.songs_count, playlist.songIds.size),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { showActions = true }) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PlaylistActionsSheet(
                playlist = playlist,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    onPlay()
                },
                onShuffle = {
                    showActions = false
                    onShuffle()
                },
                onAddToQueue = {
                    showActions = false
                    onAddToQueue()
                },
                onDelete = {
                    showActions = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun PlaylistActionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit
) {
    ActionSheetFrame(
        title = playlist.name,
        subtitle = stringResource(R.string.playlist_label),
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.Shuffle, stringResource(R.string.shuffle), onClick = onShuffle),
            ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue),
            ActionSpec(Icons.Default.Delete, stringResource(R.string.delete_playlist), destructive = true, onClick = onDelete)
        )
    )
}

@Composable
private fun SongActionsSheet(
    song: Song,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = song.title,
        subtitle = stringResource(R.string.track),
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next), onClick = onPlayNext),
            ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue),
            ActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist), onClick = onSaveToPlaylist),
            ActionSpec(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like),
                onClick = onToggleLike
            ),
            ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare)
        )
    )
}

@Composable
private fun HistoryActionsSheet(
    item: PlaybackHistoryEntity,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = item.title,
        subtitle = stringResource(R.string.history_tab),
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare)
        )
    )
}

@Composable
private fun ActionSheetFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: List<ActionSpec>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowActions.forEach { action ->
                        ActionTile(
                            icon = action.icon,
                            label = action.label,
                            onClick = action.onClick,
                            destructive = action.destructive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowActions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SaveToPlaylistSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.save_to_playlist),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            EmptyLibraryState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = stringResource(R.string.no_playlists)
            )
        } else {
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(playlist) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.songs_count, playlist.songIds.size),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun LibraryAlbumCard(
    album: LibraryAlbum,
    viewModel: LibraryViewModels,
    modifier: Modifier = Modifier
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = album.representativeSong) {
        value = viewModel.getSongImageUrl(album.representativeSong)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .liquidGlassEffect(16.dp)
            .clickable { viewModel.playSong(album.representativeSong) }
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.8f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                        ),
                        startY = 50f
                    )
                )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                album.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.album_subtitle_format, album.artist, album.songCount),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySongItem(
    song: Song,
    viewModel: LibraryViewModels,
    playlists: List<Playlist>,
    onClick: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }
    val isLiked = remember(playlists, song.id) {
        playlists.any { it.name == "Me gusta" && song.id in it.songIds }
    }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(8.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist.ifBlank { stringResource(R.string.unknown) },
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { showActions = true }) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SongActionsSheet(
                song = song,
                isLiked = isLiked,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    viewModel.playSong(song)
                },
                onPlayNext = {
                    showActions = false
                    viewModel.addSongNext(song)
                },
                onAddToQueue = {
                    showActions = false
                    viewModel.addSongToQueue(song)
                },
                onSaveToPlaylist = {
                    showActions = false
                    showPlaylistPicker = true
                },
                onToggleLike = {
                    showActions = false
                    viewModel.toggleLike(song)
                },
                onShare = {
                    showActions = false
                    shareSong(context, song)
                }
            )
        }
    }

    if (showPlaylistPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SaveToPlaylistSheet(
                playlists = playlists,
                onDismiss = { showPlaylistPicker = false },
                onSelect = { playlist ->
                    viewModel.addSongToPlaylist(playlist, song.id)
                    showPlaylistPicker = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySongItem(
    item: PlaybackHistoryEntity,
    songs: List<Song>,
    viewModel: LibraryViewModels,
    playlists: List<Playlist>,
    onClick: () -> Unit
) {
    val linkedSong = remember(item, songs) {
        songs.firstOrNull { song ->
            song.uri.toString() == item.songId ||
                (song.title == item.title && song.artist == item.artist)
        }
    }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(8.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!item.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.History,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.artist.ifBlank { stringResource(R.string.unknown_artist) },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { showActions = true }) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showActions && linkedSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SongActionsSheet(
                song = linkedSong,
                isLiked = playlists.any { it.name == "Me gusta" && linkedSong.id in it.songIds },
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    viewModel.playSong(linkedSong)
                },
                onPlayNext = {
                    showActions = false
                    viewModel.addSongNext(linkedSong)
                },
                onAddToQueue = {
                    showActions = false
                    viewModel.addSongToQueue(linkedSong)
                },
                onSaveToPlaylist = {
                    showActions = false
                    showPlaylistPicker = true
                },
                onToggleLike = {
                    showActions = false
                    viewModel.toggleLike(linkedSong)
                },
                onShare = {
                    showActions = false
                    shareSong(context, linkedSong)
                }
            )
        }
    } else if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HistoryActionsSheet(
                item = item,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    viewModel.playHistoryItem(item)
                },
                onShare = {
                    showActions = false
                    shareHistoryItem(context, item)
                }
            )
        }
    }

    if (showPlaylistPicker && linkedSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SaveToPlaylistSheet(
                playlists = playlists,
                onDismiss = { showPlaylistPicker = false },
                onSelect = { playlist ->
                    viewModel.addSongToPlaylist(playlist, linkedSong.id)
                    showPlaylistPicker = false
                }
            )
        }
    }
}

@Composable
private fun ArtistListItem(
    artist: LibraryArtist,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(8.dp)
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.songs_count, artist.songs.size),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var playlistName by rememberSaveable { mutableStateOf("") }
    var playlistDescription by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.create_playlist_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.playlist_name)) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
            },
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = playlistDescription,
            onValueChange = { playlistDescription = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.playlist_description)) },
            leadingIcon = {
                Icon(Icons.Default.MusicNote, contentDescription = null)
            },
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                onCreate(
                    playlistName.trim(),
                    playlistDescription.trim().takeIf { it.isNotBlank() }
                )
            },
            enabled = playlistName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.create), fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SortAndFilterSheet(
    selectedSortName: String,
    reversed: Boolean,
    saveSort: Boolean,
    onSortSelected: (LibrarySortOption) -> Unit,
    onReversedChange: (Boolean) -> Unit,
    onSaveSortChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reset))
            }
            Text(
                text = stringResource(R.string.sort_and_filter),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(72.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.reversed),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(checked = reversed, onCheckedChange = onReversedChange)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.sort),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibrarySortOption.entries.forEach { option ->
                FilterChip(
                    selected = selectedSortName == option.name,
                    onClick = { onSortSelected(option) },
                    label = {
                        Text(
                            when (option) {
                                LibrarySortOption.TITLE -> stringResource(R.string.title_label)
                                LibrarySortOption.DATE -> stringResource(R.string.date)
                                LibrarySortOption.ARTIST -> stringResource(R.string.artists_tab)
                            }
                        )
                    },
                    leadingIcon = {
                        if (option == LibrarySortOption.TITLE) {
                            Icon(Icons.Default.SortByAlpha, contentDescription = null)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.save))
            Checkbox(checked = saveSort, onCheckedChange = onSaveSortChange)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.apply), fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

private fun List<PlaybackHistoryEntity>.toHistorySections(): List<HistorySection> {
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

private fun shareSong(context: android.content.Context, song: Song) {
    val text = "${song.title} — ${song.artist}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share))
    )
}

private fun shareHistoryItem(context: android.content.Context, item: PlaybackHistoryEntity) {
    val text = "${item.title} — ${item.artist}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share))
    )
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

private fun Int.nearestPageForTab(targetTab: Int, tabCount: Int): Int {
    val currentTab = floorMod(tabCount)
    val forward = (targetTab - currentTab + tabCount) % tabCount
    val backward = forward - tabCount
    val delta = if (kotlin.math.abs(backward) < kotlin.math.abs(forward)) backward else forward
    return this + delta
}

private fun List<Song>.sortedSongs(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<Song> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE -> sortedBy { it.title.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.dateAdded }
        LibrarySortOption.ARTIST -> sortedWith(
            compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
    }
    return if (reversed) sorted.reversed() else sorted
}

private fun List<PlaybackHistoryEntity>.sortedHistory(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<PlaybackHistoryEntity> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE -> sortedBy { it.title.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.playedAt }
        LibrarySortOption.ARTIST -> sortedWith(
            compareBy<PlaybackHistoryEntity> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
    }
    return if (reversed) sorted.reversed() else sorted
}

private fun List<Playlist>.sortedPlaylists(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<Playlist> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST -> sortedBy { it.name.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.createdAt }
    }
    return if (reversed) sorted.reversed() else sorted
}

private fun List<LibraryAlbum>.sortedAlbums(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<LibraryAlbum> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE -> sortedBy { it.title.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.newestDateAdded }
        LibrarySortOption.ARTIST -> sortedWith(
            compareBy<LibraryAlbum> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        )
    }
    return if (reversed) sorted.reversed() else sorted
}

private fun List<LibraryArtist>.sortedArtists(
    sortOption: LibrarySortOption,
    reversed: Boolean
): List<LibraryArtist> {
    val sorted = when (sortOption) {
        LibrarySortOption.TITLE,
        LibrarySortOption.ARTIST -> sortedBy { it.name.lowercase() }
        LibrarySortOption.DATE -> sortedByDescending { it.newestDateAdded }
    }
    return if (reversed) sorted.reversed() else sorted
}


