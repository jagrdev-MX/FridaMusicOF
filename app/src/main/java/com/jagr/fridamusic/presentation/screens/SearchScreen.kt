package com.jagr.fridamusic.presentation.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.data.remote.innertube.ResultType
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.min

private const val SEARCH_HISTORY_LIMIT = 30
private const val SEARCH_RECENT_CARD_LIMIT = 4
private const val SEARCH_RECOMMENDATION_LIMIT = 15
private const val SEARCH_ALL_SECTION_LIMIT = 10
private const val SEARCH_TAB_LIMIT = 20

private enum class SearchTab(val labelRes: Int) {
    ALL(R.string.all_filter),
    SONGS(R.string.songs_filter),
    ARTISTS(R.string.artists_filter),
    ALBUMS(R.string.albums_filter),
    PLAYLISTS(R.string.playlists_filter)
}

private enum class SearchSource {
    LIBRARY,
    ONLINE
}

private data class SearchSongHit(
    val song: Song,
    val source: SearchSource,
    val score: Int,
    val thumbnailUrl: String? = null,
    val remoteResult: YouTubeResult? = null
) {
    val key: String = "${source.name}:${remoteResult?.videoId ?: song.id}"
}

private data class SearchArtistHit(
    val name: String,
    val score: Int,
    val localSongs: List<Song>,
    val thumbnailUrl: String? = null,
    val remoteResult: YouTubeResult? = null
) {
    val key: String = normalizeForSearch(name)
}

private data class SearchPlaylistHit(
    val name: String,
    val score: Int,
    val playlist: Playlist? = null,
    val thumbnailUrl: String? = null,
    val remoteResult: YouTubeResult? = null,
    val songCount: Int? = playlist?.songIds?.size
) {
    val key: String = "${playlist?.id ?: remoteResult?.videoId ?: normalizeForSearch(name)}"
}

private data class SearchAlbumHit(
    val name: String,
    val artist: String,
    val score: Int,
    val source: SearchSource,
    val localSongs: List<Song> = emptyList(),
    val thumbnailUrl: String? = null,
    val remoteResult: YouTubeResult? = null
) {
    val key: String = "${source.name}:${remoteResult?.videoId ?: "${normalizeForSearch(name)}|${normalizeForSearch(artist)}"}"
}

private data class SearchRecentCard(
    val query: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val key: String
)

private data class SearchRankingInputs(
    val query: String,
    val songs: List<Song>,
    val onlineResults: List<YouTubeResult>,
    val history: List<PlaybackHistoryEntity>,
    val playlists: List<Playlist>
)

private data class SearchComputedResults(
    val query: String = "",
    val songHits: List<SearchSongHit> = emptyList(),
    val artistHits: List<SearchArtistHit> = emptyList(),
    val albumHits: List<SearchAlbumHit> = emptyList(),
    val playlistHits: List<SearchPlaylistHit> = emptyList()
)

private sealed interface SearchActionTarget {
    data class SongTarget(val hit: SearchSongHit) : SearchActionTarget
    data class ArtistTarget(val hit: SearchArtistHit) : SearchActionTarget
    data class PlaylistTarget(val hit: SearchPlaylistHit) : SearchActionTarget
    data class AlbumTarget(val hit: SearchAlbumHit) : SearchActionTarget
}

private data class SearchActionSpec(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: LibraryViewModels,
    hazeState: HazeState? = null,
    focusSignal: Int = 0,
    onNavigateToArtist: (String, String) -> Unit = { _, _ -> }
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeQuery by rememberSaveable { mutableStateOf("") }
    var selectedTabName by rememberSaveable { mutableStateOf(SearchTab.ALL.name) }
    val selectedTab = remember(selectedTabName) {
        runCatching { SearchTab.valueOf(selectedTabName) }.getOrDefault(SearchTab.ALL)
    }

    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val recentHistory by viewModel.recentHistory.collectAsState()
    val fullHistory by viewModel.fullHistory.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val onlineResults by viewModel.youtubeSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()

    val query = activeQuery.trim()
    val rankingInputs = remember(query, songs, onlineResults, fullHistory, playlists) {
        SearchRankingInputs(query, songs, onlineResults, fullHistory, playlists)
    }
    val computedResults by produceState(SearchComputedResults(), rankingInputs) {
        value = if (rankingInputs.query.isBlank()) {
            SearchComputedResults()
        } else {
            withContext(Dispatchers.Default) {
                SearchComputedResults(
                    query = rankingInputs.query,
                    songHits = buildSongHits(
                        rankingInputs.query,
                        rankingInputs.songs,
                        rankingInputs.onlineResults,
                        rankingInputs.history
                    ).take(SEARCH_TAB_LIMIT),
                    artistHits = buildArtistHits(
                        rankingInputs.query,
                        rankingInputs.songs,
                        rankingInputs.onlineResults
                    ).take(SEARCH_TAB_LIMIT),
                    albumHits = buildAlbumHits(
                        rankingInputs.query,
                        rankingInputs.songs,
                        rankingInputs.onlineResults
                    ).take(SEARCH_TAB_LIMIT),
                    playlistHits = buildPlaylistHits(
                        rankingInputs.query,
                        rankingInputs.playlists,
                        rankingInputs.songs,
                        rankingInputs.onlineResults
                    ).take(SEARCH_TAB_LIMIT)
                )
            }
        }
    }
    val displayedResults = computedResults.takeIf { it.query == query } ?: SearchComputedResults()
    val songHits = displayedResults.songHits
    val searchQueueSongs = remember(songHits) { songHits.map { it.song } }
    val artistHits = displayedResults.artistHits
    val albumHits = displayedResults.albumHits
    val playlistHits = displayedResults.playlistHits
    val isRankingResults = query.isNotBlank() && computedResults.query != query
    val hasResults = songHits.isNotEmpty() || artistHits.isNotEmpty() || albumHits.isNotEmpty() || playlistHits.isNotEmpty()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val showingResults = query.isNotBlank()
    val headerHeight = topInset + if (showingResults) 154.dp else 96.dp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var actionTarget by remember { mutableStateOf<SearchActionTarget?>(null) }
    var detailsTarget by remember { mutableStateOf<SearchActionTarget?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }

    val recentCards = remember(recentHistory, songs) {
        buildRecentSearchCards(recentHistory, songs).take(SEARCH_RECENT_CARD_LIMIT)
    }
    val smartSuggestions = remember(searchHistory, fullHistory, songs) {
        buildSmartSearchSuggestions(searchHistory, fullHistory, songs).take(SEARCH_RECOMMENDATION_LIMIT)
    }

    fun focusSearchField() {
        scope.launch {
            delay(80)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    fun submitSearch(rawQuery: String) {
        val cleanQuery = rawQuery.trim()
        if (cleanQuery.isBlank()) return
        searchQuery = cleanQuery
        activeQuery = cleanQuery
        selectedTabName = SearchTab.ALL.name
        viewModel.addToSearchHistory(cleanQuery)
        viewModel.searchYouTube(cleanQuery)
        keyboardController?.hide()
        scope.launch {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
            }
        }
    }

    fun fillSearchField(rawQuery: String) {
        searchQuery = rawQuery
        activeQuery = ""
        selectedTabName = SearchTab.ALL.name
        focusSearchField()
    }

    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var isScrollingDown by remember { mutableStateOf(false) }
    var lastScrollPosition by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex * 1000 + listState.firstVisibleItemScrollOffset }
            .collect { scrollPosition ->
                isScrollingDown = scrollPosition > lastScrollPosition
                lastScrollPosition = scrollPosition
            }
    }

    val isPastShortcutThreshold by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 320
        }
    }
    var showScrollShortcut by remember { mutableStateOf(false) }
    LaunchedEffect(isPastShortcutThreshold, isScrollingDown, listState.isScrollInProgress, selectedTab) {
        if (!isPastShortcutThreshold || !isScrollingDown) {
            showScrollShortcut = false
        } else {
            showScrollShortcut = true
            if (!listState.isScrollInProgress) {
                delay(2200)
                showScrollShortcut = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (query.isNotBlank() && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchBackdrop()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = headerHeight + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (query.isBlank()) {
                if (recentCards.isNotEmpty()) {
                    item {
                        SearchRecentCardsSection(
                            cards = recentCards,
                            onSubmit = ::submitSearch
                        )
                    }
                }

                if (searchHistory.isNotEmpty()) {
                    item {
                        SearchHistoryHeader(onClear = viewModel::clearSearchHistory)
                    }
                    items(searchHistory.take(SEARCH_HISTORY_LIMIT), key = { "history-$it" }) { pastQuery ->
                        SearchHistoryRow(
                            query = pastQuery,
                            icon = Icons.Default.History,
                            onClick = { submitSearch(pastQuery) },
                            onFill = { fillSearchField(pastQuery) },
                            onRemove = { viewModel.removeFromSearchHistory(pastQuery) }
                        )
                    }
                }

                if (smartSuggestions.isNotEmpty()) {
                    item {
                        SearchSuggestionHeader()
                    }
                    items(smartSuggestions, key = { "suggestion-$it" }) { suggestion ->
                        SearchHistoryRow(
                            query = suggestion,
                            icon = Icons.Default.Search,
                            onClick = { submitSearch(suggestion) },
                            onFill = { fillSearchField(suggestion) },
                            onRemove = null
                        )
                    }
                }

                if (recentCards.isEmpty() && searchHistory.isEmpty() && smartSuggestions.isEmpty()) {
                    item { SearchStartState() }
                }
            } else {
                if ((isSearching || isRankingResults) && !hasResults) {
                    item {
                        SearchLoadingState()
                    }
                }

                when (selectedTab) {
                    SearchTab.ALL -> {
                        if (songHits.isNotEmpty()) {
                            val visibleSongs = songHits.take(SEARCH_ALL_SECTION_LIMIT)
                            item { SectionTitle(stringResource(R.string.songs_filter), visibleSongs.size) }
                            items(visibleSongs, key = { it.key }) { hit ->
                                SearchSongRow(
                                    hit = hit,
                                    onClick = { viewModel.playSongFromSearch(hit.song, searchQueueSongs, query) },
                                    onAddToPlaylist = { playlistPickerSong = hit.song },
                                    onMore = { actionTarget = SearchActionTarget.SongTarget(hit) }
                                )
                            }
                        }

                        if (artistHits.isNotEmpty()) {
                            val visibleArtists = artistHits.take(SEARCH_ALL_SECTION_LIMIT)
                            item { SectionTitle(stringResource(R.string.artists_filter), visibleArtists.size) }
                            items(visibleArtists, key = { "artist-${it.key}" }) { hit ->
                                SearchArtistRow(
                                    hit = hit,
                                    onClick = {
                                        onNavigateToArtist(hit.name, hit.thumbnailUrl.orEmpty())
                                    },
                                    onMore = { actionTarget = SearchActionTarget.ArtistTarget(hit) }
                                )
                            }
                        }

                        if (albumHits.isNotEmpty()) {
                            val visibleAlbums = albumHits.take(SEARCH_ALL_SECTION_LIMIT)
                            item { SectionTitle(stringResource(R.string.albums_filter), visibleAlbums.size) }
                            items(visibleAlbums, key = { "album-${it.key}" }) { hit ->
                                SearchAlbumRow(
                                    hit = hit,
                                    onClick = {
                                        if (hit.localSongs.isNotEmpty()) {
                                            viewModel.playSongs(hit.localSongs)
                                        } else {
                                            detailsTarget = SearchActionTarget.AlbumTarget(hit)
                                        }
                                    },
                                    onMore = { actionTarget = SearchActionTarget.AlbumTarget(hit) }
                                )
                            }
                        }

                        if (playlistHits.isNotEmpty()) {
                            val visiblePlaylists = playlistHits.take(SEARCH_ALL_SECTION_LIMIT)
                            item { SectionTitle(stringResource(R.string.playlists_filter), visiblePlaylists.size) }
                            items(visiblePlaylists, key = { "playlist-${it.key}" }) { hit ->
                                SearchPlaylistRow(
                                    hit = hit,
                                    viewModel = viewModel,
                                    allSongs = songs,
                                    onClick = { actionTarget = SearchActionTarget.PlaylistTarget(hit) },
                                    onMore = { actionTarget = SearchActionTarget.PlaylistTarget(hit) }
                                )
                            }
                        }
                    }

                    SearchTab.SONGS -> {
                        item { SectionTitle(stringResource(R.string.songs_filter), songHits.size) }
                        if (songHits.isEmpty()) {
                            item { EmptySearchState(query) }
                        } else {
                            items(songHits, key = { it.key }) { hit ->
                                SearchSongRow(
                                    hit = hit,
                                    onClick = { viewModel.playSongFromSearch(hit.song, searchQueueSongs, query) },
                                    onAddToPlaylist = { playlistPickerSong = hit.song },
                                    onMore = { actionTarget = SearchActionTarget.SongTarget(hit) }
                                )
                            }
                        }
                    }

                    SearchTab.ARTISTS -> {
                        item { SectionTitle(stringResource(R.string.artists_filter), artistHits.size) }
                        if (artistHits.isEmpty()) {
                            item { EmptySearchState(query) }
                        } else {
                            items(artistHits, key = { "artist-${it.key}" }) { hit ->
                                SearchArtistRow(
                                    hit = hit,
                                    onClick = { onNavigateToArtist(hit.name, hit.thumbnailUrl.orEmpty()) },
                                    onMore = { actionTarget = SearchActionTarget.ArtistTarget(hit) }
                                )
                            }
                        }
                    }

                    SearchTab.ALBUMS -> {
                        item { SectionTitle(stringResource(R.string.albums_filter), albumHits.size) }
                        if (albumHits.isEmpty()) {
                            item { EmptySearchState(query) }
                        } else {
                            items(albumHits, key = { "album-${it.key}" }) { hit ->
                                SearchAlbumRow(
                                    hit = hit,
                                    onClick = {
                                        if (hit.localSongs.isNotEmpty()) {
                                            viewModel.playSongs(hit.localSongs)
                                        } else {
                                            detailsTarget = SearchActionTarget.AlbumTarget(hit)
                                        }
                                    },
                                    onMore = { actionTarget = SearchActionTarget.AlbumTarget(hit) }
                                )
                            }
                        }
                    }

                    SearchTab.PLAYLISTS -> {
                        item { SectionTitle(stringResource(R.string.playlists_filter), playlistHits.size) }
                        if (playlistHits.isEmpty()) {
                            item { EmptySearchState(query) }
                        } else {
                            items(playlistHits, key = { "playlist-${it.key}" }) { hit ->
                                SearchPlaylistRow(
                                    hit = hit,
                                    viewModel = viewModel,
                                    allSongs = songs,
                                    onClick = { actionTarget = SearchActionTarget.PlaylistTarget(hit) },
                                    onMore = { actionTarget = SearchActionTarget.PlaylistTarget(hit) }
                                )
                            }
                        }
                    }
                }

                if (!isSearching && !isRankingResults && !hasResults && selectedTab == SearchTab.ALL) {
                    item {
                        EmptySearchState(query)
                    }
                }
            }
        }

        SearchStickyHeader(
            query = searchQuery,
            selectedTab = selectedTab,
            showTabs = showingResults,
            listState = listState,
            hazeState = hazeState,
            focusRequester = focusRequester,
            onQueryChange = {
                searchQuery = it
                if (activeQuery.isNotEmpty() && it.trim() != activeQuery) {
                    activeQuery = ""
                    selectedTabName = SearchTab.ALL.name
                    viewModel.searchYouTube("")
                }
            },
            onSearch = { submitSearch(searchQuery) },
            onClearQuery = {
                searchQuery = ""
                activeQuery = ""
                selectedTabName = SearchTab.ALL.name
                viewModel.searchYouTube("")
                focusSearchField()
            },
            onTabSelected = { selectedTabName = it.name },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f)
        )

        SearchScrollShortcut(
            visible = showScrollShortcut,
            reduceMotion = reduceMotion,
            bottomPadding = paddingValues.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            onClick = {
                scope.launch {
                    if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        actionTarget?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { actionTarget = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                SearchActionsSheet(
                    target = target,
                    playlists = playlists,
                    followedArtists = followedArtists,
                    searchSongs = searchQueueSongs,
                    query = query,
                    viewModel = viewModel,
                    onDismiss = { actionTarget = null },
                    onPickPlaylist = { song ->
                        actionTarget = null
                        playlistPickerSong = song
                    },
                    onShowDetails = {
                        actionTarget = null
                        detailsTarget = target
                    },
                    onNavigateToArtist = onNavigateToArtist
                )
            }
        }

        playlistPickerSong?.let { song ->
            ModalBottomSheet(
                onDismissRequest = { playlistPickerSong = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                SearchSaveToPlaylistSheet(
                    song = song,
                    playlists = playlists,
                    onDismiss = { playlistPickerSong = null },
                    onSelect = { playlist ->
                        viewModel.addSongToPlaylist(playlist, song)
                        Toast.makeText(
                            context,
                            context.getString(R.string.added_to_playlist_format, playlist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        playlistPickerSong = null
                    },
                    onCreate = { name, description ->
                        viewModel.createPlaylistWithSong(name, description, song)
                        Toast.makeText(
                            context,
                            context.getString(R.string.added_to_playlist_format, name),
                            Toast.LENGTH_SHORT
                        ).show()
                        playlistPickerSong = null
                    }
                )
            }
        }

        detailsTarget?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { detailsTarget = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                SearchDetailsSheet(
                    target = target,
                    viewModel = viewModel,
                    onDismiss = { detailsTarget = null }
                )
            }
        }
    }
}

@Composable
private fun SearchBackdrop() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.08f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(primary, secondary.copy(alpha = 0.35f), Color.Transparent),
                    endY = 620f
                )
            )
    )
}

@Composable
private fun SearchStickyHeader(
    query: String,
    selectedTab: SearchTab,
    showTabs: Boolean,
    listState: LazyListState,
    hazeState: HazeState?,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 16 }
    }
    val scrollAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        label = "search-header-scroll-alpha"
    )
    val isDark = isSystemInDarkTheme()

    // CORRECCIÓN: Fondo dinámico adaptable a modo oscuro y claro sin colores quemados.
    val tint = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isDark) 0.65f + (0.3f * scrollAlpha) else 0.85f + (0.15f * scrollAlpha)
    )
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f + (0.07f * scrollAlpha))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .searchHeaderGlass(hazeState, tint)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
                bottom = 12.dp
            )
    ) {
        PremiumSearchField(
            query = query,
            focusRequester = focusRequester,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onClearQuery = onClearQuery
        )

        AnimatedVisibility(
            visible = showTabs,
            enter = fadeIn() + slideInVertically { -it / 4 },
            exit = fadeOut() + slideOutVertically { -it / 4 }
        ) {
            SearchTabs(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun Modifier.searchHeaderGlass(
    hazeState: HazeState?,
    tint: Color
): Modifier {
    return background(tint)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumSearchField(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                style = LiquidTypography.bodyLarge.copy(fontSize = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_all),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(30.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle = LiquidTypography.bodyLarge.copy(
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun SearchTabs(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SearchTab.entries, key = { it.name }) { tab ->
            val selected = selectedTab == tab
            val selectedAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                label = "search-tab-alpha"
            )
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.03f else 1f,
                label = "search-tab-scale"
            )
            val shape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(shape)
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.88f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f + selectedAlpha * 0.22f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        },
                        shape = shape
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SearchRecentCardsSection(
    cards: List<SearchRecentCard>,
    onSubmit: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.recent_searches),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(cards, key = { it.key }) { card ->
                SearchRecentCardTile(card = card, onClick = { onSubmit(card.query) })
            }
        }
    }
}

@Composable
private fun SearchRecentCardTile(
    card: SearchRecentCard,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        SearchArtworkBox(
            imageUrl = card.imageUrl,
            icon = Icons.Default.MusicNote,
            shape = RoundedCornerShape(14.dp),
            size = 118.dp
        )
        Text(
            text = card.title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = card.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchHistoryHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.search_history),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp
        )
        Text(
            text = stringResource(R.string.clear_all),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onClear)
        )
    }
}

@Composable
private fun SearchSuggestionHeader() {
    Text(
        text = stringResource(R.string.you_may_also_like),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp)
    )
}

@Composable
private fun SearchHistoryRow(
    query: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = query,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onFill, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.fill_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = -45f }
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SearchStartState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TravelExplore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.search_start_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.search_start_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 42.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
    }
}

@Composable
private fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            modifier = Modifier.size(38.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.no_search_results),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.try_another_search, query),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp
        )
        if (count > 0) {
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SearchSongRow(
    hit: SearchSongHit,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMore: () -> Unit
) {
    val imageUrl = remember(hit.song, hit.thumbnailUrl) { bestSongArtwork(hit.song, hit.thumbnailUrl) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchArtworkBox(
            imageUrl = imageUrl,
            icon = Icons.Default.MusicNote,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.song.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(hit.source)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(
                        R.string.song_format,
                        hit.song.artist.ifBlank { stringResource(R.string.unknown_artist_search) }
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.AddCircleOutline,
                contentDescription = stringResource(R.string.save_to_playlist),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(25.dp)
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
private fun SearchArtistRow(
    hit: SearchArtistHit,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val imageUrl = remember(hit) {
        hit.thumbnailUrl ?: hit.localSongs.firstNotNullOfOrNull { bestSongArtwork(it, null) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchArtworkBox(
            imageUrl = imageUrl,
            icon = Icons.Default.Person,
            shape = CircleShape
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (hit.localSongs.isNotEmpty()) {
                    stringResource(R.string.songs_count, hit.localSongs.size)
                } else {
                    stringResource(R.string.artist)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchPlaylistRow(
    hit: SearchPlaylistHit,
    viewModel: LibraryViewModels,
    allSongs: List<Song>,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val playlistSongs = remember(hit.playlist, allSongs) {
        hit.playlist?.let { viewModel.songsForPlaylist(it) }.orEmpty()
    }
    val imageUrl = remember(hit.key, hit.thumbnailUrl, playlistSongs) {
        hit.thumbnailUrl
            ?: hit.playlist?.let { viewModel.playlistCoverUri(it.id) }
            ?: playlistSongs.firstNotNullOfOrNull { bestSongArtwork(it, null) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchArtworkBox(
            imageUrl = imageUrl,
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hit.songCount?.let { stringResource(R.string.songs_count, it) }
                    ?: stringResource(R.string.online_playlist_source),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMore, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchAlbumRow(
    hit: SearchAlbumHit,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    val imageUrl = remember(hit) {
        hit.thumbnailUrl ?: hit.localSongs.firstNotNullOfOrNull { bestSongArtwork(it, null) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchArtworkBox(
            imageUrl = imageUrl,
            icon = Icons.Default.Album,
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hit.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(hit.source)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (hit.localSongs.isNotEmpty()) {
                        stringResource(R.string.album_subtitle_format, hit.artist.ifBlank { stringResource(R.string.unknown_artist_search) }, hit.localSongs.size)
                    } else {
                        stringResource(R.string.album_format, hit.artist.ifBlank { stringResource(R.string.unknown_artist_search) })
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onMore, modifier = Modifier.size(42.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchArtworkBox(
    imageUrl: String?,
    icon: ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    size: androidx.compose.ui.unit.Dp = 58.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        FridaArtworkImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            shape = shape,
            modifier = Modifier.fillMaxSize(),
            requestSizePx = 128
        )
    }
}

@Composable
private fun SourceBadge(source: SearchSource) {
    val label = when (source) {
        SearchSource.LIBRARY -> stringResource(R.string.library_source)
        SearchSource.ONLINE -> stringResource(R.string.yt_label)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SearchScrollShortcut(
    visible: Boolean,
    reduceMotion: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) EnterTransition.None else fadeIn() + slideInVertically { it / 2 },
        exit = if (reduceMotion) ExitTransition.None else fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
            .padding(end = 22.dp, bottom = bottomPadding + 118.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .liquidGlassEffect(23.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.back_to_top),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun SearchActionsSheet(
    target: SearchActionTarget,
    playlists: List<Playlist>,
    followedArtists: Set<String>,
    searchSongs: List<Song>,
    query: String,
    viewModel: LibraryViewModels,
    onDismiss: () -> Unit,
    onPickPlaylist: (Song) -> Unit,
    onShowDetails: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favoriteNames = favoritePlaylistNames()

    val title: String
    val subtitle: String
    val actions: List<SearchActionSpec>

    when (target) {
        is SearchActionTarget.SongTarget -> {
            val hit = target.hit
            val song = hit.song
            val isLiked = playlists.any { it.name in favoriteNames && song.id in it.songIds }
            title = song.title
            subtitle = stringResource(R.string.track)
            actions = buildList {
                add(SearchActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play)) {
                    onDismiss()
                    viewModel.playSongFromSearch(song, searchSongs, query)
                })
                add(SearchActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next)) {
                    onDismiss()
                    viewModel.addSongNext(song)
                    toast(context, R.string.play_next_feedback)
                })
                add(SearchActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue)) {
                    onDismiss()
                    viewModel.addSongToQueue(song)
                    toast(context, R.string.added_to_queue_feedback)
                })
                add(SearchActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist)) {
                    onPickPlaylist(song)
                })
                add(
                    SearchActionSpec(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like)
                    ) {
                        onDismiss()
                        viewModel.toggleLike(song)
                        toast(context, if (isLiked) R.string.removed_from_favorites else R.string.added_to_favorites)
                    }
                )
                if (isKnownArtist(song.artist)) {
                    add(SearchActionSpec(Icons.Default.Person, stringResource(R.string.open_artist)) {
                        onDismiss()
                        onNavigateToArtist(song.artist, hit.thumbnailUrl.orEmpty())
                    })
                }
                add(SearchActionSpec(Icons.Default.Share, stringResource(R.string.share)) {
                    onDismiss()
                    scope.launch {
                        val remoteUrl = hit.remoteResult?.videoId?.let { "https://music.youtube.com/watch?v=$it" }
                        val fallbackUrl = remoteUrl ?: if (song.hasLocalAudioToShare()) {
                            null
                        } else {
                            viewModel.resolveShareUrl(song)
                        }
                        shareSearchSong(context, song, fallbackUrl)
                    }
                })
                add(SearchActionSpec(Icons.Default.Info, stringResource(R.string.details)) {
                    onShowDetails()
                })
            }
        }

        is SearchActionTarget.ArtistTarget -> {
            val hit = target.hit
            val isFollowed = hit.name in followedArtists
            title = hit.name
            subtitle = stringResource(R.string.artist)
            actions = buildList {
                add(SearchActionSpec(Icons.Default.Person, stringResource(R.string.open_artist)) {
                    onDismiss()
                    onNavigateToArtist(hit.name, hit.thumbnailUrl.orEmpty())
                })
                if (hit.localSongs.isNotEmpty()) {
                    add(SearchActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play)) {
                        onDismiss()
                        viewModel.playSongs(hit.localSongs)
                    })
                    add(SearchActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue)) {
                        onDismiss()
                        viewModel.addSongsToQueue(hit.localSongs)
                        toast(context, R.string.added_to_queue_feedback)
                    })
                }
                add(
                    SearchActionSpec(
                        if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
                        if (isFollowed) stringResource(R.string.unfollow) else stringResource(R.string.follow)
                    ) {
                        onDismiss()
                        viewModel.toggleFollowArtist(hit.name)
                    }
                )
                add(SearchActionSpec(Icons.Default.Share, stringResource(R.string.share)) {
                    onDismiss()
                    shareText(context, hit.name, hit.name)
                })
                add(SearchActionSpec(Icons.Default.Info, stringResource(R.string.details)) {
                    onShowDetails()
                })
            }
        }

        is SearchActionTarget.PlaylistTarget -> {
            val hit = target.hit
            title = hit.name
            subtitle = stringResource(R.string.playlist_label)
            actions = buildList {
                hit.playlist?.let { playlist ->
                    add(SearchActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play)) {
                        onDismiss()
                        viewModel.playPlaylist(playlist)
                    })
                    add(SearchActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue)) {
                        onDismiss()
                        viewModel.addPlaylistToQueue(playlist)
                        toast(context, R.string.added_to_queue_feedback)
                    })
                    add(SearchActionSpec(Icons.Default.Share, stringResource(R.string.share)) {
                        onDismiss()
                        sharePlaylist(context, playlist, viewModel.songsForPlaylist(playlist))
                    })
                }
                if (hit.remoteResult != null) {
                    add(SearchActionSpec(Icons.Default.Share, stringResource(R.string.share)) {
                        onDismiss()
                        shareText(
                            context,
                            hit.name,
                            "https://www.youtube.com/playlist?list=${hit.remoteResult.videoId}"
                        )
                    })
                }
                add(SearchActionSpec(Icons.Default.Info, stringResource(R.string.details)) {
                    onShowDetails()
                })
            }
        }

        is SearchActionTarget.AlbumTarget -> {
            val hit = target.hit
            title = hit.name
            subtitle = stringResource(R.string.album_label)
            actions = buildList {
                if (hit.localSongs.isNotEmpty()) {
                    add(SearchActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play)) {
                        onDismiss()
                        viewModel.playSongs(hit.localSongs)
                    })
                    add(SearchActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue)) {
                        onDismiss()
                        viewModel.addSongsToQueue(hit.localSongs)
                        toast(context, R.string.added_to_queue_feedback)
                    })
                    add(SearchActionSpec(Icons.Default.Share, stringResource(R.string.share)) {
                        onDismiss()
                        shareText(
                            context,
                            hit.name,
                            buildString {
                                append(hit.name)
                                if (hit.artist.isNotBlank()) append(" - ${hit.artist}")
                                append("\n")
                                append(context.getString(R.string.songs_count, hit.localSongs.size))
                            }
                        )
                    })
                }
                add(SearchActionSpec(Icons.Default.Info, stringResource(R.string.details)) {
                    onShowDetails()
                })
            }
        }
    }

    ActionSheetFrame(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        actions = actions
    )
}

@Composable
private fun ActionSheetFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: List<SearchActionSpec>
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                        SearchActionTile(
                            action = action,
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
private fun SearchActionTile(
    action: SearchActionSpec,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
            .clickable(onClick = action.onClick)
            .padding(horizontal = 12.dp, vertical = 17.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = action.label,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SearchSaveToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var createMode by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    var playlistDescription by rememberSaveable { mutableStateOf("") }

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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.save_to_playlist),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = song.title,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(
            onClick = { createMode = !createMode },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.create_new_playlist))
        }

        AnimatedVisibility(visible = createMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) },
                    shape = RoundedCornerShape(20.dp)
                )
                OutlinedTextField(
                    value = playlistDescription,
                    onValueChange = { playlistDescription = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_description)) },
                    shape = RoundedCornerShape(20.dp)
                )
                Button(
                    onClick = {
                        onCreate(
                            playlistName.trim(),
                            playlistDescription.trim().takeIf { it.isNotBlank() }
                        )
                    },
                    enabled = playlistName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(stringResource(R.string.create))
                }
            }
        }

        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.no_playlists),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 22.dp)
            )
        } else {
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(playlist) }
                        .padding(horizontal = 10.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

        Spacer(modifier = Modifier.height(22.dp))
    }
}

@Composable
private fun SearchDetailsSheet(
    target: SearchActionTarget,
    viewModel: LibraryViewModels,
    onDismiss: () -> Unit
) {
    val title = when (target) {
        is SearchActionTarget.SongTarget -> target.hit.song.title
        is SearchActionTarget.ArtistTarget -> target.hit.name
        is SearchActionTarget.PlaylistTarget -> target.hit.name
        is SearchActionTarget.AlbumTarget -> target.hit.name
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp)
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
                text = stringResource(R.string.details),
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(18.dp))

        when (target) {
            is SearchActionTarget.SongTarget -> {
                val song = target.hit.song
                DetailRow(stringResource(R.string.artist_label), song.artist.ifBlank { stringResource(R.string.unknown_artist_search) })
                if (song.album.isNotBlank()) DetailRow(stringResource(R.string.album_label), song.album)
                DetailRow(stringResource(R.string.source_label), if (target.hit.source == SearchSource.LIBRARY) stringResource(R.string.source_local) else stringResource(R.string.source_youtube))
            }

            is SearchActionTarget.ArtistTarget -> {
                DetailRow(stringResource(R.string.artist), target.hit.name)
                if (target.hit.localSongs.isNotEmpty()) {
                    DetailRow(stringResource(R.string.songs_tab), stringResource(R.string.songs_count, target.hit.localSongs.size))
                }
                DetailRow(stringResource(R.string.source_label), if (target.hit.localSongs.isNotEmpty()) stringResource(R.string.source_local) else stringResource(R.string.source_youtube))
            }

            is SearchActionTarget.PlaylistTarget -> {
                val hit = target.hit
                DetailRow(stringResource(R.string.playlist_label), hit.name)
                hit.songCount?.let { DetailRow(stringResource(R.string.songs_tab), stringResource(R.string.songs_count, it)) }
                hit.playlist?.let { playlist ->
                    val songs = viewModel.songsForPlaylist(playlist)
                    if (!playlist.description.isNullOrBlank()) DetailRow(stringResource(R.string.playlist_description), playlist.description.orEmpty())
                    if (songs.isNotEmpty()) {
                        DetailRow(stringResource(R.string.from_your_library), songs.take(3).joinToString { it.title })
                    }
                }
                DetailRow(stringResource(R.string.source_label), if (hit.playlist != null) stringResource(R.string.source_local) else stringResource(R.string.source_youtube))
            }

            is SearchActionTarget.AlbumTarget -> {
                val hit = target.hit
                DetailRow(stringResource(R.string.album_label), hit.name)
                if (hit.artist.isNotBlank()) DetailRow(stringResource(R.string.artist_label), hit.artist)
                if (hit.localSongs.isNotEmpty()) {
                    DetailRow(stringResource(R.string.songs_tab), stringResource(R.string.songs_count, hit.localSongs.size))
                    DetailRow(stringResource(R.string.from_your_library), hit.localSongs.take(3).joinToString { it.title })
                }
                DetailRow(stringResource(R.string.source_label), if (hit.source == SearchSource.LIBRARY) stringResource(R.string.source_local) else stringResource(R.string.source_youtube))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.weight(0.38f)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.62f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

private fun buildSongHits(
    query: String,
    localSongs: List<Song>,
    onlineResults: List<YouTubeResult>,
    history: List<PlaybackHistoryEntity>
): List<SearchSongHit> {
    val normalizedQuery = normalizeForSearch(query)
    if (normalizedQuery.isBlank()) return emptyList()

    val tokens = expandedSearchTokens(normalizedQuery)
    val historyCounts = history
        .groupingBy { metadataKey(it.title, it.artist) }
        .eachCount()
    val byMetadata = LinkedHashMap<String, SearchSongHit>()

    localSongs.forEach { song ->
        val score = scoreSong(song, normalizedQuery, tokens) +
                min((historyCounts[metadataKey(song.title, song.artist)] ?: 0) * 5, 24)
        if (score <= 0) return@forEach
        val key = metadataKey(song.title, song.artist)
        val hit = SearchSongHit(
            song = song,
            source = SearchSource.LIBRARY,
            score = score,
            thumbnailUrl = bestSongArtwork(song, null)
        )
        val existing = byMetadata[key]
        if (existing == null || hit.score > existing.score || existing.source != SearchSource.LIBRARY) {
            byMetadata[key] = hit
        }
    }

    onlineResults
        .filter { it.type == ResultType.SONG }
        .forEach { result ->
            val score = scoreRemoteResult(result, normalizedQuery, tokens)
            if (score <= 0) return@forEach
            val song = result.toSearchSong()
            val key = metadataKey(song.title, song.artist)
            val hit = SearchSongHit(
                song = song,
                source = SearchSource.ONLINE,
                score = score,
                thumbnailUrl = result.thumbnailUrl.takeIf { it.isNotBlank() },
                remoteResult = result
            )
            val existing = byMetadata[key]
            if (existing == null || (existing.source != SearchSource.LIBRARY && hit.score > existing.score)) {
                byMetadata[key] = hit
            }
        }

    return byMetadata.values
        .sortedWith(
            compareByDescending<SearchSongHit> { it.score }
                .thenBy { stableRank(normalizedQuery, it.key) }
        )
}

private fun buildArtistHits(
    query: String,
    localSongs: List<Song>,
    onlineResults: List<YouTubeResult>
): List<SearchArtistHit> {
    val normalizedQuery = normalizeForSearch(query)
    if (normalizedQuery.isBlank()) return emptyList()
    val tokens = expandedSearchTokens(normalizedQuery)
    val byArtist = LinkedHashMap<String, SearchArtistHit>()

    localSongs
        .filter { isKnownArtist(it.artist) }
        .groupBy { normalizeForSearch(it.artist) }
        .forEach { (_, artistSongs) ->
            val artistName = artistSongs.first().artist
            val score = scoreText(artistName, normalizedQuery, tokens, 1.2f) +
                    artistSongs.sumOf { scoreSong(it, normalizedQuery, tokens).coerceAtMost(18) }
            if (score <= 0) return@forEach
            byArtist[normalizeForSearch(artistName)] = SearchArtistHit(
                name = artistName,
                score = score,
                localSongs = artistSongs.sortedByDescending { it.dateAdded }
            )
        }

    onlineResults.forEach { result ->
        val candidateName = when {
            result.type == ResultType.ARTIST -> result.title
            result.type == ResultType.SONG && isKnownArtist(result.artist) -> result.artist
            else -> null
        } ?: return@forEach

        val key = normalizeForSearch(candidateName)
        if (key.isBlank()) return@forEach
        val score = scoreText(candidateName, normalizedQuery, tokens, 1.2f) +
                scoreRemoteResult(result, normalizedQuery, tokens).coerceAtMost(34)
        if (score <= 0) return@forEach

        val existing = byArtist[key]
        byArtist[key] = SearchArtistHit(
            name = existing?.name ?: candidateName,
            score = maxOf(existing?.score ?: 0, score),
            localSongs = existing?.localSongs.orEmpty(),
            thumbnailUrl = existing?.thumbnailUrl ?: result.thumbnailUrl.takeIf { it.isNotBlank() },
            remoteResult = if (result.type == ResultType.ARTIST) result else existing?.remoteResult
        )
    }

    return byArtist.values
        .sortedWith(
            compareByDescending<SearchArtistHit> { it.score }
                .thenByDescending { it.localSongs.size }
                .thenBy { stableRank(normalizedQuery, it.key) }
        )
}

private fun buildPlaylistHits(
    query: String,
    playlists: List<Playlist>,
    localSongs: List<Song>,
    onlineResults: List<YouTubeResult>
): List<SearchPlaylistHit> {
    val normalizedQuery = normalizeForSearch(query)
    if (normalizedQuery.isBlank()) return emptyList()
    val tokens = expandedSearchTokens(normalizedQuery)
    val songsById = localSongs.associateBy { it.id }
    val byPlaylist = LinkedHashMap<String, SearchPlaylistHit>()

    playlists.forEach { playlist ->
        val playlistSongs = playlist.songIds.mapNotNull { songsById[it] }
        val score = scoreText(playlist.name, normalizedQuery, tokens, 1.25f) +
                scoreText(playlist.description.orEmpty(), normalizedQuery, tokens, 0.8f) +
                playlistSongs.sumOf { scoreSong(it, normalizedQuery, tokens).coerceAtMost(14) }
        if (score <= 0) return@forEach
        byPlaylist["local-${playlist.id}"] = SearchPlaylistHit(
            name = playlist.name,
            score = score,
            playlist = playlist,
            songCount = playlist.songIds.size
        )
    }

    onlineResults
        .filter { it.type == ResultType.PLAYLIST }
        .forEach { result ->
            val score = scoreRemoteResult(result, normalizedQuery, tokens)
            if (score <= 0) return@forEach
            byPlaylist["remote-${result.videoId}"] = SearchPlaylistHit(
                name = result.title,
                score = score,
                thumbnailUrl = result.thumbnailUrl.takeIf { it.isNotBlank() },
                remoteResult = result
            )
        }

    return byPlaylist.values
        .sortedWith(
            compareByDescending<SearchPlaylistHit> { it.score }
                .thenByDescending { it.songCount ?: 0 }
                .thenBy { stableRank(normalizedQuery, it.key) }
        )
}

private fun buildAlbumHits(
    query: String,
    localSongs: List<Song>,
    onlineResults: List<YouTubeResult>
): List<SearchAlbumHit> {
    val normalizedQuery = normalizeForSearch(query)
    if (normalizedQuery.isBlank()) return emptyList()
    val tokens = expandedSearchTokens(normalizedQuery)
    val byAlbum = LinkedHashMap<String, SearchAlbumHit>()

    localSongs
        .filter { isKnownAlbum(it.album) }
        .groupBy { "${normalizeForSearch(it.album)}|${normalizeForSearch(it.artist)}" }
        .forEach { (_, albumSongs) ->
            val first = albumSongs.first()
            val score = scoreText(first.album, normalizedQuery, tokens, 1.25f) +
                    scoreText(first.artist, normalizedQuery, tokens, 0.9f) +
                    albumSongs.sumOf { scoreSong(it, normalizedQuery, tokens).coerceAtMost(12) }
            if (score <= 0) return@forEach
            val sortedSongs = albumSongs.sortedByDescending { it.dateAdded }
            val key = "${normalizeForSearch(first.album)}|${normalizeForSearch(first.artist)}"
            byAlbum[key] = SearchAlbumHit(
                name = first.album,
                artist = first.artist,
                score = score,
                source = SearchSource.LIBRARY,
                localSongs = sortedSongs,
                thumbnailUrl = sortedSongs.firstNotNullOfOrNull { bestSongArtwork(it, null) }
            )
        }

    onlineResults
        .filter { it.type == ResultType.ALBUM }
        .forEach { result ->
            val score = scoreRemoteResult(result, normalizedQuery, tokens)
            if (score <= 0) return@forEach
            val key = "${normalizeForSearch(result.title)}|${normalizeForSearch(result.artist)}"
            val existing = byAlbum[key]
            if (existing == null || existing.source != SearchSource.LIBRARY) {
                byAlbum[key] = SearchAlbumHit(
                    name = result.title,
                    artist = result.artist,
                    score = maxOf(existing?.score ?: 0, score),
                    source = SearchSource.ONLINE,
                    thumbnailUrl = result.thumbnailUrl.takeIf { it.isNotBlank() },
                    remoteResult = result
                )
            }
        }

    return byAlbum.values
        .sortedWith(
            compareByDescending<SearchAlbumHit> { it.score }
                .thenByDescending { it.localSongs.size }
                .thenBy { stableRank(normalizedQuery, it.key) }
        )
}

private fun buildRecentSearchCards(
    history: List<PlaybackHistoryEntity>,
    localSongs: List<Song>
): List<SearchRecentCard> {
    val songsById = localSongs.associateBy { it.id.toString() }
    val songsByMetadata = localSongs.associateBy { metadataKey(it.title, it.artist) }
    val seen = mutableSetOf<String>()

    return history.mapNotNull { item ->
        val key = metadataKey(item.title, item.artist)
        if (!seen.add(key)) return@mapNotNull null
        val song = songsById[item.songId] ?: songsByMetadata[key]
        SearchRecentCard(
            query = listOf(item.title, item.artist).filter { it.isNotBlank() }.joinToString(" "),
            title = item.title,
            subtitle = item.artist,
            imageUrl = song?.let { bestSongArtwork(it, item.artworkUrl) } ?: item.artworkUrl?.takeIf { it.isNotBlank() },
            key = key
        )
    }
}

private fun buildSmartSearchSuggestions(
    searchHistory: List<String>,
    playbackHistory: List<PlaybackHistoryEntity>,
    localSongs: List<Song>
): List<String> {
    val suggestions = LinkedHashSet<String>()
    val blocked = searchHistory
        .map { normalizeForSearch(it) }
        .filter { it.isNotBlank() }
        .toSet()

    playbackHistory
        .asSequence()
        .map { it.artist.trim() }
        .filter { isKnownArtist(it) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .filter { normalizeForSearch(it) !in blocked }
        .forEach { suggestions += it }

    localSongs
        .asSequence()
        .filter { isKnownAlbum(it.album) }
        .groupBy { it.album.trim() }
        .entries
        .sortedByDescending { it.value.size }
        .map { it.key }
        .filter { normalizeForSearch(it) !in blocked }
        .forEach { suggestions += it }

    localSongs
        .asSequence()
        .sortedByDescending { it.dateAdded }
        .map { it.title.trim() }
        .filter { it.length > 1 }
        .filter { normalizeForSearch(it) !in blocked }
        .forEach { suggestions += it }

    return suggestions.take(SEARCH_RECOMMENDATION_LIMIT)
}

private fun scoreSong(
    song: Song,
    normalizedQuery: String,
    tokens: Set<String>
): Int {
    return scoreText(song.title, normalizedQuery, tokens, 1.35f) +
            scoreText(song.artist, normalizedQuery, tokens, 1.0f) +
            scoreText(song.album, normalizedQuery, tokens, 0.7f)
}

private fun scoreRemoteResult(
    result: YouTubeResult,
    normalizedQuery: String,
    tokens: Set<String>
): Int {
    val typeBoost = when (result.type) {
        ResultType.SONG -> 10
        ResultType.ARTIST -> 9
        ResultType.PLAYLIST -> 8
        ResultType.ALBUM -> 7
    }
    return typeBoost +
            scoreText(result.title, normalizedQuery, tokens, 1.25f) +
            scoreText(result.artist, normalizedQuery, tokens, 0.9f)
}

private fun scoreText(
    value: String,
    normalizedQuery: String,
    tokens: Set<String>,
    weight: Float
): Int {
    val normalizedValue = normalizeForSearch(value)
    if (normalizedValue.isBlank()) return 0

    var score = 0
    if (normalizedValue == normalizedQuery) score += 72
    if (normalizedValue.startsWith(normalizedQuery)) score += 46
    if (normalizedValue.contains(normalizedQuery)) score += 36

    val matchedTokens = tokens.count { token ->
        normalizedValue.split(" ").any { word -> word == token || word.startsWith(token) || word.contains(token) }
    }
    tokens.forEach { token ->
        if (normalizedValue.startsWith(token)) score += 18
        if (normalizedValue.contains(token)) score += 11
    }
    if (tokens.isNotEmpty()) {
        score += ((matchedTokens.toFloat() / tokens.size.toFloat()) * 22).toInt()
    }

    return (score * weight).toInt()
}

private val searchStopWords = setOf(
    "the", "a", "an", "el", "la", "los", "las", "de", "del", "of", "and", "y",
    "music", "musica", "song", "songs", "cancion", "canciones", "official", "audio", "video"
)

private fun expandedSearchTokens(value: String): Set<String> {
    val tokens = value
        .split(" ")
        .filter { it.length > 1 && it !in searchStopWords }
        .toMutableSet()

    if ("brazilian" in tokens) tokens += setOf("brasil", "brasileiro", "brasileira")
    if ("brasilian" in tokens) tokens += setOf("brasil", "brazilian")
    if ("slowed" in tokens) tokens += setOf("slow", "reverb")
    if ("nightcore" in tokens) tokens += setOf("sped", "speed")
    if ("phonk" in tokens) tokens += setOf("drift", "phonky")
    return tokens
}

private fun normalizeForSearch(value: String?): String {
    val withoutMarks = Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    return withoutMarks
        .lowercase()
        .replace(Regex("(?i)\\.(mp3|m4a|wav|flac|ogg)$"), " ")
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("(?i)\\b(feat|ft|featuring)\\b\\.?"), " ")
        .replace(Regex("(?i)\\b(official|video|audio|lyrics|lyric|visualizer|remaster|remastered|explicit|clean|edit)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun metadataKey(title: String, artist: String): String =
    "${normalizeForSearch(title)}|${normalizeForSearch(artist)}"

private fun stableRank(query: String, key: String): Int =
    kotlin.math.abs("$query|$key".hashCode())

private fun bestSongArtwork(song: Song, remoteThumbnail: String?): String? =
    remoteThumbnail?.takeIf { it.isNotBlank() }
        ?: song.artworkUri.toString().takeIf { it.isNotBlank() }

private fun YouTubeResult.toSearchSong(): Song =
    Song(
        id = videoId.hashCode().toLong(),
        title = title,
        artist = artist,
        data = videoId,
        duration = 0L,
        albumId = 0L,
        uri = Uri.parse("https://music.youtube.com/watch?v=$videoId"),
        artworkUri = thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
    )

private fun isKnownArtist(artist: String): Boolean {
    val normalized = normalizeForSearch(artist)
    return normalized.isNotBlank() &&
            normalized !in setOf("unknown", "unknown artist", "artista", "artista desconocido", "youtube")
}

private fun isKnownAlbum(album: String): Boolean {
    val normalized = normalizeForSearch(album)
    return normalized.isNotBlank() &&
            normalized !in setOf("unknown", "unknown album", "album desconocido", "sin album")
}

@Composable
private fun favoritePlaylistNames(): Set<String> =
    setOf(stringResource(R.string.favorites_playlist_name), "Me gusta", "Favorites")

private fun toast(context: android.content.Context, resId: Int) {
    Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
}

private fun shareSearchSong(
    context: android.content.Context,
    song: Song,
    fallbackUrl: String?
) {
    shareSongAudioOrLink(context, song, fallbackUrl)
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
            if (song.artist.isNotBlank()) append(" - ${song.artist}")
        }
        if (songs.size > 12) append("\n...")
    }
    shareText(context, playlist.name, text)
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
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
