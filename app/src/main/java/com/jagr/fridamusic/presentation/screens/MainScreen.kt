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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.components.ModernBottomNav
import com.jagr.fridamusic.presentation.components.ModernSideNav
import com.jagr.fridamusic.presentation.components.ModernGlassPlaybar
import com.jagr.fridamusic.presentation.onboarding.OnboardingScreen
import com.jagr.fridamusic.presentation.onboarding.SetupViewModel
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
    val onboardingCompleted by settingsViewModel.onboardingCompleted.collectAsState()
    val useFloatingNavBar by settingsViewModel.useFloatingNavBar.collectAsState()

    if (!onboardingCompleted) {
        val setupViewModel: SetupViewModel = viewModel()
        OnboardingScreen(
            viewModel = setupViewModel,
            settingsViewModel = settingsViewModel,
            libraryViewModel = libraryViewModel,
            onFinish = { settingsViewModel.completeOnboarding() }
        )
        return
    }

    val repeatMode by playbackViewModel.repeatMode.collectAsState()
    val isShuffleMode by playbackViewModel.isShuffleMode.collectAsState()
    val currentSong by playbackViewModel.currentSong.collectAsState()
    val isPlaying by playbackViewModel.isPlaying.collectAsState()
    val keepScreenOn by settingsViewModel.keepScreenOn.collectAsState()
    val currentAlbumArt = remember(currentSong) { currentSong?.artworkUri?.toString() }
    val lyricsLines = remember(currentSong) { 
        currentSong?.lyrics?.let { com.jagr.fridamusic.domain.lyrics.LyricsParser.parseLrc(it) } ?: emptyList() 
    }
    val currentPositionState = playbackViewModel.currentPosition.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var libraryReselectSignal by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(currentRoute) {
        if (currentRoute in setOf("home", "search", "library") || currentRoute.startsWith("library/")) {
            selectedTopLevelRoute = if (currentRoute.startsWith("library")) "library" else currentRoute
        }
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isTablet && currentRoute != "settings") {
                ModernSideNav(
                    currentRoute = selectedTopLevelRoute,
                    userProfileUrl = null,
                    onNavigate = { route ->
                        if (route != selectedTopLevelRoute) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Scaffold(
                    bottomBar = {
                        if (!isTablet && currentRoute != "settings") {
                            ModernBottomNav(
                                currentRoute = selectedTopLevelRoute,
                                useFloatingStyle = useFloatingNavBar,
                                onNavigate = { route ->
                                    if (route == selectedTopLevelRoute) {
                                        if (route == "search") searchFocusSignal++
                                        else if (route == "library") libraryReselectSignal++
                                    } else {
                                        searchFocusSignal = 0
                                        navController.navigate(route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
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
                                onOpenLibrarySection = { section -> navController.navigate("library/$section") }
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
                                    val encUrl = URLEncoder.encode(url.ifBlank { "none" }, "UTF-8")
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
                                            uri = "https://music.youtube.com/watch?v=${result.videoId}".toUri(),
                                            artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.toUri() ?: Uri.EMPTY
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
                                            uri = "https://www.youtube.com/playlist?list=${result.videoId}".toUri(),
                                            artworkUri = result.thumbnailUrl.takeIf { it.isNotBlank() }?.toUri() ?: Uri.EMPTY
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

                // Modern Glass Playbar integration
                if (currentRoute != "settings" && currentSong != null) {
                    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = (if (isTablet) 24.dp else 92.dp) + navBarPadding)
                    ) {
                        ModernGlassPlaybar(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            artworkUrl = currentAlbumArt,
                            onPlayPause = { playbackViewModel.togglePlayback() },
                            onNext = { playbackViewModel.skipToNext() },
                            onPrevious = { playbackViewModel.skipToPrevious() },
                            onFavoriteClick = { /* Handle favorite */ },
                            onExpand = { isPlayerExpanded = true }
                        )
                    }
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
                onPlayPause = { playbackViewModel.togglePlayback() },
                onNext = { playbackViewModel.skipToNext() },
                onPrevious = { playbackViewModel.skipToPrevious() },
                onSeek = { playbackViewModel.seekTo(it) },
                onToggleRepeat = { playbackViewModel.toggleRepeatMode() },
                onToggleShuffle = { playbackViewModel.toggleShuffleMode() },
                onCollapse = { isPlayerExpanded = false },
                viewModel = libraryViewModel,
                playbackViewModel = playbackViewModel,
                settingsViewModel = settingsViewModel
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
