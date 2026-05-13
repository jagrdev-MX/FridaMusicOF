package com.jagr.fridamusic.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.ads.AdManager
import com.jagr.fridamusic.domain.lyrics.LyricsLine
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.SpotifyNativeAd
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.RepeatMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    isLiked: Boolean,
    currentPosition: () -> Long,
    albumArtUrl: String?,
    repeatMode: RepeatMode,
    lyricsLines: List<LyricsLine>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    val adManager = remember { AdManager.getInstance(context) }

    var showAd by remember { mutableStateOf(false) }
    var adExplicitlyClosed by remember { mutableStateOf(false) }
    var lastAdSongId by remember { mutableStateOf<String?>(null) }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isYouTube = currentSong?.uri?.toString()?.startsWith("http") == true
    val hasAnyLyrics = lyricsLines.isNotEmpty() || !currentSong?.lyrics.isNullOrBlank()

    LaunchedEffect(currentSong) {
        if (currentSong == null) return@LaunchedEffect
        if (currentSong.data != lastAdSongId) {
            adExplicitlyClosed = false
            if (adManager.canShowAdNow()) {
                showAd = true
                lastAdSongId = currentSong.data
            } else {
                showAd = false
            }
        }
    }

    BackHandler(onBack = onCollapse)

    val backgroundColor = MaterialTheme.colorScheme.background
    val bgGradient = remember(backgroundColor) {
        Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.4f),
                backgroundColor.copy(alpha = 0.95f)
            )
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY.value > 250f) {
                            onCollapse()
                        } else {
                            coroutineScope.launch { dragOffsetY.animateTo(0f) }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { dragOffsetY.animateTo(0f) }
                    }
                ) { change, dragAmount ->
                    val newY = (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                    coroutineScope.launch { dragOffsetY.snapTo(newY) }
                }
            }
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
                modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f)
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(bgGradient))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            NowPlayingTopBar(isYouTube = isYouTube, onCollapse = onCollapse)

            AlbumArtOrAdSection(
                albumArtUrl = albumArtUrl,
                showAd = showAd,
                adExplicitlyClosed = adExplicitlyClosed,
                onCloseAd = { showAd = false; adExplicitlyClosed = true },
                onAdFailed = { showAd = false },
                modifier = Modifier.fillMaxWidth().weight(1f).aspectRatio(1f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            SongInfoSection(
                currentSong = currentSong,
                isLiked = isLiked,
                onToggleLike = onToggleLike
            )

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

            BottomActionsSection(
                hasAnyLyrics = hasAnyLyrics,
                onOpenQueue = { showQueueSheet = true },
                onOpenLyrics = { showLyricsSheet = true },
                onOpenInfo = { showInfoSheet = true }
            )
        }

        if (showQueueSheet) {
            QueueBottomSheet(
                currentSong = currentSong,
                albumArtUrl = albumArtUrl,
                sheetState = sheetState,
                onDismiss = { showQueueSheet = false }
            )
        }

        if (showLyricsSheet) {
            LyricsBottomSheet(
                currentSong = currentSong,
                lyricsLines = lyricsLines,
                currentPosition = currentPosition,
                sheetState = sheetState,
                onSeek = onSeek,
                onDismiss = { showLyricsSheet = false }
            )
        }

        if (showInfoSheet) {
            InfoBottomSheet(
                currentSong = currentSong,
                isYouTube = isYouTube,
                sheetState = sheetState,
                onDismiss = { showInfoSheet = false }
            )
        }
    }
}

@Composable
private fun NowPlayingTopBar(isYouTube: Boolean, onCollapse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.minimize), tint = MaterialTheme.colorScheme.onBackground)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.now_playing),
                style = LiquidTypography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            if (isYouTube) {
                Text(
                    text = stringResource(R.string.from_youtube_music),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        IconButton(onClick = { }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun AlbumArtOrAdSection(
    albumArtUrl: String?,
    showAd: Boolean,
    adExplicitlyClosed: Boolean,
    onCloseAd: () -> Unit,
    onAdFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (showAd && !adExplicitlyClosed) {
            SpotifyNativeAd(onClose = onCloseAd, onAdFailed = onAdFailed)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                    .blur(30.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                if (albumArtUrl != null) {
                    AsyncImage(
                        model = albumArtUrl,
                        contentDescription = stringResource(R.string.album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.align(Alignment.Center).size(80.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SongInfoSection(currentSong: Song?, isLiked: Boolean, onToggleLike: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = currentSong?.title ?: stringResource(R.string.no_song_playing),
                style = LiquidTypography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong?.artist ?: stringResource(R.string.unknown_artist),
                style = LiquidTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = stringResource(R.string.like),
                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun SeekBarSection(currentSong: Song?, currentPosition: () -> Long, onSeek: (Long) -> Unit) {
    val totalDuration = currentSong?.duration ?: 0L
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val currentProgress by remember(totalDuration) {
        derivedStateOf { if (totalDuration > 0) currentPosition().toFloat() / totalDuration.toFloat() else 0f }
    }
    val displayProgress = sliderPosition ?: currentProgress

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))) {
                Box(modifier = Modifier.fillMaxWidth(displayProgress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
            }
            Slider(
                value = displayProgress,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { sliderPosition?.let { onSeek((it * totalDuration).toLong()) }; sliderPosition = null },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onBackground,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayTime = (displayProgress * totalDuration).toLong()
            Text(text = formatTime(displayTime), style = LiquidTypography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "-${formatTime(maxOf(0L, totalDuration - displayTime))}", style = LiquidTypography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlayerControlsSection(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { }) { Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(28.dp)) }
        IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp)) }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp)) }

        val repeatIcon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
        val repeatTint = if (repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
        IconButton(onClick = onToggleRepeat) { Icon(repeatIcon, contentDescription = stringResource(R.string.theme), tint = repeatTint, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun BottomActionsSection(hasAnyLyrics: Boolean, onOpenQueue: () -> Unit, onOpenLyrics: () -> Unit, onOpenInfo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenQueue) { Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
        IconButton(onClick = onOpenLyrics) { Icon(Icons.Default.Lyrics, null, tint = if (hasAnyLyrics) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)) }
        IconButton(onClick = onOpenInfo) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    currentSong: Song?,
    albumArtUrl: String?,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    var isInfiniteQueueEnabled by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.queue), style = LiquidTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AllInclusive, null, tint = if (isInfiniteQueueEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.autoplay), color = if (isInfiniteQueueEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isInfiniteQueueEnabled,
                        onCheckedChange = { isInfiniteQueueEnabled = it },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            Text(stringResource(R.string.now_playing), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(model = albumArtUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background.copy(0.4f)))
                    Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(currentSong?.title ?: stringResource(R.string.unknown), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(currentSong?.artist ?: stringResource(R.string.unknown), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.up_next), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 16.dp))

            Box(
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isInfiniteQueueEnabled) Icons.Default.AllInclusive else Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isInfiniteQueueEnabled) stringResource(R.string.infinite_mode_on) else stringResource(R.string.queue_empty_autoplay),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsBottomSheet(
    currentSong: Song?,
    lyricsLines: List<LyricsLine>,
    currentPosition: () -> Long,
    sheetState: SheetState,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.lyrics), style = LiquidTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 16.dp))

            if (lyricsLines.isNotEmpty()) {
                val listState = rememberLazyListState()
                val activeLineIndex by remember(lyricsLines) {
                    derivedStateOf {
                        val pos = currentPosition()
                        val index = lyricsLines.indexOfLast { it.startTime <= pos }
                        if (index >= 0) index else 0
                    }
                }

                LaunchedEffect(activeLineIndex) {
                    val targetIndex = maxOf(0, activeLineIndex - 2)
                    listState.animateScrollToItem(
                        index = targetIndex,
                        scrollOffset = 0
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 64.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    itemsIndexed(lyricsLines) { index, line ->
                        val isActive = index == activeLineIndex
                        val color by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "lyricsColor"
                        )
                        val fontSize by animateFloatAsState(
                            targetValue = if (isActive) 24f else 20f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                            label = "lyricsSize"
                        )

                        Text(
                            text = line.content,
                            color = color,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize + 8f).sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { onSeek(line.startTime) }
                        )
                    }
                }
            } else if (!currentSong?.lyrics.isNullOrBlank()) {
                currentSong?.lyrics?.let { lyricsText ->
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(scrollState)
                    ) {
                        Text(
                            text = lyricsText,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontSize = 20.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.looking_for_lyrics),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoBottomSheet(
    currentSong: Song?,
    isYouTube: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(stringResource(R.string.song_info), style = LiquidTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 24.dp))
            InfoRow(label = stringResource(R.string.title_label), value = currentSong?.title ?: stringResource(R.string.unknown))
            InfoRow(label = stringResource(R.string.artist_label), value = currentSong?.artist ?: stringResource(R.string.unknown))
            InfoRow(label = stringResource(R.string.source_label), value = if (isYouTube) stringResource(R.string.source_youtube) else stringResource(R.string.source_local))
            val durationMin = (currentSong?.duration ?: 0) / 1000 / 60
            val durationSec = ((currentSong?.duration ?: 0) / 1000) % 60
            InfoRow(label = stringResource(R.string.duration_label), value = String.format("%d:%02d", durationMin, durationSec))
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}