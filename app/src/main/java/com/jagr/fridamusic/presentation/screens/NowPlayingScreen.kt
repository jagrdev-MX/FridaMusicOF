package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.domain.lyrics.LyricsLine
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.RepeatMode

enum class PlayerDisplayMode {
    COVER, LYRICS, QUEUE, INFO
}

@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    albumArtUrl: String?,
    repeatMode: RepeatMode,
    lyricsLines: List<LyricsLine>,
    queue: List<Song>,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onCollapse: () -> Unit
) {
    var displayMode by remember { mutableStateOf(PlayerDisplayMode.COVER) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Blurred Background Artwork
        if (!albumArtUrl.isNullOrEmpty()) {
            AsyncImage(
                model = albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.35f)
            )
        }

        // 2. Sophisticated Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 3. Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        style = LiquidTypography.labelSmall.copy(letterSpacing = 1.sp),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (currentSong?.data?.contains("http") == true) "YouTube Music" else "Local Library",
                        style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                IconButton(onClick = { /* More options */ }) {
                    Icon(Icons.Default.MoreVert, null, tint = Color.White)
                }
            }

            // 4. Main Interactive Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                when (displayMode) {
                    PlayerDisplayMode.COVER -> CoverView(albumArtUrl, currentSong)
                    PlayerDisplayMode.LYRICS -> LyricsView(lyricsLines, currentPosition)
                    PlayerDisplayMode.QUEUE -> QueueView(queue, currentSong)
                    PlayerDisplayMode.INFO -> InfoView(currentSong)
                }
            }

            // 5. Playback Controls & Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Song Title & Artist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    IconButton(onClick = { currentSong?.let { onToggleFavorite(it) } }) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Progress Bar
                val duration = currentSong?.duration ?: 0L
                val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
                
                Slider(
                    value = progress,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentPosition), style = LiquidTypography.labelSmall, color = Color.White.copy(0.5f))
                    Text(formatTime(duration), style = LiquidTypography.labelSmall, color = Color.White.copy(0.5f))
                }

                Spacer(Modifier.height(32.dp))

                // Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleRepeat) {
                        Icon(
                            imageVector = when(repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                RepeatMode.ALL -> Icons.Default.Repeat
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != RepeatMode.OFF) LiquidPrimary else Color.White.copy(0.5f)
                        )
                    }

                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { /* Shuffle logic could go here */ }) {
                        Icon(Icons.Default.Shuffle, null, tint = Color.White.copy(0.5f))
                    }
                }

                Spacer(Modifier.height(48.dp))

                // Bottom Dynamic Tabs (Glassmorphic)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomTabItem(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        isActive = displayMode == PlayerDisplayMode.QUEUE,
                        onClick = { displayMode = if (displayMode == PlayerDisplayMode.QUEUE) PlayerDisplayMode.COVER else PlayerDisplayMode.QUEUE }
                    )
                    BottomTabItem(
                        icon = Icons.Default.Lyrics,
                        isActive = displayMode == PlayerDisplayMode.LYRICS,
                        onClick = { displayMode = if (displayMode == PlayerDisplayMode.LYRICS) PlayerDisplayMode.COVER else PlayerDisplayMode.LYRICS }
                    )
                    BottomTabItem(
                        icon = Icons.Default.Info,
                        isActive = displayMode == PlayerDisplayMode.INFO,
                        onClick = { displayMode = if (displayMode == PlayerDisplayMode.INFO) PlayerDisplayMode.COVER else PlayerDisplayMode.INFO }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomTabItem(icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isActive) Color.White.copy(0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.White else Color.White.copy(0.4f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun CoverView(albumArtUrl: String?, currentSong: Song?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f),
            shape = RoundedCornerShape(24.dp),
            color = Color.DarkGray,
            shadowElevation = 24.dp
        ) {
            if (!albumArtUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = albumArtUrl,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(0.1f), modifier = Modifier.size(120.dp))
                }
            }
        }
    }
}

@Composable
fun LyricsView(lyrics: List<LyricsLine>, currentPosition: Long) {
    // Basic implementation, can be improved with scrolling
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(items = lyrics) { line ->
            val isActive = currentPosition >= line.startTime
            Text(
                text = line.content,
                style = LiquidTypography.headlineSmall,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
fun QueueView(queue: List<Song>, currentSong: Song?) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = queue) { song ->
            val isPlaying = song.id == currentSong?.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = song.title,
                    color = if (isPlaying) LiquidPrimary else Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun InfoView(song: Song?) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Title: ${song?.title ?: "N/A"}", color = Color.White)
        Text("Artist: ${song?.artist ?: "N/A"}", color = Color.White)
        Text("Path: ${song?.data ?: "N/A"}", color = Color.White.copy(0.5f), fontSize = 12.sp)
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}