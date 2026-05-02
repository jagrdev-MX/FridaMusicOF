package com.jagr.fridamusic.presentation.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.jagr.fridamusic.presentation.theme.LiquidBackground
import com.jagr.fridamusic.presentation.theme.LiquidSurfaceContainer
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModels = viewModel()

    val repeatMode by libraryViewModel.repeatMode.collectAsState()
    val currentSong by libraryViewModel.currentSong.collectAsState()
    val isPlaying by libraryViewModel.isPlaying.collectAsState()
    val currentPosition by libraryViewModel.currentPosition.collectAsState()
    val keepScreenOn by libraryViewModel.keepScreenOn.collectAsState()
    val currentAlbumArt by libraryViewModel.currentAlbumArt.collectAsState()
    val lyricsLines by libraryViewModel.lyricsLines.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        if (audioGranted) {
            libraryViewModel.loadSongs()
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

    val isNavCollapsed by remember(currentRoute) {
        derivedStateOf {
            when (currentRoute) {
                "home" -> {
                    val isScrollable = homeListState.layoutInfo.totalItemsCount > homeListState.layoutInfo.visibleItemsInfo.size
                    isScrollable && (homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 20)
                }
                "search" -> {
                    val isScrollable = searchListState.layoutInfo.totalItemsCount > searchListState.layoutInfo.visibleItemsInfo.size
                    isScrollable && (searchListState.firstVisibleItemIndex > 0 || searchListState.firstVisibleItemScrollOffset > 20)
                }
                "library" -> {
                    val isScrollable = libraryListState.layoutInfo.totalItemsCount > libraryListState.layoutInfo.visibleItemsInfo.size
                    isScrollable && (libraryListState.firstVisibleItemIndex > 0 || libraryListState.firstVisibleItemScrollOffset > 20)
                }
                "settings" -> {
                    val isScrollable = settingsListState.layoutInfo.totalItemsCount > settingsListState.layoutInfo.visibleItemsInfo.size
                    isScrollable && (settingsListState.firstVisibleItemIndex > 0 || settingsListState.firstVisibleItemScrollOffset > 20)
                }
                else -> false
            }
        }
    }

    val fluidBackground = Brush.verticalGradient(
        colors = listOf(
            LiquidBackground,
            LiquidSurfaceContainer,
            Color(0xFF050505)
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                VitreaBottomNavigation(
                    isCollapsed = isNavCollapsed,
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
            },
            containerColor = Color.Transparent,
            modifier = Modifier.background(fluidBackground)
        ) { paddingValues ->

            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
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
                        onNavigateToArtist = { name, imageUrl ->
                            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                            val safeUrl = if (imageUrl.isBlank()) "none" else imageUrl
                            val encodedUrl = java.net.URLEncoder.encode(safeUrl, "UTF-8")

                            navController.navigate("artist?name=$encodedName&url=$encodedUrl")
                        }
                    )
                }

                // --- RUTA CON QUERY PARAMETERS ---
                composable("artist?name={artistName}&url={artistUrl}") { backStackEntry ->
                    val rawName = backStackEntry.arguments?.getString("artistName") ?: ""
                    val rawUrl = backStackEntry.arguments?.getString("artistUrl") ?: ""

                    val name = java.net.URLDecoder.decode(rawName, "UTF-8")
                    val decodedUrl = java.net.URLDecoder.decode(rawUrl, "UTF-8")
                    val url = if (decodedUrl == "none") "" else decodedUrl

                    val youtubeResults by libraryViewModel.youtubeSearchResults.collectAsState()

                    // 1. Filtramos las canciones
                    val artistSongs = youtubeResults
                        .filter { it.type == com.jagr.fridamusic.data.remote.innertube.ResultType.SONG }
                        .map { result ->
                            com.jagr.fridamusic.domain.model.Song(
                                id = result.videoId.hashCode().toLong(),
                                title = result.title,
                                artist = result.artist,
                                data = result.videoId,
                                duration = 0L,
                                albumId = 0L,
                                uri = android.net.Uri.parse(""),
                                artworkUri = android.net.Uri.parse(result.thumbnailUrl)
                            )
                        }

                    // 2. NUEVO: Filtramos las listas de reproducción y álbumes
                    val artistPlaylists = youtubeResults
                        .filter { it.type == com.jagr.fridamusic.data.remote.innertube.ResultType.PLAYLIST }
                        .map { result ->
                            com.jagr.fridamusic.domain.model.Song(
                                id = result.videoId.hashCode().toLong(),
                                title = result.title,
                                artist = result.artist, // Aquí viene el uploader o creador de la lista
                                data = result.videoId,
                                duration = 0L,
                                albumId = 0L,
                                uri = android.net.Uri.parse(""),
                                artworkUri = android.net.Uri.parse(result.thumbnailUrl)
                            )
                        }

                    ArtistScreen(
                        artistName = name,
                        artistImageUrl = url,
                        popularSongs = artistSongs,
                        popularReleases = artistPlaylists, // Pasamos los datos reales aquí
                        onBack = { navController.popBackStack() },
                        onPlaySong = { song ->
                            libraryViewModel.setShuffleMode(true)
                            val ytResult = com.jagr.fridamusic.data.remote.innertube.YouTubeResult(
                                videoId = song.data,
                                title = song.title,
                                artist = song.artist ?: "",
                                thumbnailUrl = song.artworkUri.toString(),
                                type = com.jagr.fridamusic.data.remote.innertube.ResultType.SONG
                            )
                            libraryViewModel.playYouTubeSong(ytResult)
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
                        viewModel = libraryViewModel
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isPlayerExpanded,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight }
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight }
            )
        ) {
            NowPlayingScreen(
                currentSong = currentSong,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                albumArtUrl = currentAlbumArt,
                repeatMode = repeatMode,
                lyricsLines = lyricsLines, // <--- Pasamos las letras al reproductor
                onPlayPause = { libraryViewModel.togglePlayback() },
                onNext = { libraryViewModel.skipToNext() },
                onPrevious = { libraryViewModel.skipToPrevious() },
                onSeek = { position -> libraryViewModel.seekTo(position) },
                onToggleRepeat = { libraryViewModel.toggleRepeatMode()},
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }
}