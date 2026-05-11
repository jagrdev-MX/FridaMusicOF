package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.jagr.fridamusic.presentation.components.VitreaBottomNavigation
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModels = viewModel()

    val repeatMode by libraryViewModel.repeatMode.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState(initial = emptyList())
    val currentSong by libraryViewModel.currentSong.collectAsState()
    val isPlaying by libraryViewModel.isPlaying.collectAsState()
    val keepScreenOn by libraryViewModel.keepScreenOn.collectAsState()
    val currentAlbumArt by libraryViewModel.currentAlbumArt.collectAsState()
    val lyricsLines by libraryViewModel.lyricsLines.collectAsState()
    val currentPositionState = libraryViewModel.currentPosition.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted) { libraryViewModel.loadSongs() }
    }

    val isCurrentSongLiked by remember(currentSong, playlists) {
        derivedStateOf {
            val likedPlaylist = playlists.find { it.name == "Me gusta" }
            currentSong != null && likedPlaylist?.songIds?.contains(currentSong!!.id) == true
        }
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

    var isPlayerExpanded by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()

    val fluidBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (currentRoute != "settings") {
                    VitreaBottomNavigation(
                        isCollapsed = false,
                        currentRoute = currentRoute,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        albumArtUrl = currentAlbumArt,
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
            NavHost(navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize()) {
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
                            val encName = java.net.URLEncoder.encode(name, "UTF-8")
                            val encUrl = java.net.URLEncoder.encode(if (url.isBlank()) "none" else url, "UTF-8")
                            navController.navigate("artist?name=$encName&url=$encUrl")
                        }
                    )
                }
                composable("artist?name={artistName}&url={artistUrl}") { backStackEntry ->
                    val name = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("artistName") ?: "", "UTF-8")
                    val rawUrl = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("artistUrl") ?: "", "UTF-8")
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
                    LibraryScreen(paddingValues = paddingValues, listState = libraryListState, viewModel = libraryViewModel)
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
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            NowPlayingScreen(
                currentSong = currentSong,
                isPlaying = isPlaying,
                isLiked = isCurrentSongLiked,
                currentPosition = { currentPositionState.value },
                albumArtUrl = currentAlbumArt,
                repeatMode = repeatMode,
                lyricsLines = lyricsLines,
                onPlayPause = { libraryViewModel.togglePlayback() },
                onNext = { libraryViewModel.skipToNext() },
                onPrevious = { libraryViewModel.skipToPrevious() },
                onSeek = { libraryViewModel.seekTo(it) },
                onToggleRepeat = { libraryViewModel.toggleRepeatMode() },
                onToggleLike = { currentSong?.let { libraryViewModel.toggleLike(it) } },
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }
}