package com.jagr.fridamusic.presentation.screens

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.jagr.fridamusic.domain.model.*
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.SpotifyNativeAd
import com.jagr.fridamusic.presentation.components.rememberMiniPlayerArtworkPalette
import com.jagr.fridamusic.presentation.components.rememberFridaArtworkRequest
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: () -> Long,
    albumArtUrl: String?,
    repeatMode: com.jagr.fridamusic.domain.model.RepeatMode,
    isShuffleMode: Boolean,
    lyricsLines: List<LyricsLine>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCollapse: () -> Unit,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val adManager = remember { AdManager.getInstance(context) }

    var showAd by remember { mutableStateOf(false) }
    var adExplicitlyClosed by remember { mutableStateOf(false) }
    var lastAdSongId by remember { mutableStateOf<String?>(null) }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showCurrentSongActions by remember { mutableStateOf(false) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isYouTube = currentSong?.uri?.toString()?.startsWith("http") == true
    val hasAnyLyrics = lyricsLines.isNotEmpty() || !currentSong?.lyrics.isNullOrBlank()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val totalDuration by playbackViewModel.duration.collectAsState()
    val enableBlur by settingsViewModel.enableBlurEffect.collectAsState()
    val queueState by playbackViewModel.queueState.collectAsState()
    val isAutoplayEnabled by settingsViewModel.isAutoPlayEnabled.collectAsState()
    val dismissAdForPlaybackOverlay: () -> Unit = {
        showAd = false
        adExplicitlyClosed = true
    }

    val supportsNativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shouldApplyBlur = enableBlur && supportsNativeBlur

    val artworkPalette by rememberMiniPlayerArtworkPalette(albumArtUrl)
    val immersiveDarkGradient = remember(artworkPalette) {
        Brush.verticalGradient(
            colors = listOf(
                artworkPalette.glowStart.copy(alpha = 0.65f), 
                Color(0xFF121212)
            )
        )
    }

    val isLiked = remember(playlists, currentSong) {
        playlists.find { it.name == "Me gusta" }?.songIds?.contains(currentSong?.id) == true
    }

    val onToggleLike: () -> Unit = {
        if (currentSong != null) {
            viewModel.toggleLike(currentSong)
        }
    }

    LaunchedEffect(currentSong) {
        if (currentSong == null) return@LaunchedEffect
        if (currentSong.data != lastAdSongId) {
            adExplicitlyClosed = false
            if (adManager.canShowAdNow()) {
                adManager.preloadNativeAd()
                showAd = true
                lastAdSongId = currentSong.data
            } else {
                showAd = false
            }
        }
    }

    BackHandler(onBack = onCollapse)

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .background(Color.Black) 
            .background(
                if (shouldApplyBlur) SolidColor(Color(0xFF121212)) else immersiveDarkGradient
            )
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
                ) { _, dragAmount ->
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.minimize), tint = Color.White)
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isYouTube) stringResource(R.string.from_youtube_music) else stringResource(R.string.local_audio_player),
                        style = LiquidTypography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(onClick = { showCurrentSongActions = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.weight(0.15f))
            
            // Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            ) {
                FridaArtworkImage(
                    model = albumArtUrl,
                    contentDescription = stringResource(R.string.album_art),
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.weight(0.15f))
            
            // Title & Artist
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: stringResource(R.string.no_song_playing),
                        style = LiquidTypography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong?.artist ?: stringResource(R.string.unknown_artist),
                        style = LiquidTypography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.like),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress Bar
            val position = currentPosition()
            val progress = if (totalDuration > 0) position.toFloat() / totalDuration else 0f
            
            Slider(
                value = progress,
                onValueChange = { onSeek((it * totalDuration).toLong()) },
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
                Text(
                    text = formatDuration(position),
                    style = LiquidTypography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = formatDuration(totalDuration),
                    style = LiquidTypography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.weight(0.2f))
            
            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle, 
                        contentDescription = stringResource(R.string.shuffle),
                        tint = if (isShuffleMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), tint = Color.White, modifier = Modifier.size(32.dp))
                }
                
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            com.jagr.fridamusic.domain.model.RepeatMode.ALL -> Icons.Default.Repeat
                            com.jagr.fridamusic.domain.model.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = stringResource(R.string.repeat),
                        tint = if (repeatMode != com.jagr.fridamusic.domain.model.RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(0.2f))
            
            // Bottom Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showLyricsSheet = true }) {
                    Icon(Icons.Default.Lyrics, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.lyrics), color = Color.White.copy(alpha = 0.6f))
                }
                
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = stringResource(R.string.queue), tint = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        
        if (showQueueSheet) {
            QueueBottomSheet(
                currentSong = currentSong,
                albumArtUrl = albumArtUrl,
                sheetState = sheetState,
                viewModel = viewModel,
                playbackViewModel = playbackViewModel,
                settingsViewModel = settingsViewModel,
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

        if (showCurrentSongActions && currentSong != null) {
            ModalBottomSheet(
                onDismissRequest = { showCurrentSongActions = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CurrentSongActionsSheet(
                    song = currentSong,
                    isLiked = isLiked,
                    viewModel = viewModel,
                    onDismiss = { showCurrentSongActions = false },
                    onToggleLike = { onToggleLike() },
                    onPickPlaylist = {
                        showCurrentSongActions = false
                        playlistPickerSong = it
                    },
                    onOpenInfo = {
                        showCurrentSongActions = false
                        showInfoSheet = true
                    }
                )
            }
        }

        playlistPickerSong?.let { song ->
            ModalBottomSheet(
                onDismissRequest = { playlistPickerSong = null },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                QueuePlaylistPicker(
                    playlists = playlists,
                    onDismiss = { playlistPickerSong = null },
                    onSelect = { playlist ->
                        viewModel.addSongToPlaylist(playlist, song)
                        playlistPickerSong = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    currentSong: Song?,
    albumArtUrl: String?,
    sheetState: SheetState,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val queueState by playbackViewModel.queueState.collectAsState()
    val isAutoplayEnabled by settingsViewModel.isAutoPlayEnabled.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    var actionTarget by remember { mutableStateOf<QueueActionTarget?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.queue), style = LiquidTypography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        queueState.sourceName?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    SmartAutoplayControl(
                        isEnabled = isAutoplayEnabled,
                        isLoading = queueState.isAutoplayLoading,
                        suggestionCount = queueState.autoplay.size,
                        onToggle = { settingsViewModel.toggleAutoplay(it) }
                    )
                }
                if (queueState.upNext.isNotEmpty() || queueState.autoplay.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val clearLabel = if (queueState.upNext.isNotEmpty()) R.string.clear_queue else R.string.remove_suggestions
                        TextButton(onClick = { playbackViewModel.clearManualQueue() }) {
                            Text(stringResource(clearLabel))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (queueState.previous.isNotEmpty()) {
                    item { QueueSectionTitle(stringResource(R.string.queue_previous)) }
                    val previousOffset = (queueState.previous.size - 6).coerceAtLeast(0)
                    itemsIndexed(
                        items = queueState.previous.takeLast(6),
                        key = { index, item -> "previous_${previousOffset + index}_${item.song.id}" }
                    ) { index, item ->
                        val realIndex = previousOffset + index
                        QueueSongRow(
                            item = item,
                            imageUrl = queueArtworkUrl(item.song, null),
                            isCurrent = false,
                            isMuted = true,
                            onClick = { playbackViewModel.replayPrevious(realIndex) },
                            onMore = { actionTarget = QueueActionTarget(item, QueueSection.PREVIOUS, realIndex, queueState.previous.size) }
                        )
                    }
                }

                item { QueueSectionTitle(stringResource(R.string.now_playing)) }
                item {
                    val nowPlaying = queueState.current ?: currentSong?.let { QueueItem(it, QueueSource.RESTORED) }
                    if (nowPlaying != null) {
                        QueueSongRow(
                            item = nowPlaying,
                            imageUrl = albumArtUrl ?: queueArtworkUrl(nowPlaying.song, null),
                            isCurrent = true,
                            isMuted = false,
                            onClick = null,
                            onMore = null
                        )
                    } else {
                        QueueEmptyState(text = stringResource(R.string.no_song_playing), loading = false)
                    }
                }

                item { QueueSectionTitle(stringResource(R.string.up_next)) }
                if (queueState.upNext.isEmpty()) {
                    item {
                        QueueEmptyState(
                            text = stringResource(R.string.no_songs_in_queue),
                            loading = false
                        )
                    }
                } else {
                    itemsIndexed(
                        items = queueState.upNext,
                        key = { index, item -> "next_${index}_${item.song.id}" }
                    ) { index, item ->
                        QueueSongRow(
                            item = item,
                            imageUrl = queueArtworkUrl(item.song, null),
                            isCurrent = false,
                            isMuted = false,
                            onClick = { playbackViewModel.playUpNext(index) },
                            onMore = { actionTarget = QueueActionTarget(item, QueueSection.UP_NEXT, index, queueState.upNext.size) }
                        )
                    }
                }

                item { QueueSectionTitle(stringResource(R.string.autoplay_suggestions)) }
                when {
                    !isAutoplayEnabled -> {
                        item { QueueEmptyState(text = stringResource(R.string.queue_autoplay_off), loading = false) }
                    }
                    queueState.isAutoplayLoading -> {
                        item { QueueEmptyState(text = stringResource(R.string.queue_autoplay_loading), loading = true) }
                    }
                    queueState.autoplayError != null -> {
                        item { QueueEmptyState(text = queueState.autoplayError.orEmpty(), loading = false, isError = true) }
                    }
                    queueState.autoplay.isEmpty() -> {
                        item { QueueEmptyState(text = stringResource(R.string.queue_autoplay_empty), loading = false) }
                    }
                    else -> {
                        itemsIndexed(
                            items = queueState.autoplay,
                            key = { index, item -> "autoplay_${index}_${item.song.id}" }
                        ) { index, item ->
                            QueueSongRow(
                                item = item,
                                imageUrl = queueArtworkUrl(item.song, null),
                                isCurrent = false,
                                isMuted = false,
                                onClick = { playbackViewModel.playSong(item.song) },
                                onMore = { actionTarget = QueueActionTarget(item, QueueSection.AUTOPLAY, index, queueState.autoplay.size) }
                            )
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QueueActionsSheet(
                target = target,
                viewModel = viewModel,
                playbackViewModel = playbackViewModel,
                onDismiss = { actionTarget = null },
                onPickPlaylist = { song ->
                    actionTarget = null
                    playlistPickerSong = song
                }
            )
        }
    }

    playlistPickerSong?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { playlistPickerSong = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QueuePlaylistPicker(
                playlists = playlists,
                onDismiss = { playlistPickerSong = null },
                onSelect = { playlist ->
                    viewModel.addSongToPlaylist(playlist, song)
                    playlistPickerSong = null
                }
            )
        }
    }
}

private enum class QueueSection { PREVIOUS, UP_NEXT, AUTOPLAY }

private data class QueueActionTarget(
    val item: QueueItem,
    val section: QueueSection,
    val index: Int,
    val sectionSize: Int
)

private data class QueueActionSpec(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun QueueSectionTitle(title: String) {
    Text(
        text = title,
        style = LiquidTypography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun SmartAutoplayControl(
    isEnabled: Boolean,
    isLoading: Boolean,
    suggestionCount: Int,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable { onToggle(!isEnabled) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (isEnabled) Icons.Default.AutoAwesome else Icons.Default.AutoAwesomeMotion,
            contentDescription = null,
            tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isEnabled) stringResource(R.string.autoplay) else stringResource(R.string.off_label),
            style = LiquidTypography.labelMedium,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isEnabled && isLoading) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun QueueSongRow(
    item: QueueItem,
    imageUrl: String?,
    isCurrent: Boolean,
    isMuted: Boolean,
    onClick: (() -> Unit)?,
    onMore: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            FridaArtworkImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                style = LiquidTypography.bodyLarge.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium),
                color = if (isMuted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.song.artist,
                style = LiquidTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueueEmptyState(text: String, loading: Boolean, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator()
        } else {
            Text(text = text, style = LiquidTypography.bodyMedium, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QueueActionsSheet(
    target: QueueActionTarget,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onDismiss: () -> Unit,
    onPickPlaylist: (Song) -> Unit
) {
    val context = LocalContext.current
    val song = target.item.song
    val actions = buildList {
        add(QueueActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play_now)) {
            onDismiss()
            when (target.section) {
                QueueSection.PREVIOUS -> playbackViewModel.replayPrevious(target.index)
                QueueSection.UP_NEXT -> playbackViewModel.playUpNext(target.index)
                QueueSection.AUTOPLAY -> playbackViewModel.playSong(target.item.song)
            }
        })
        if (target.section != QueueSection.UP_NEXT || target.index > 0) {
            add(QueueActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next)) {
                onDismiss()
                playbackViewModel.addSongNext(song)
            })
        }
        if (target.section == QueueSection.UP_NEXT) {
            add(QueueActionSpec(Icons.Default.Delete, stringResource(R.string.remove_from_queue), destructive = true) {
                onDismiss()
                playbackViewModel.removeFromQueue(target.index)
            })
        } else {
            add(QueueActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue)) {
                onDismiss()
                playbackViewModel.addSongToQueue(song)
            })
        }
        add(QueueActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist)) {
            onPickPlaylist(song)
        })
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        }
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = action.onClick)
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Icon(action.icon, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(14.dp))
                Text(action.label, color = color, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun CurrentSongActionsSheet(
    song: Song,
    isLiked: Boolean,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    onToggleLike: (Song) -> Unit,
    onPickPlaylist: (Song) -> Unit,
    onOpenInfo: () -> Unit
) {
    val actions = buildList {
        add(QueueActionSpec(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like)) {
            onDismiss()
            onToggleLike(song)
        })
        add(QueueActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist)) {
            onPickPlaylist(song)
        })
        add(QueueActionSpec(Icons.Default.Info, stringResource(R.string.details)) {
            onOpenInfo()
        })
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        }
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = action.onClick)
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Icon(action.icon, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(14.dp))
                Text(action.label, color = color, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun QueuePlaylistPicker(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.save_to_playlist), style = LiquidTypography.titleMedium)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
        }
        if (playlists.isEmpty()) {
            QueueEmptyState(text = stringResource(R.string.no_playlists), loading = false)
        } else {
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(playlist) }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(playlist.name, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.songs_count, playlist.songIds.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun queueArtworkUrl(song: Song, fallback: String?): String? =
    fallback?.takeIf { it.isNotBlank() }
        ?: song.artworkUri.toString().takeIf { it.isNotBlank() && it != "content://media/external/audio/albumart/0" }

private fun shareQueueSong(context: Context, song: Song, remoteUrl: String?) {
    // shareSongAudioOrLink(context, song, remoteUrl, song.title) // Placeholder
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
                val activeLineIndex by remember(lyricsLines, currentPosition) {
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
                    itemsIndexed(
                        items = lyricsLines,
                        key = { index, line -> "${index}_${line.startTime}_${line.content.hashCode()}" }
                    ) { index, line ->
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
            InfoRow(label = stringResource(R.string.duration_label), value = String.format(java.util.Locale.getDefault(), "%d:%02d", durationMin, durationSec))
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
