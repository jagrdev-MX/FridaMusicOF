package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.app.Activity
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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModels = viewModel()

    val repeatMode by libraryViewModel.repeatMode.collectAsState()
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

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    var selectedTopLevelRoute by remember { mutableStateOf("home") }

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
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
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
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
                    .haze(
                        state = hazeState,
                        style = HazeDefaults.style(
                            backgroundColor = MaterialTheme.colorScheme.background
                        )
                    )
            ) {
                composable("home") {
                    val songs by libraryViewModel.songs.collectAsState()
                    HomeScreen(
                        paddingValues = paddingValues,
                        listState = homeListState,
                        songs = songs,
                        currentSong = currentSong,
                        viewModel = libraryViewModel,
                        onSongClick = { libraryViewModel.playSong(it) },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }

                composable("search") {
                    SearchScreen(
                        paddingValues = paddingValues,
                        listState = searchListState,
                        viewModel = libraryViewModel,
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
                    val artistSongs by libraryViewModel.artistSongs.collectAsState()
                    val artistPlaylists by libraryViewModel.artistPlaylists.collectAsState()

                    ArtistScreen(
                        artistName = name,
                        artistImageUrl = if (rawUrl == "none") "" else rawUrl,
                        popularSongs = artistSongs,
                        popularReleases = artistPlaylists,
                        onBack = { navController.popBackStack() },
                        onPlaySong = { song ->
                            libraryViewModel.setShuffleMode(true)
                            libraryViewModel.playYouTubeSong(
                                com.jagr.fridamusic.data.remote.innertube.YouTubeResult(
                                    videoId = song.data,
                                    title = song.title,
                                    artist = song.artist ?: "",
                                    thumbnailUrl = song.artworkUri.toString()
                                )
                            )
                        }
                    )
                }

                composable("library") {
                    LibraryScreen(
                        paddingValues = paddingValues,
                        listState = libraryListState,
                        viewModel = libraryViewModel
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
                lyricsLines = lyricsLines,
                onPlayPause = { libraryViewModel.togglePlayback() },
                onNext = { libraryViewModel.skipToNext() },
                onPrevious = { libraryViewModel.skipToPrevious() },
                onSeek = { libraryViewModel.seekTo(it) },
                onToggleRepeat = { libraryViewModel.toggleRepeatMode() },
                onCollapse = { isPlayerExpanded = false },
                viewModel = libraryViewModel
            )
        }
    }
}
