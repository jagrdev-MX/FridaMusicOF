package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.components.VitreaBottomNavigation
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModels = viewModel()

    val repeatMode by libraryViewModel.repeatMode.collectAsState()
    val isShuffleMode by libraryViewModel.isShuffleMode.collectAsState()
    val currentSong by libraryViewModel.currentSong.collectAsState()
    val isPlaying by libraryViewModel.isPlaying.collectAsState()
    val playbackState by libraryViewModel.playbackState.collectAsState()
    val keepScreenOn by libraryViewModel.keepScreenOn.collectAsState()
    val currentAlbumArt by libraryViewModel.currentAlbumArt.collectAsState()
    val lyricsLines by libraryViewModel.lyricsLines.collectAsState()
    val currentPositionState = libraryViewModel.currentPosition.collectAsState()
    val durationState = libraryViewModel.duration.collectAsState()
    val isExtracting by libraryViewModel.isExtracting.collectAsState()
    val errorMessage by libraryViewModel.errorMessage.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var libraryReselectSignal by remember { mutableIntStateOf(0) }
    var libraryHistorySignal by remember { mutableIntStateOf(0) }
    var searchFocusSignal by remember { mutableIntStateOf(0) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    var selectedTopLevelRoute by remember { mutableStateOf("home") }

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    val bgPrimary = MaterialTheme.colorScheme.background
    val bgSurface = MaterialTheme.colorScheme.surface
    val fluidBackground = remember(bgPrimary, bgSurface) {
        Brush.verticalGradient(
            colors = listOf(bgPrimary, bgSurface, bgPrimary.copy(alpha = 0.9f))
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val audioGranted = permissions[audioPermission] == true
        if (audioGranted) { libraryViewModel.loadSongs() }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(keepScreenOn) {
        val activity = context as? Activity
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute in setOf("home", "search", "library")) {
            selectedTopLevelRoute = currentRoute
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (currentRoute != "settings") {
                    VitreaBottomNavigation(
                        isCollapsed = false,
                        currentRoute = selectedTopLevelRoute,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        albumArtUrl = currentAlbumArt,
                        playbackState = playbackState,
                        isLoading = isExtracting,
                        errorMessage = errorMessage,
                        currentPosition = currentPositionState.value,
                        duration = durationState.value,
                        hazeState = hazeState,
                        onPlayPause = { libraryViewModel.togglePlayback() },
                        onNext = { libraryViewModel.skipToNext() },
                        onPrevious = { libraryViewModel.skipToPrevious() },
                        onNavigate = { route ->
                            if (route == "search") {
                                searchFocusSignal++
                            }
                            if (route == selectedTopLevelRoute && route == "library") {
                                libraryReselectSignal++
                            } else {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onExpandPlayer = { isPlayerExpanded = true }
                    )
                }
            },
            containerColor = Color.Transparent,
            modifier = Modifier.background(fluidBackground)
        ) { paddingValues ->

            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .haze(state = hazeState)
            ) {
                composable("home") {
                    val songs by libraryViewModel.songs.collectAsState()
                    val recentHistory by libraryViewModel.recentHistory.collectAsState()
                    val fullHistory by libraryViewModel.fullHistory.collectAsState()
                    val playlists by libraryViewModel.playlists.collectAsState(initial = emptyList())
                    HomeScreen(
                        paddingValues = paddingValues,
                        listState = homeListState,
                        songs = songs,
                        currentSong = currentSong,
                        recentHistory = recentHistory,
                        fullHistory = fullHistory,
                        playlists = playlists,
                        viewModel = libraryViewModel,
                        onSongClick = { libraryViewModel.playSong(it) },
                        onHistoryClick = { libraryViewModel.playHistoryItem(it) },
                        onSeeAllHistory = {
                            libraryHistorySignal++
                            navController.navigate("library") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToArtist = { name, url ->
                            val encName = URLEncoder.encode(name, "UTF-8")
                            val encUrl = URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                            navController.navigate("artist?name=$encName&url=$encUrl")
                        },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }

                composable("search") {
                    SearchScreen(
                        paddingValues = paddingValues,
                        listState = searchListState,
                        viewModel = libraryViewModel,
                        focusSignal = searchFocusSignal,
                        onNavigateToArtist = { name, url ->
                            val encName = URLEncoder.encode(name, "UTF-8")
                            val encUrl = URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                            navController.navigate("artist?name=$encName&url=$encUrl")
                        },
                        onNavigateToAlbum = { title, artist, url ->
                            val encTitle = URLEncoder.encode(title, "UTF-8")
                            val encArtist = URLEncoder.encode(artist, "UTF-8")
                            val encUrl = URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                            navController.navigate("album?title=$encTitle&artist=$encArtist&url=$encUrl")
                        }
                    )
                }

                composable("artist?name={artistName}&url={artistUrl}") { backStackEntry ->
                    val name = URLDecoder.decode(backStackEntry.arguments?.getString("artistName") ?: "", "UTF-8")
                    val rawUrl = URLDecoder.decode(backStackEntry.arguments?.getString("artistUrl") ?: "", "UTF-8")
                    val songs by libraryViewModel.songs.collectAsState()
                    val playlists by libraryViewModel.playlists.collectAsState(initial = emptyList())
                    val onlineResults by libraryViewModel.youtubeSearchResults.collectAsState()
                    val playlistLabel = stringResource(R.string.playlist_label)
                    val normalizedArtistName = remember(name) { normalizeRouteArtistName(name) }
                    val localArtistSongs = remember(songs, normalizedArtistName) {
                        songs.filter { normalizeRouteArtistName(it.artist) == normalizedArtistName }
                    }
                    val onlineArtistSongs = remember(onlineResults, normalizedArtistName) {
                        onlineResults
                            .filter {
                                it.type == com.jagr.fridamusic.data.remote.innertube.ResultType.SONG &&
                                    normalizeRouteArtistName(it.artist).let { artist ->
                                        artist.isNotBlank() && (
                                            artist == normalizedArtistName ||
                                            artist.contains(normalizedArtistName) ||
                                            normalizedArtistName.contains(artist)
                                        )
                                    }
                            }
                            .map { result ->
                                com.jagr.fridamusic.domain.model.Song(
                                    id = result.videoId.hashCode().toLong(),
                                    title = result.title,
                                    artist = result.artist,
                                    data = result.videoId,
                                    duration = 0L,
                                    albumId = 0L,
                                    uri = Uri.parse("https://music.youtube.com/watch?v=${result.videoId}"),
                                    artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
                                )
                            }
                    }
                    val localArtistPlaylists = remember(playlists, localArtistSongs, playlistLabel) {
                        val artistSongIds = localArtistSongs.map { it.id }.toSet()
                        playlists
                            .filter { playlist -> playlist.songIds.any { it in artistSongIds } }
                            .map { playlist ->
                                com.jagr.fridamusic.domain.model.Song(
                                    id = playlist.id,
                                    title = playlist.name,
                                    artist = playlistLabel,
                                    data = playlist.id.toString(),
                                    duration = 0L,
                                    albumId = 0L,
                                    uri = Uri.EMPTY,
                                    artworkUri = Uri.EMPTY
                                )
                            }
                    }
                    val onlineArtistPlaylists = remember(onlineResults, normalizedArtistName, playlistLabel) {
                        onlineResults
                            .filter {
                                it.type == com.jagr.fridamusic.data.remote.innertube.ResultType.PLAYLIST &&
                                    normalizeRouteArtistName("${it.title} ${it.artist}").contains(normalizedArtistName)
                            }
                            .map { result ->
                                com.jagr.fridamusic.domain.model.Song(
                                    id = result.videoId.hashCode().toLong(),
                                    title = result.title,
                                    artist = playlistLabel,
                                    data = result.videoId,
                                    duration = 0L,
                                    albumId = 0L,
                                    uri = Uri.parse("https://www.youtube.com/playlist?list=${result.videoId}"),
                                    artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY
                                )
                            }
                    }

                    ArtistScreen(
                        artistName = name,
                        artistImageUrl = if (rawUrl == "none") "" else rawUrl,
                        popularSongs = localArtistSongs.ifEmpty { onlineArtistSongs },
                        popularReleases = localArtistPlaylists.ifEmpty { onlineArtistPlaylists },
                        onBack = { navController.popBackStack() },
                        onPlaySong = { song ->
                            libraryViewModel.playSongFromArtist(
                                song,
                                localArtistSongs.ifEmpty { onlineArtistSongs }
                            )
                        }
                    )
                }

                composable("library") {
                    LibraryScreen(
                        paddingValues = paddingValues,
                        reselectSignal = libraryReselectSignal,
                        openHistorySignal = libraryHistorySignal,
                        viewModel = libraryViewModel
                    )
                }

                composable("album?title={albumTitle}&artist={albumArtist}&url={albumUrl}") { backStackEntry ->
                    val title = URLDecoder.decode(backStackEntry.arguments?.getString("albumTitle") ?: "", "UTF-8")
                    val artist = URLDecoder.decode(backStackEntry.arguments?.getString("albumArtist") ?: "", "UTF-8")
                    val rawUrl = URLDecoder.decode(backStackEntry.arguments?.getString("albumUrl") ?: "", "UTF-8")
                    val songs by libraryViewModel.songs.collectAsState()
                    val normalizedAlbum = remember(title) { normalizeRouteArtistName(title) }
                    val normalizedArtist = remember(artist) { normalizeRouteArtistName(artist) }
                    val albumSongs = remember(songs, normalizedAlbum, normalizedArtist) {
                        songs.filter { song ->
                            normalizeRouteArtistName(song.album) == normalizedAlbum &&
                                (normalizedArtist.isBlank() || normalizeRouteArtistName(song.artist) == normalizedArtist)
                        }
                    }

                    AlbumScreen(
                        albumTitle = title,
                        albumArtist = artist,
                        albumImageUrl = if (rawUrl == "none") "" else rawUrl,
                        songs = albumSongs,
                        paddingValues = paddingValues,
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        paddingValues = paddingValues,
                        listState = settingsListState,
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            NowPlayingScreen(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = { currentPositionState.value },
                albumArtUrl = currentAlbumArt,
                repeatMode = repeatMode,
                isShuffleMode = isShuffleMode,
                lyricsLines = lyricsLines,
                onPlayPause = { libraryViewModel.togglePlayback() },
                onNext = { libraryViewModel.skipToNext() },
                onPrevious = { libraryViewModel.skipToPrevious() },
                onSeek = { libraryViewModel.seekTo(it) },
                onToggleRepeat = { libraryViewModel.toggleRepeatMode() },
                onToggleShuffle = { libraryViewModel.toggleShuffleMode() },
                onCollapse = { isPlayerExpanded = false },
                onNavigateToArtist = { name, url ->
                    isPlayerExpanded = false
                    val encName = URLEncoder.encode(name, "UTF-8")
                    val encUrl = URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                    navController.navigate("artist?name=$encName&url=$encUrl")
                },
                onNavigateToAlbum = { title, artist, url ->
                    isPlayerExpanded = false
                    val encTitle = URLEncoder.encode(title, "UTF-8")
                    val encArtist = URLEncoder.encode(artist, "UTF-8")
                    val encUrl = URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                    navController.navigate("album?title=$encTitle&artist=$encArtist&url=$encUrl")
                },
                viewModel = libraryViewModel
            )
        }
    }
}

private fun normalizeRouteArtistName(value: String): String {
    val withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    return withoutMarks
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
