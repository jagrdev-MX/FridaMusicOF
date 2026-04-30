package com.jagr.fridamusic.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.domain.lyrics.LyricsLine
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.RepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Long,
    albumArtUrl: String?,
    repeatMode: RepeatMode,
    lyricsLines: List<LyricsLine>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onCollapse: () -> Unit
) {
    val isYouTube = currentSong?.uri?.toString()?.startsWith("http") == true
    val hasAnyLyrics = lyricsLines.isNotEmpty() || !currentSong?.lyrics.isNullOrBlank()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isInfiniteQueueEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        if (albumArtUrl != null) {
            AsyncImage(
                model = albumArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
                    .alpha(0.6f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

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
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        style = LiquidTypography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    if (isYouTube) {
                        Text(
                            text = "from YouTube Music",
                            fontSize = 11.sp,
                            color = LiquidPrimary,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                IconButton(onClick = {  }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
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
                            contentDescription = null,
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
                Icon(Icons.Default.Favorite, contentDescription = null, tint = LiquidPrimary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SeekBarSection(currentSong = currentSong, currentPosition = currentPosition, onSeek = onSeek)

            PlayerControlsSection(
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleRepeat = onToggleRepeat
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = { showLyricsSheet = true }) {
                    Icon(Icons.Default.Lyrics, contentDescription = null, tint = if (hasAnyLyrics) Color.White else Color.White.copy(alpha = 0.3f))
                }
                IconButton(onClick = { showInfoSheet = true }) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
            }
        }

        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF121212)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Queue", style = LiquidTypography.titleMedium, color = Color.White)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AllInclusive,
                                contentDescription = null,
                                tint = if (isInfiniteQueueEnabled) LiquidPrimary else Color.White.copy(0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Autoplay",
                                color = if (isInfiniteQueueEnabled) LiquidPrimary else Color.White.copy(0.5f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isInfiniteQueueEnabled,
                                onCheckedChange = { isInfiniteQueueEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = LiquidPrimary,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }

                    Text("NOW PLAYING", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LiquidPrimary.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = albumArtUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.4f)))
                            Icon(Icons.Default.VolumeUp, null, tint = LiquidPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentSong?.title ?: "Unknown", color = LiquidPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentSong?.artist ?: "Unknown", color = LiquidPrimary.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("UP NEXT", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isInfiniteQueueEnabled) Icons.Default.AllInclusive else Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = Color.White.copy(0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isInfiniteQueueEnabled)
                                    "Infinite Mode is ON.\nSimilar songs will play automatically after the queue ends."
                                else
                                    "Your queue is empty.\nTurn on Autoplay to keep the music going forever.",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        if (showLyricsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF121212)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(
                        text = "Lyrics",
                        style = LiquidTypography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (lyricsLines.isNotEmpty()) {
                        val listState = rememberLazyListState()
                        val activeLineIndex = remember(currentPosition, lyricsLines) {
                            val index = lyricsLines.indexOfLast { it.startTime <= currentPosition }
                            if (index >= 0) index else 0
                        }

                        LaunchedEffect(activeLineIndex) {
                            val targetIndex = maxOf(0, activeLineIndex - 2)
                            listState.animateScrollToItem(targetIndex)
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            contentPadding = PaddingValues(vertical = 64.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            itemsIndexed(lyricsLines) { index, line ->
                                val isActive = index == activeLineIndex
                                val color by animateColorAsState(if (isActive) Color.White else Color.White.copy(alpha = 0.4f), label = "lyricsColor")
                                val fontSize by animateFloatAsState(if (isActive) 24f else 20f, label = "lyricsSize")

                                Text(
                                    text = line.content,
                                    color = color,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize + 8f).sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .clickable { onSeek(line.startTime) }
                                )
                            }
                        }
                    } else if (!currentSong?.lyrics.isNullOrBlank()) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = currentSong?.lyrics!!,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 20.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    } else {
                        Text(
                            text = "Looking for lyrics...\nIf they don't appear, they might not be available in the database.",
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showInfoSheet) {
            ModalBottomSheet(
                onDismissRequest = { showInfoSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF121212)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Song Info", style = LiquidTypography.titleMedium, color = Color.White, modifier = Modifier.padding(bottom = 24.dp))
                    InfoRow(label = "Title", value = currentSong?.title ?: "Unknown")
                    InfoRow(label = "Artist", value = currentSong?.artist ?: "Unknown")
                    InfoRow(label = "Source", value = if (isYouTube) "YouTube Music (InnerTube)" else "Local Device")
                    val durationMin = (currentSong?.duration ?: 0) / 1000 / 60
                    val durationSec = ((currentSong?.duration ?: 0) / 1000) % 60
                    InfoRow(label = "Duration", value = String.format("%d:%02d", durationMin, durationSec))
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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
        Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                Box(modifier = Modifier.fillMaxWidth(displayProgress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(colors = listOf(Color(0xFFFF99CC), Color(0xFFBBB0FD)))))
            }
            Slider(
                value = displayProgress,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { sliderPosition?.let { onSeek((it * totalDuration).toLong()) }; sliderPosition = null },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayTime = (displayProgress * totalDuration).toLong()
            Text(text = formatTime(displayTime), style = LiquidTypography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Text(text = "-${formatTime(maxOf(0L, totalDuration - displayTime))}", style = LiquidTypography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun PlayerControlsSection(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {  }) { Icon(Icons.Default.Shuffle, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(28.dp)) }
        IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onPlayPause), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        val repeatIcon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
        val repeatTint = if (repeatMode == RepeatMode.OFF) Color.White.copy(alpha = 0.5f) else LiquidPrimary
        IconButton(onClick = onToggleRepeat) { Icon(repeatIcon, null, tint = repeatTint, modifier = Modifier.size(28.dp)) }
    }
}