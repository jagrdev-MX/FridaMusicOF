package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.draw.blur
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
import com.jagr.fridamusic.presentation.components.rememberMiniPlayerArtworkPalette
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

private enum class PlaylistSongSortOption {
    DEFAULT,
    DATE,
    ARTIST,
    TITLE,
    CUSTOM
}

private data class LibraryAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val representativeSong: Song,
    val newestDateAdded: Long,
    val songCount: Int,
    val songs: List<Song>
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

private sealed interface LibraryDetail {
    data class PlaylistDetail(val playlist: Playlist) : LibraryDetail
    data class AlbumDetail(val album: LibraryAlbum) : LibraryDetail
    data class ArtistDetail(val artist: LibraryArtist) : LibraryDetail
}

private const val LIBRARY_PAGER_WINDOW = 10_000
private val LibraryCardRadius = 18.dp
private val LibraryArtworkRadius = 12.dp

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
    val playlistCoverUris by viewModel.playlistCoverUris.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()

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
    var detail by remember { mutableStateOf<LibraryDetail?>(null) }
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
            if (detail != null) {
                detail = null
            } else {
                currentListState.animateScrollToItem(0)
            }
        }
    }

    BackHandler(enabled = detail != null) {
        detail = null
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
                        paddingValues = paddingValues,
                        viewModel = viewModel,
                        onBack = { detail = null }
                    )
                }

                is LibraryDetail.AlbumDetail -> AlbumDetailPage(
                    album = currentDetail.album,
                    playlists = playlists,
                    paddingValues = paddingValues,
                    viewModel = viewModel,
                    onBack = { detail = null }
                )

                is LibraryDetail.ArtistDetail -> ArtistDetailPage(
                    artist = currentDetail.artist,
                    allArtists = visibleArtists,
                    playlists = playlists,
                    isFollowed = currentDetail.artist.name in followedArtists,
                    paddingValues = paddingValues,
                    viewModel = viewModel,
                    onBack = { detail = null },
                    onOpenAlbum = { detail = LibraryDetail.AlbumDetail(it) },
                    onOpenArtist = { detail = LibraryDetail.ArtistDetail(it) }
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
                    songs = songs,
                    playlistCoverUris = playlistCoverUris,
                    paddingValues = paddingValues,
                    listState = playlistsListState,
                    headerSpacerHeight = headerSpacerHeight,
                    viewModel = viewModel,
                    onOpenPlaylist = { detail = LibraryDetail.PlaylistDetail(it) },
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
                    viewModel = viewModel,
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
    val tabsState = rememberLazyListState()

    LaunchedEffect(selectedPage) {
        tabsState.animateScrollToItem(selectedPage)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp
            )
    ) {
        Text(
            text = stringResource(R.string.library),
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(4.dp)
        ) {
            LazyRow(
                state = tabsState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tabs, key = { it.first.name }) { (tab, title) ->
                    val index = tabs.indexOfFirst { it.first == tab }
                    val selected = selectedPage == index
                    val selectedAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        label = "tab-selection-alpha"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = selectedAlpha)
                            )
                            .clickable { onTabSelected(index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        }

        Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.width(8.dp))

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
                    .padding(top = 12.dp),
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

        Spacer(modifier = Modifier.height(14.dp))
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
                        viewModel = viewModel,
                        coverSongs = playlists.flatMap { playlist ->
                            viewModel.songsForPlaylist(playlist)
                        }.distinctBy { it.id },
                        customCoverUri = playlists.firstNotNullOfOrNull { playlistCoverUris[it.id] },
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
                        viewModel = viewModel,
                        coverSongs = songs,
                        onOpen = { onOpenTab(LibraryTab.SONGS.ordinal) },
                        onPlay = { viewModel.playSongs(songs) },
                        onShuffle = { viewModel.playSongs(songs, shuffle = true) }
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
                        onPlay = { viewModel.playSongs(artists.flatMap { it.songs }) },
                        onShuffle = { viewModel.playSongs(artists.flatMap { it.songs }, shuffle = true) }
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
                LibrarySongItem(
                    song = song,
                    viewModel = viewModel,
                    playlists = playlists,
                    onClick = { viewModel.playSong(song) }
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
    songs: List<Song>,
    playlistCoverUris: Map<Long, String>,
    paddingValues: PaddingValues,
    listState: LazyListState,
    headerSpacerHeight: Dp,
    viewModel: LibraryViewModels,
    onOpenPlaylist: (Playlist) -> Unit,
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
                        songs = viewModel.songsForPlaylist(playlist),
                        customCoverUri = playlistCoverUris[playlist.id],
                        viewModel = viewModel,
                        onOpen = { onOpenPlaylist(playlist) },
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
    viewModel: LibraryViewModels,
    onOpenAlbum: (LibraryAlbum) -> Unit
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
                            onOpen = { onOpenAlbum(album) },
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
    viewModel: LibraryViewModels,
    onOpenArtist: (LibraryArtist) -> Unit
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
                    viewModel = viewModel,
                    onOpen = { onOpenArtist(artist) },
                    onPlay = { viewModel.playSongs(artist.songs) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailPage(
    playlist: Playlist,
    songs: List<Song>,
    playlists: List<Playlist>,
    customCoverUri: String?,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModels,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var songSortName by rememberSaveable(playlist.id) {
        mutableStateOf(PlaylistSongSortOption.DEFAULT.name)
    }
    val songSortOption = remember(songSortName) {
        runCatching { PlaylistSongSortOption.valueOf(songSortName) }
            .getOrDefault(PlaylistSongSortOption.DEFAULT)
    }
    val displayedSongs = remember(songs, songSortOption) {
        songs.sortedPlaylistSongs(songSortOption)
    }
    val leadArtworkUrl by rememberLeadArtworkUrl(
        songs = displayedSongs,
        customCoverUri = customCoverUri,
        viewModel = viewModel
    )
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setPlaylistCoverUri(playlist.id, uri.toString())
        }
    }

    DetailPageShell(
        title = playlist.name,
        subtitle = stringResource(R.string.playlist_label),
        description = playlist.description.orEmpty(),
        cover = {
            SmartCollectionCover(
                songs = displayedSongs,
                customCoverUri = customCoverUri,
                viewModel = viewModel,
                shape = RoundedCornerShape(24.dp),
                fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic
            )
        },
        countLabel = stringResource(R.string.songs_count, displayedSongs.size),
        backgroundArtUrl = leadArtworkUrl,
        onBack = onBack,
        onMore = { showActions = true },
        onPlay = { viewModel.playSongs(displayedSongs) },
        onShuffle = { viewModel.playSongs(displayedSongs, shuffle = true) },
        secondaryActions = {
            DetailChipButton(
                icon = Icons.Default.Radio,
                label = stringResource(R.string.radio),
                onClick = { viewModel.playSongs(displayedSongs, shuffle = true) }
            )
            DetailIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = { sharePlaylist(context, playlist, displayedSongs) }
            )
        },
        paddingValues = paddingValues
    ) {
        if (displayedSongs.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(R.string.no_local_songs)
                )
            }
        } else {
            item {
                PlaylistSongSortSelector(
                    selected = songSortOption,
                    onSelected = { option -> songSortName = option.name }
                )
            }
            itemsIndexed(displayedSongs, key = { _, song -> "playlist_detail_${playlist.id}_${song.id}" }) { index, song ->
                LibrarySongItem(
                    song = song,
                    viewModel = viewModel,
                    playlists = playlists,
                    onClick = { viewModel.playSongFromLibrary(song) },
                    playlist = playlist,
                    canMoveUp = songSortOption == PlaylistSongSortOption.CUSTOM && index > 0,
                    canMoveDown = songSortOption == PlaylistSongSortOption.CUSTOM && index < displayedSongs.lastIndex,
                    onRemoveFromPlaylist = {
                        viewModel.removeSongFromPlaylist(playlist, song.id)
                    },
                    onMoveUp = {
                        viewModel.moveSongInPlaylist(playlist, song.id, -1)
                    },
                    onMoveDown = {
                        viewModel.moveSongInPlaylist(playlist, song.id, 1)
                    }
                )
            }
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
                onEdit = {
                    showActions = false
                    showEditSheet = true
                },
                onPlay = {
                    showActions = false
                    viewModel.playSongs(displayedSongs)
                },
                onShuffle = {
                    showActions = false
                    viewModel.playSongs(displayedSongs, shuffle = true)
                },
                onAddToQueue = {
                    showActions = false
                    viewModel.addSongsToQueue(displayedSongs)
                },
                onShare = {
                    showActions = false
                    sharePlaylist(context, playlist, displayedSongs)
                },
                onChangeCover = {
                    showActions = false
                    coverPicker.launch(arrayOf("image/*"))
                },
                onRemoveCover = if (customCoverUri != null) {
                    {
                        showActions = false
                        viewModel.setPlaylistCoverUri(playlist.id, null)
                    }
                } else {
                    null
                },
                onDelete = {
                    showActions = false
                    viewModel.deletePlaylist(playlist)
                    onBack()
                }
            )
        }
    }

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EditPlaylistSheet(
                playlist = playlist,
                onDismiss = { showEditSheet = false },
                onSave = { name, description ->
                    viewModel.updatePlaylistDetails(playlist, name, description)
                    showEditSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailPage(
    album: LibraryAlbum,
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModels,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showActions by rememberSaveable { mutableStateOf(false) }
    val leadArtworkUrl by rememberLeadArtworkUrl(
        songs = listOf(album.representativeSong),
        customCoverUri = null,
        viewModel = viewModel
    )

    DetailPageShell(
        title = album.title,
        subtitle = album.artist,
        description = "",
        cover = {
            DetailSongCover(
                song = album.representativeSong,
                viewModel = viewModel,
                shape = RoundedCornerShape(24.dp)
            )
        },
        countLabel = stringResource(R.string.songs_count, album.songCount),
        backgroundArtUrl = leadArtworkUrl,
        onBack = onBack,
        onMore = { showActions = true },
        onPlay = { viewModel.playSongs(album.songs) },
        onShuffle = { viewModel.playSongs(album.songs, shuffle = true) },
        secondaryActions = {
            DetailChipButton(
                icon = Icons.Default.Radio,
                label = stringResource(R.string.radio),
                onClick = { viewModel.playSongs(album.songs, shuffle = true) }
            )
            DetailIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = { shareAlbum(context, album) }
            )
        },
        paddingValues = paddingValues
    ) {
        items(album.songs, key = { "album_detail_${it.id}" }) { song ->
            LibrarySongItem(
                song = song,
                viewModel = viewModel,
                playlists = playlists,
                onClick = { viewModel.playSong(song) }
            )
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CollectionActionsSheet(
                title = album.title,
                subtitle = album.artist,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    viewModel.playSongs(album.songs)
                },
                onShuffle = {
                    showActions = false
                    viewModel.playSongs(album.songs, shuffle = true)
                },
                onAddToQueue = {
                    showActions = false
                    viewModel.addSongsToQueue(album.songs)
                },
                onShare = {
                    showActions = false
                    shareAlbum(context, album)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistDetailPage(
    artist: LibraryArtist,
    allArtists: List<LibraryArtist>,
    playlists: List<Playlist>,
    isFollowed: Boolean,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModels,
    onBack: () -> Unit,
    onOpenAlbum: (LibraryAlbum) -> Unit,
    onOpenArtist: (LibraryArtist) -> Unit
) {
    val context = LocalContext.current
    var showActions by rememberSaveable { mutableStateOf(false) }
    val artistAlbums = remember(artist) {
        artist.songs
            .groupBy { song -> song.album.ifBlank { "${song.albumId}" } }
            .map { (_, albumSongs) ->
                val representative = albumSongs.maxByOrNull { it.dateAdded } ?: albumSongs.first()
                LibraryAlbum(
                    id = representative.albumId,
                    title = representative.album.ifBlank { representative.title },
                    artist = artist.name,
                    representativeSong = representative,
                    newestDateAdded = albumSongs.maxOfOrNull { it.dateAdded } ?: 0L,
                    songCount = albumSongs.size,
                    songs = albumSongs.sortedBy { it.title.lowercase() }
                )
            }
            .sortedByDescending { it.newestDateAdded }
    }
    val leadArtworkUrl by rememberLeadArtworkUrl(
        songs = artist.songs.take(1),
        customCoverUri = null,
        viewModel = viewModel
    )
    val topSongs = remember(artist) {
        artist.songs.sortedByDescending { it.dateAdded }.take(8)
    }
    val singlesAndEps = remember(artistAlbums) {
        artistAlbums.filter { it.songCount <= 4 }.ifEmpty { artistAlbums }.take(8)
    }
    val fromYourLibrary = remember(artist) {
        artist.songs.take(8)
    }
    val fansMightAlsoLike = remember(allArtists, artist.name) {
        allArtists
            .filterNot { it.name == artist.name }
            .sortedByDescending { it.newestDateAdded }
            .take(8)
    }

    DetailPageShell(
        title = artist.name,
        subtitle = stringResource(R.string.artist),
        description = "",
        cover = {
            DetailSongCover(
                song = artist.songs.firstOrNull(),
                viewModel = viewModel,
                shape = CircleShape
            )
        },
        countLabel = stringResource(R.string.songs_count, artist.songs.size),
        backgroundArtUrl = leadArtworkUrl,
        onBack = onBack,
        onMore = { showActions = true },
        onPlay = { viewModel.playSongs(artist.songs) },
        onShuffle = { viewModel.playSongs(artist.songs, shuffle = true) },
        secondaryActions = {
            DetailChipButton(
                icon = if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
                label = if (isFollowed) stringResource(R.string.unfollow) else stringResource(R.string.follow),
                onClick = { viewModel.toggleFollowArtist(artist.name) }
            )
            DetailIconButton(
                icon = Icons.Default.Radio,
                contentDescription = stringResource(R.string.radio),
                onClick = { viewModel.playSongs(artist.songs, shuffle = true) }
            )
            DetailIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = { shareArtist(context, artist) }
            )
        },
        paddingValues = paddingValues
    ) {
        if (topSongs.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 0) {
                    ArtistSongShelf(
                        title = stringResource(R.string.top_songs),
                        songs = topSongs,
                        viewModel = viewModel,
                        playlists = playlists
                    )
                }
            }
        }

        if (singlesAndEps.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 1) {
                    ArtistAlbumShelf(
                        title = stringResource(R.string.singles_and_eps),
                        albums = singlesAndEps,
                        viewModel = viewModel,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
        }

        if (fromYourLibrary.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 2) {
                    ArtistSongShelf(
                        title = stringResource(R.string.from_your_library),
                        songs = fromYourLibrary,
                        viewModel = viewModel,
                        playlists = playlists
                    )
                }
            }
        }

        if (fansMightAlsoLike.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 3) {
                    RelatedArtistsShelf(
                        artists = fansMightAlsoLike,
                        viewModel = viewModel,
                        onOpenArtist = onOpenArtist
                    )
                }
            }
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ArtistActionsSheet(
                artist = artist,
                isFollowed = isFollowed,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    viewModel.playSongs(artist.songs)
                },
                onRadio = {
                    showActions = false
                    viewModel.playSongs(artist.songs, shuffle = true)
                },
                onToggleFollow = {
                    showActions = false
                    viewModel.toggleFollowArtist(artist.name)
                },
                onShare = {
                    showActions = false
                    shareArtist(context, artist)
                }
            )
        }
    }
}

@Composable
private fun DetailPageShell(
    title: String,
    subtitle: String,
    description: String,
    cover: @Composable () -> Unit,
    countLabel: String,
    backgroundArtUrl: String?,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    secondaryActions: @Composable RowScope.() -> Unit,
    paddingValues: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val palette by rememberMiniPlayerArtworkPalette(backgroundArtUrl)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.glowStart.copy(alpha = 0.22f),
                        palette.glowEnd.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        if (!backgroundArtUrl.isNullOrBlank()) {
            AsyncImage(
                model = backgroundArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp)
                    .alpha(0.18f)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = paddingValues.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
            }
        }

        item { cover() }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = secondaryActions
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = countLabel,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.play))
                }
                TextButton(onClick = onShuffle) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.shuffle))
                }
            }
        }

        content()
    }
    }
}

@Composable
private fun DetailCoverPlaceholder(
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(84.dp)
        )
    }
}

@Composable
private fun rememberLeadArtworkUrl(
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModels
) = produceState<String?>(
    initialValue = customCoverUri,
    key1 = songs,
    key2 = customCoverUri
) {
    value = customCoverUri ?: songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
}

@Composable
private fun SmartCollectionCover(
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModels,
    shape: androidx.compose.ui.graphics.Shape,
    fallbackIcon: ImageVector
) {
    val coverModels by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = songs,
        key2 = customCoverUri
    ) {
        value = if (!customCoverUri.isNullOrBlank()) {
            listOf(customCoverUri)
        } else {
            viewModel.getDistinctSongImageUrls(songs)
        }
    }

    when {
        coverModels.size == 1 -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = coverModels.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        coverModels.size >= 2 -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
            ) {
                coverModels.chunked(2).take(2).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { model ->
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        if (row.size == 1) {
                            DetailCoverPlaceholderCell(
                                icon = fallbackIcon,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (coverModels.size < 3) {
                    Row(Modifier.weight(1f)) {
                        repeat(2) {
                            DetailCoverPlaceholderCell(
                                icon = fallbackIcon,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        else -> DetailCoverPlaceholder(icon = fallbackIcon)
    }
}

@Composable
private fun DetailCoverPlaceholderCell(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun SmartCollectionThumbnail(
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModels,
    fallbackIcon: ImageVector
) {
    val coverModels by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = songs,
        key2 = customCoverUri
    ) {
        value = if (!customCoverUri.isNullOrBlank()) {
            listOf(customCoverUri)
        } else {
            viewModel.getDistinctSongImageUrls(songs)
        }
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(LibraryArtworkRadius))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            coverModels.size == 1 -> {
                AsyncImage(
                    model = coverModels.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            coverModels.size >= 2 -> {
                Column(Modifier.fillMaxSize()) {
                    coverModels.chunked(2).take(2).forEach { row ->
                        Row(Modifier.weight(1f)) {
                            row.forEach { model ->
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            }
                            if (row.size == 1) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                )
                            }
                        }
                    }
                    if (coverModels.size < 3) {
                        Row(Modifier.weight(1f)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DetailSongCover(
    song: Song?,
    viewModel: LibraryViewModels,
    shape: androidx.compose.ui.graphics.Shape
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = song?.let { viewModel.getSongImageUrl(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(84.dp)
            )
        }
    }
}

@Composable
private fun DetailChipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun DetailIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun PlaylistSongSortSelector(
    selected: PlaylistSongSortOption,
    onSelected: (PlaylistSongSortOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.playlist_song_order),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlaylistSongSortOption.entries, key = { it.name }) { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            when (option) {
                                PlaylistSongSortOption.DEFAULT -> stringResource(R.string.default_order)
                                PlaylistSongSortOption.DATE -> stringResource(R.string.date)
                                PlaylistSongSortOption.ARTIST -> stringResource(R.string.artists_tab)
                                PlaylistSongSortOption.TITLE -> stringResource(R.string.title_label)
                                PlaylistSongSortOption.CUSTOM -> stringResource(R.string.custom_order)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (option) {
                                PlaylistSongSortOption.DEFAULT -> Icons.Default.Refresh
                                PlaylistSongSortOption.DATE -> Icons.Default.History
                                PlaylistSongSortOption.ARTIST -> Icons.Default.Person
                                PlaylistSongSortOption.TITLE -> Icons.Default.SortByAlpha
                                PlaylistSongSortOption.CUSTOM -> Icons.Default.Edit
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumChip(
    album: LibraryAlbum,
    viewModel: LibraryViewModels,
    onClick: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = album.representativeSong) {
        value = viewModel.getSongImageUrl(album.representativeSong)
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(18.dp))
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
                    Icons.Default.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Text(
            text = album.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AnimatedArtistShelf(
    order: Int,
    content: @Composable () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(order * 70L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 })
    ) {
        content()
    }
}

@Composable
private fun ArtistSongShelf(
    title: String,
    songs: List<Song>,
    viewModel: LibraryViewModels,
    playlists: List<Playlist>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(songs, key = { "artist_shelf_song_${it.id}_$title" }) { song ->
                ArtistSongCard(
                    song = song,
                    viewModel = viewModel,
                    playlists = playlists
                )
            }
        }
    }
}

@Composable
private fun ArtistSongCard(
    song: Song,
    viewModel: LibraryViewModels,
    playlists: List<Playlist>
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }

    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable { viewModel.playSong(song) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(18.dp))
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
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Text(
            text = song.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ArtistAlbumShelf(
    title: String,
    albums: List<LibraryAlbum>,
    viewModel: LibraryViewModels,
    onOpenAlbum: (LibraryAlbum) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(albums, key = { "artist_shelf_album_${it.id}_${it.title}" }) { album ->
                ArtistAlbumChip(
                    album = album,
                    viewModel = viewModel,
                    onClick = { onOpenAlbum(album) }
                )
            }
        }
    }
}

@Composable
private fun RelatedArtistsShelf(
    artists: List<LibraryArtist>,
    viewModel: LibraryViewModels,
    onOpenArtist: (LibraryArtist) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.fans_might_also_like),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(artists, key = { "related_artist_${it.name}" }) { relatedArtist ->
                RelatedArtistCard(
                    artist = relatedArtist,
                    viewModel = viewModel,
                    onOpen = { onOpenArtist(relatedArtist) }
                )
            }
        }
    }
}

@Composable
private fun RelatedArtistCard(
    artist: LibraryArtist,
    viewModel: LibraryViewModels,
    onOpen: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = artist.name) {
        value = artist.songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
    }

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null)
            }
        }
        Text(
            text = artist.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LibraryCollectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    viewModel: LibraryViewModels,
    coverSongs: List<Song>,
    customCoverUri: String? = null,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(LibraryCardRadius)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartCollectionThumbnail(
            songs = coverSongs,
            customCoverUri = customCoverUri,
            viewModel = viewModel,
            fallbackIcon = icon
        )

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
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface
                )
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
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModels,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setPlaylistCoverUri(playlist.id, uri.toString())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(LibraryCardRadius)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartCollectionThumbnail(
            songs = songs,
            customCoverUri = customCoverUri,
            viewModel = viewModel,
            fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic
        )
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
                onEdit = {
                    showActions = false
                    showEditSheet = true
                },
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
                onShare = {
                    showActions = false
                    sharePlaylist(context, playlist, songs)
                },
                onChangeCover = {
                    showActions = false
                    coverPicker.launch(arrayOf("image/*"))
                },
                onRemoveCover = if (customCoverUri != null) {
                    {
                        showActions = false
                        viewModel.setPlaylistCoverUri(playlist.id, null)
                    }
                } else {
                    null
                },
                onDelete = {
                    showActions = false
                    onDelete()
                }
            )
        }
    }

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EditPlaylistSheet(
                playlist = playlist,
                onDismiss = { showEditSheet = false },
                onSave = { name, description ->
                    viewModel.updatePlaylistDetails(playlist, name, description)
                    showEditSheet = false
                }
            )
        }
    }
}

@Composable
private fun PlaylistActionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveCover: (() -> Unit)?,
    onDelete: () -> Unit
) {
    ActionSheetFrame(
        title = playlist.name,
        subtitle = stringResource(R.string.playlist_label),
        onDismiss = onDismiss,
        actions = buildList {
            if (onEdit != null) {
                add(ActionSpec(Icons.Default.Edit, stringResource(R.string.edit_playlist), onClick = onEdit))
            }
            add(ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay))
            add(ActionSpec(Icons.Default.Shuffle, stringResource(R.string.shuffle), onClick = onShuffle))
            add(ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue))
            add(ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare))
            add(ActionSpec(Icons.Default.Image, stringResource(R.string.change_cover), onClick = onChangeCover))
            if (onRemoveCover != null) {
                add(ActionSpec(Icons.Default.Delete, stringResource(R.string.remove_cover), onClick = onRemoveCover))
            }
            add(ActionSpec(Icons.Default.Delete, stringResource(R.string.delete_playlist), destructive = true, onClick = onDelete))
        }
    )
}

@Composable
private fun CollectionActionsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.Shuffle, stringResource(R.string.shuffle), onClick = onShuffle),
            ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue),
            ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare)
        )
    )
}

@Composable
private fun ArtistActionsSheet(
    artist: LibraryArtist,
    isFollowed: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
    onToggleFollow: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = artist.name,
        subtitle = stringResource(R.string.artist),
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.Radio, stringResource(R.string.radio), onClick = onRadio),
            ActionSpec(
                if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
                if (isFollowed) stringResource(R.string.unfollow) else stringResource(R.string.follow),
                onClick = onToggleFollow
            ),
            ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare)
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
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = song.title,
        subtitle = stringResource(R.string.track),
        onDismiss = onDismiss,
        actions = buildList {
            add(ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay))
            add(ActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next), onClick = onPlayNext))
            add(ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue))
            add(ActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist), onClick = onSaveToPlaylist))
            if (onMoveUp != null) {
                add(ActionSpec(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up), onClick = onMoveUp))
            }
            if (onMoveDown != null) {
                add(ActionSpec(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down), onClick = onMoveDown))
            }
            if (onRemoveFromPlaylist != null) {
                add(ActionSpec(Icons.Default.Delete, stringResource(R.string.remove_from_playlist), destructive = true, onClick = onRemoveFromPlaylist))
            }
            add(
                ActionSpec(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like),
                    onClick = onToggleLike
                )
            )
            add(ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare))
        }
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
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = album.representativeSong) {
        value = viewModel.getSongImageUrl(album.representativeSong)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .liquidGlassEffect(16.dp)
            .clickable(onClick = onOpen)
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
    onClick: () -> Unit,
    playlist: Playlist? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
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
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(LibraryCardRadius)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(LibraryArtworkRadius))
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
                maxLines = 2,
                lineHeight = 21.sp,
                overflow = TextOverflow.Clip
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (song.isExplicit) {
                    RestrictionBadge(text = stringResource(R.string.explicit_badge))
                }
                Text(
                    song.artist.ifBlank { stringResource(R.string.unknown) },
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Clip
                )
            }
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
                    viewModel.playSongFromLibrary(song)
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
                onRemoveFromPlaylist = onRemoveFromPlaylist?.let {
                    {
                        showActions = false
                        it()
                    }
                },
                onMoveUp = if (playlist != null && canMoveUp && onMoveUp != null) {
                    {
                        showActions = false
                        onMoveUp()
                    }
                } else {
                    null
                },
                onMoveDown = if (playlist != null && canMoveDown && onMoveDown != null) {
                    {
                        showActions = false
                        onMoveDown()
                    }
                } else {
                    null
                },
                onShare = {
                    showActions = false
                    scope.launch {
                        shareSong(
                            context = context,
                            song = song,
                            fallbackUrl = viewModel.resolveShareUrl(song)
                        )
                    }
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
                    viewModel.addSongToPlaylist(playlist, song)
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
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(LibraryCardRadius)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(LibraryArtworkRadius))
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
                maxLines = 2,
                lineHeight = 21.sp,
                overflow = TextOverflow.Clip
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (linkedSong?.isExplicit == true) {
                    RestrictionBadge(text = stringResource(R.string.explicit_badge))
                }
                Text(
                    item.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Clip
                )
            }
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
                    scope.launch {
                        shareSong(
                            context = context,
                            song = linkedSong,
                            fallbackUrl = viewModel.resolveShareUrl(linkedSong)
                        )
                    }
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
                    if (linkedSong != null) {
                        scope.launch {
                            shareSong(
                                context = context,
                                song = linkedSong,
                                fallbackUrl = viewModel.resolveShareUrl(linkedSong)
                            )
                        }
                    } else {
                        scope.launch {
                            shareHistoryItem(
                                context = context,
                                item = item,
                                fallbackUrl = viewModel.resolveShareUrl(item.title, item.artist)
                            )
                        }
                    }
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
                    viewModel.addSongToPlaylist(playlist, linkedSong)
                    showPlaylistPicker = false
                }
            )
        }
    }
}

@Composable
private fun RestrictionBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistListItem(
    artist: LibraryArtist,
    viewModel: LibraryViewModels,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = artist.name) {
        value = artist.songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(LibraryCardRadius)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
private fun EditPlaylistSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var playlistName by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    var playlistDescription by rememberSaveable(playlist.id) { mutableStateOf(playlist.description.orEmpty()) }

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
                text = stringResource(R.string.edit_playlist),
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
                Icon(Icons.Default.Edit, contentDescription = null)
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
                onSave(
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
            Text(stringResource(R.string.save_changes), fontSize = 18.sp)
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

private fun shareSong(
    context: android.content.Context,
    song: Song,
    fallbackUrl: String?
) {
    val text = buildString {
        append(song.title)
        if (song.artist.isNotBlank()) append(" — ${song.artist}")
        if (song.album.isNotBlank()) append("\n${song.album}")
        if (!fallbackUrl.isNullOrBlank()) append("\n$fallbackUrl")
    }
    val canShareAudioFile = song.uri.scheme == "content" || song.uri.scheme == "file"
    val intent = if (canShareAudioFile) {
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            clipData = ClipData.newUri(context.contentResolver, song.title, song.uri)
            putExtra(Intent.EXTRA_STREAM, song.uri)
            putExtra(Intent.EXTRA_TITLE, song.title)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, song.title)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, fallbackUrl ?: text)
        }
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share))
    )
}

private fun shareHistoryItem(
    context: android.content.Context,
    item: PlaybackHistoryEntity,
    fallbackUrl: String?
) {
    val text = buildString {
        append("${item.title} — ${item.artist}")
        if (!fallbackUrl.isNullOrBlank()) append("\n$fallbackUrl")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, item.title)
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, fallbackUrl ?: text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share))
    )
}

private fun sharePlaylist(
    context: android.content.Context,
    playlist: Playlist,
    songs: List<Song>
) {
    val text = buildString {
        append(playlist.name)
        append("\n")
        append(context.getString(R.string.songs_count, songs.size))
        if (!playlist.description.isNullOrBlank()) {
            append("\n")
            append(playlist.description)
        }
        songs.take(12).forEachIndexed { index, song ->
            append("\n${index + 1}. ${song.title}")
            if (song.artist.isNotBlank()) append(" — ${song.artist}")
        }
        if (songs.size > 12) append("\n…")
    }
    shareText(context, playlist.name, text)
}

private fun shareAlbum(
    context: android.content.Context,
    album: LibraryAlbum
) {
    val text = buildString {
        append(album.title)
        if (album.artist.isNotBlank()) append(" — ${album.artist}")
        append("\n")
        append(context.getString(R.string.songs_count, album.songCount))
        album.songs.take(12).forEachIndexed { index, song ->
            append("\n${index + 1}. ${song.title}")
        }
        if (album.songs.size > 12) append("\n…")
    }
    shareText(context, album.title, text)
}

private fun shareArtist(
    context: android.content.Context,
    artist: LibraryArtist
) {
    val text = buildString {
        append(artist.name)
        append("\n")
        append(context.getString(R.string.songs_count, artist.songs.size))
        artist.songs.take(12).forEachIndexed { index, song ->
            append("\n${index + 1}. ${song.title}")
        }
        if (artist.songs.size > 12) append("\n…")
    }
    shareText(context, artist.name, text)
}

private fun shareText(
    context: android.content.Context,
    title: String,
    text: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
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

private fun List<Song>.sortedPlaylistSongs(sortOption: PlaylistSongSortOption): List<Song> {
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


