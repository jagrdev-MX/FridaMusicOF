package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@Composable
fun VitreaBottomNavigation(
    isCollapsed: Boolean,
    currentRoute: String,
    currentSong: Song?,
    isPlaying: Boolean,
    albumArtUrl: String?,
    playbackState: Int,
    isLoading: Boolean,
    errorMessage: String?,
    currentPosition: Long,
    duration: Long,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onNavigate: (String) -> Unit,
    onExpandPlayer: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedVisibility(
            visible = currentSong != null || isLoading || !errorMessage.isNullOrBlank(),
            enter = fadeIn() + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut() + slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            MiniPlayer(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
                currentSong = currentSong,
                isPlaying = isPlaying,
                albumArtUrl = albumArtUrl,
                playbackState = playbackState,
                isLoading = isLoading,
                errorMessage = errorMessage,
                currentPosition = currentPosition,
                duration = duration,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onExpand = onExpandPlayer
            )
        }

        AnimatedVisibility(
            visible = !isCollapsed,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
        ) {
            NativeBottomNavigationBar(
                currentRoute = currentRoute,
                hazeState = hazeState,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
private fun NativeBottomNavigationBar(
    currentRoute: String,
    hazeState: HazeState,
    onNavigate: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val tint = if (isDark) {
        Color(0xFF090A12).copy(alpha = 0.38f)
    } else {
        Color.White.copy(alpha = 0.42f)
    }
    val dividerColor = if (isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentHeight = 56.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(contentHeight + bottomInset)
            .hazeChild(
                state = hazeState,
                style = HazeDefaults.style(
                    tint = tint,
                    blurRadius = 24.dp,
                    noiseFactor = 0.05f
                )
            )
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                activeIcon = Icons.Default.Home,
                inactiveIcon = Icons.Outlined.Home,
                label = stringResource(R.string.nav_home),
                isSelected = currentRoute == "home",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate("home") }
            )
            NavItem(
                activeIcon = Icons.Default.Search,
                inactiveIcon = Icons.Outlined.Search,
                label = stringResource(R.string.nav_search),
                isSelected = currentRoute == "search",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate("search") }
            )
            NavItem(
                activeIcon = Icons.Default.LibraryMusic,
                inactiveIcon = Icons.Outlined.LibraryMusic,
                label = stringResource(R.string.nav_library),
                isSelected = currentRoute == "library",
                modifier = Modifier.weight(1f),
                onClick = { onNavigate("library") }
            )
        }

        Spacer(modifier = Modifier.height(bottomInset))
    }
}

@Composable
private fun NavItem(
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = isSystemInDarkTheme()
    val navBaseColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isSelected) {
        navBaseColor
    } else {
        navBaseColor.copy(alpha = 0.72f)
    }
    val labelColor = if (isSelected) {
        navBaseColor
    } else {
        navBaseColor.copy(alpha = 0.68f)
    }

    Column(
        modifier = modifier
            .height(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveIcon(
            imageVector = if (isSelected) activeIcon else inactiveIcon,
            contentDescription = label,
            tint = iconColor,
            active = isSelected,
            pressed = isPressed,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = labelColor
        )
    }
}
