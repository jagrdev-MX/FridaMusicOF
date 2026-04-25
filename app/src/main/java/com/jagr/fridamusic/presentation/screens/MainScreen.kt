package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jagr.fridamusic.presentation.components.VitreaBottomNavigation
import com.jagr.fridamusic.presentation.theme.LiquidBackground
import com.jagr.fridamusic.presentation.theme.LiquidSurfaceContainer

@Composable
fun MainScreen() {
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()

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
                    HomeScreen(paddingValues = paddingValues, listState = homeListState)
                }
                composable("search") {
                    SearchScreen(paddingValues = paddingValues, listState = searchListState)
                }
                composable("library") {
                    LibraryScreen(paddingValues = paddingValues, listState = libraryListState)
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
            NowPlayingScreen(onCollapse = { isPlayerExpanded = false })
        }
    }
}