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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.mutableStateOf
import com.jagr.fridamusic.domain.model.Song
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.presentation.theme.*

@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    albumArtUrl: String?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCollapse: () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > 150f) {
                            onCollapse()
                        }
                        offsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount
                    }
                )
            }
    ) {
        AmbientBackgroundGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NowPlayingTopBar(onCollapse = onCollapse)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlbumArtSection(albumArtUrl = albumArtUrl, modifier = Modifier.weight(1f, fill = false))

                Column(
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    TrackInfoSection(currentSong = currentSong)
                    SeekBarSection(
                        currentSong = currentSong,
                        currentPosition = currentPosition,
                        onSeek = onSeek)
                    PlayerControlsSection(isPlaying = isPlaying, onPlayPause = onPlayPause)
                }
            }
        }

        NowPlayingBottomBar(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun AmbientBackgroundGlow() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = (-50).dp)
                .size(300.dp)
                .background(LiquidPrimary.copy(alpha = 0.15f), CircleShape)
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .size(350.dp)
                .background(LiquidTertiary.copy(alpha = 0.15f), CircleShape)
                .blur(100.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .background(Color(0xFFFFAFD5).copy(alpha = 0.05f), CircleShape)
                .blur(80.dp)
        )
    }
}

@Composable
fun NowPlayingTopBar(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White.copy(alpha = 0.6f))
        }
        Text(
            text = "NOW PLAYING",
            style = LiquidTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFFFF99CC),
            letterSpacing = 1.sp
        )
        IconButton(onClick = {  }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun AlbumArtSection(albumArtUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
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
                .background(LiquidSurfaceContainer.copy(alpha = 0.3f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
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
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            }
        }
    }
}

@Composable
fun TrackInfoSection(currentSong: Song?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong?.title ?: "Not Playing",
                style = LiquidTypography.displayLarge.copy(fontSize = 32.sp),
                color = LiquidOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong?.artist ?: "Unknown Artist",
                style = LiquidTypography.bodyLarge,
                color = LiquidOnSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = {  }) {
            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = Color(0xFFFF99CC), modifier = Modifier.size(28.dp))
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
                    .background(LiquidSurfaceHigh)
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
                color = LiquidOnSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "-${formatTime(maxOf(0L, totalDuration - displayTime))}",
                style = LiquidTypography.labelSmall,
                color = LiquidOnSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PlayerControlsSection(isPlaying: Boolean, onPlayPause: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Refresh, contentDescription = "Shuffle", tint = LiquidOnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(28.dp).clickable {  })
        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = LiquidOnSurface, modifier = Modifier.size(40.dp).clickable {  })
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = LiquidOnSurface, modifier = Modifier.size(40.dp).clickable {  })
        Icon(Icons.Default.Refresh, contentDescription = "Repeat", tint = LiquidOnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(28.dp).clickable {  })
    }
}

@Composable
fun NowPlayingBottomBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {  }) {
            Icon(Icons.Default.List, contentDescription = "Queue", tint = Color(0xFFFF99CC))
        }
        IconButton(onClick = {  }) {
            Icon(Icons.Default.Subject, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.4f))
        }
        IconButton(onClick = {  }) {
            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White.copy(alpha = 0.4f))
        }
    }
}