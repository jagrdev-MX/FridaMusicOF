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
import androidx.compose.runtime.*
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
import com.jagr.fridamusic.domain.lyrics.LyricsResult
import com.jagr.fridamusic.presentation.components.VitreaBottomNavigation
import com.jagr.fridamusic.presentation.viewmodels.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = viewModel()
    val playbackViewModel: PlaybackViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val repeatMode by playbackViewModel.repeatMode.collectAsState()
    val isShuffleMode by playbackViewModel.isShuffleMode.collectAsState()
    val currentSong by playbackViewModel.currentSong.collectAsState()
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val playbackState by playbackViewModel.playbackState.collectAsState()
    val keepScreenOn by settingsViewModel.keepScreenOn.collectAsState()
    val currentAlbumArt = remember(currentSong) { currentSong?.artworkUri?.toString() }
    val lyricsResult by produceState<LyricsResult>(initialValue = LyricsResult.NotAvailable, currentSong) {
        val song = currentSong
        value = if (song == null) {
            LyricsResult.NotAvailable
        } else {
            LyricsResult.Loading
        }
        if (song != null) {
            value = libraryViewModel.getLyricsResult(song)
        }
    }
    val currentPositionState = playbackViewModel.currentPosition.collectAsState()
    val durationState = playbackViewModel.duration.collectAsState()
    val isLoading by playbackViewModel.isLoading.collectAsState()
    val errorMessage by playbackViewModel.errorMessage.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var libraryReselectSignal by remember { mutableIntStateOf(0) }
    var searchFocusSignal by remember { mutableIntStateOf(0) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val selectedTopLevelRoute = when {
        currentRoute.startsWith("library") -> "library"
        currentRoute in setOf("home", "search") -> currentRoute
        else -> "home"
    }

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
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
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
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        currentPosition = currentPositionState.value,
                        duration = durationState.value,
                        hazeState = hazeState,
                        onPlayPause = { playbackViewModel.togglePlayback() },
                        onNext = { playbackViewModel.skipToNext() },
                        onPrevious = { playbackViewModel.skipToPrevious() },
                        onNavigate = { route ->
                            if (route == selectedTopLevelRoute && currentRoute == route) {
                                if (route == "search") {
                                    searchFocusSignal++
                                } else if (route == "library") {
                                    libraryReselectSignal++
                                }
                            } else {
                                searchFocusSignal = 0
                                when (route) {
                                    "home" -> {
                                        navController.navigate("home") {
                                            popUpTo("home") {
                                                inclusive = false
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                    "library" -> {
                                        navController.navigate("library") {
                                            popUpTo("home") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                    else -> {
                                        navController.navigate(route) {
                                            popUpTo("home") {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
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
                    HomeScreen(
                        paddingValues = paddingValues,
                        listState = homeListState,
                        songs = songs,
                        viewModel = libraryViewModel,
                        playbackViewModel = playbackViewModel,
                        onSongClick = { playbackViewModel.playSong(it, songs) },
                        onNavigateToSettings = { navController.navigate("settings") },
                        onOpenLibrarySection = { section ->
                            navController.navigate("library/${URLEncoder.encode(section, "UTF-8")}") {
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    )
                }

                composable("search") {
                    SearchScreen(
                        paddingValues = paddingValues,
                        listState = searchListState,
                        viewModel = searchViewModel,
                        libraryViewModel = libraryViewModel,
                        playbackViewModel = playbackViewModel,
                        focusSignal = searchFocusSignal,
                        onNavigateToArtist = { name, url ->
                            val encName = URLEncoder.encode(name, "UTF-8")
                            val encUrl = URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                            navController.navigate("artist?name=$encName&url=$encUrl")
                        }
                    )
                }

                composable("artist?name={artistName}&url={artistUrl}") { backStackEntry ->
                    val name = URLDecoder.decode(backStackEntry.arguments?.getString("artistName") ?: "", "UTF-8")
                    val rawUrl = URLDecoder.decode(backStackEntry.arguments?.getString("artistUrl") ?: "", "UTF-8")
                    val songs by libraryViewModel.songs.collectAsState()
                    val playlists by libraryViewModel.playlists.collectAsState(initial = emptyList())
                    val onlineResults by searchViewModel.youtubeSearchResults.collectAsState()
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
                            playbackViewModel.playSong(
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
                        viewModel = libraryViewModel,
                        playbackViewModel = playbackViewModel,
                        initialSection = null
                    )
                }

                composable("library/{section}") { backStackEntry ->
                    LibraryScreen(
                        paddingValues = paddingValues,
                        reselectSignal = libraryReselectSignal,
                        viewModel = libraryViewModel,
                        playbackViewModel = playbackViewModel,
                        initialSection = backStackEntry.arguments?.getString("section")
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        paddingValues = paddingValues,
                        listState = settingsListState,
                        viewModel = settingsViewModel,
                        playbackViewModel = playbackViewModel,
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
                lyricsResult = lyricsResult,
                onPlayPause = { playbackViewModel.togglePlayback() },
                onNext = { playbackViewModel.skipToNext() },
                onPrevious = { playbackViewModel.skipToPrevious() },
                onSeek = { playbackViewModel.seekTo(it) },
                onToggleRepeat = { playbackViewModel.toggleRepeatMode() },
                onToggleShuffle = { playbackViewModel.toggleShuffleMode() },
                onCollapse = { isPlayerExpanded = false },
                viewModel = libraryViewModel,
                playbackViewModel = playbackViewModel,
                settingsViewModel = settingsViewModel,
                onOpenArtist = { artist ->
                    isPlayerExpanded = false
                    val encName = URLEncoder.encode(artist, "UTF-8")
                    navController.navigate("artist?name=$encName&url=none") {
                        launchSingleTop = true
                    }
                },
                onOpenAlbum = { song ->
                    isPlayerExpanded = false
                    val section = if (song.albumId > 0L) {
                        "ALBUM_ID_${song.albumId}"
                    } else {
                        "ALBUM_NAME_${song.album}"
                    }
                    navController.navigate("library/${URLEncoder.encode(section, "UTF-8")}") {
                        launchSingleTop = true
                        restoreState = false
                    }
                }
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
