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

    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

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
                    SearchScreen(paddingValues = paddingValues, listState = searchListState)
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
                onPlayPause = { libraryViewModel.togglePlayback() },
                onSeek = { position -> libraryViewModel.seekTo(position) },
                onToggleRepeat = { libraryViewModel.toggleRepeatMode()},
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }
}