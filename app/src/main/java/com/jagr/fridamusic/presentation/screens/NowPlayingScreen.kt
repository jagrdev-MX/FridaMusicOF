package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.RepeatMode

/**
 * [NowPlayingScreen] is the main playback interface.
 * Features:
 * - Dynamic Neon Glow backgrounds.
 * - Glassmorphism UI components.
 * - Real-time playback synchronization.
 */
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    albumArtUrl: String?,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A0A2E), Color(0xFF0A0A0A)),
                    center = androidx.compose.ui.geometry.Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White)
            }
            Text(
                text = "NOW PLAYING",
                style = LiquidTypography.labelSmall,
                color = LiquidPrimary,
                letterSpacing = 2.sp
            )
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .aspectRatio(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(LiquidPrimary.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                    .blur(30.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.DarkGray)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                if (albumArtUrl != null) {
                    AsyncImage(
                        model = albumArtUrl,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = currentSong?.title ?: "No Song Playing",
                    style = LiquidTypography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.artist ?: "Unknown Artist",
                    style = LiquidTypography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = LiquidPrimary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        SeekBarSection(currentSong = currentSong, currentPosition = currentPosition, onSeek = onSeek)

        PlayerControlsSection(
            isPlaying = isPlaying,
            repeatMode = repeatMode,
            onPlayPause = onPlayPause,
            onToggleRepeat = onToggleRepeat
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = Color.White.copy(alpha = 0.5f))
            Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.5f))
            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White.copy(alpha = 0.5f))
        }
        }
    }
}

@Composable
fun SeekBarSection(currentSong: Song?, currentPosition: Long, onSeek: (Long) -> Unit) {
    val totalDuration = currentSong?.duration ?: 0L
    var sliderPosition by remember { mutableStateOf<Float?>(null) }

    val currentProgress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
    val displayProgress = sliderPosition ?: currentProgress

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF99CC), Color(0xFFBBB0FD))
                            )
                        )
                )
            }

            Slider(
                value = displayProgress,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    sliderPosition?.let {
                        onSeek((it * totalDuration).toLong())
                    }
                    sliderPosition = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayTime = (displayProgress * totalDuration).toLong()
            Text(
                text = formatTime(displayTime),
                style = LiquidTypography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "-${formatTime(maxOf(0L, totalDuration - displayTime))}",
                style = LiquidTypography.labelSmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PlayerControlsSection(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { /* TODO */ }) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = { /* TODO */ }) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(LiquidPrimary.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        IconButton(onClick = { /* Next */ }) {
            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        val repeatIcon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
        val repeatTint = if (repeatMode == RepeatMode.OFF) Color.White.copy(alpha = 0.5f) else LiquidPrimary

        IconButton(onClick = onToggleRepeat) {
            Icon(
                imageVector = repeatIcon,
                contentDescription = "Repeat",
                tint = repeatTint,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}