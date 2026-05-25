package com.jagr.fridamusic.presentation.screens

import android.content.Context

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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.jagr.fridamusic.domain.model.QueueItem
import com.jagr.fridamusic.domain.model.QueueSource
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.SpotifyNativeAd
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import com.jagr.fridamusic.presentation.viewmodels.RepeatMode
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: () -> Long,
    albumArtUrl: String?,
    repeatMode: RepeatMode,
    isShuffleMode: Boolean,
    lyricsLines: List<LyricsLine>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCollapse: () -> Unit,
    viewModel: LibraryViewModels
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
    val totalDuration by viewModel.duration.collectAsState()

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
        FridaArtworkImage(
            model = albumArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f)
        )

        Box(modifier = Modifier.fillMaxSize().background(bgGradient))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            NowPlayingTopBar(
                isYouTube = isYouTube,
                onCollapse = onCollapse,
                onMore = { if (currentSong != null) showCurrentSongActions = true }
            )

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

            SeekBarSection(totalDuration = totalDuration, currentPosition = currentPosition, onSeek = onSeek)

            PlayerControlsSection(
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                isShuffleMode = isShuffleMode,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleRepeat = onToggleRepeat,
                onToggleShuffle = onToggleShuffle
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
                viewModel = viewModel,
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
                    onToggleLike = { song ->
                        showCurrentSongActions = false
                        viewModel.toggleLike(song)
                    },
                    onPickPlaylist = { song ->
                        showCurrentSongActions = false
                        playlistPickerSong = song
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

@Composable
private fun NowPlayingTopBar(isYouTube: Boolean, onCollapse: () -> Unit, onMore: () -> Unit) {
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
        IconButton(onClick = onMore) {
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
                FridaArtworkImage(
                    model = albumArtUrl,
                    contentDescription = stringResource(R.string.album_art),
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(32.dp),
                    contentScale = ContentScale.Crop
                )
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
private fun SeekBarSection(totalDuration: Long, currentPosition: () -> Long, onSeek: (Long) -> Unit) {
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val currentProgress by remember(totalDuration) {
        derivedStateOf { if (totalDuration > 0) currentPosition().toFloat() / totalDuration.toFloat() else 0f }
    }
    val displayProgress = sliderPosition ?: currentProgress

    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
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
    isShuffleMode: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (isShuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
        }
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
        IconButton(onClick = onToggleRepeat) { Icon(repeatIcon, contentDescription = stringResource(R.string.repeat), tint = repeatTint, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun BottomActionsSection(hasAnyLyrics: Boolean, onOpenQueue: () -> Unit, onOpenLyrics: () -> Unit, onOpenInfo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
        IconButton(onClick = onOpenLyrics) { Icon(Icons.Default.Lyrics, null, tint = if (hasAnyLyrics) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)) }
        IconButton(onClick = onOpenInfo) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
    }
}

@Composable
private fun CurrentSongActionsSheet(
    song: Song,
    isLiked: Boolean,
    viewModel: LibraryViewModels,
    onDismiss: () -> Unit,
    onToggleLike: (Song) -> Unit,
    onPickPlaylist: (Song) -> Unit,
    onOpenInfo: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = listOf(
        QueueActionSpec(
            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like)
        ) {
            onToggleLike(song)
        },
        QueueActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist)) {
            onPickPlaylist(song)
        },
        QueueActionSpec(
            Icons.Default.Share,
            stringResource(if (song.hasLocalAudioToShare()) R.string.share_audio_file else R.string.share_song_link)
        ) {
            onDismiss()
            scope.launch {
                val fallbackUrl = if (song.hasLocalAudioToShare()) null else viewModel.resolveShareUrl(song)
                shareQueueSong(context, song, fallbackUrl)
            }
        },
        QueueActionSpec(Icons.Default.Info, stringResource(R.string.song_info)) {
            onOpenInfo()
        }
    )

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
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(14.dp))
                Text(action.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    currentSong: Song?,
    albumArtUrl: String?,
    sheetState: SheetState,
    viewModel: LibraryViewModels,
    onDismiss: () -> Unit
) {
    val queueState by viewModel.queueState.collectAsState()
    val isAutoplayEnabled by viewModel.isAutoPlayEnabled.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    var actionTarget by remember { mutableStateOf<QueueActionTarget?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                        onToggle = viewModel::toggleAutoplay
                    )
                }
                if (queueState.upNext.isNotEmpty() || queueState.autoplay.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val clearLabel = if (queueState.upNext.isNotEmpty()) R.string.clear_queue else R.string.remove_suggestions
                        TextButton(onClick = viewModel::clearManualQueue) {
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
                    itemsIndexed(queueState.previous.takeLast(6), key = { index, item -> "previous_${previousOffset + index}_${item.song.id}" }) { index, item ->
                        val realIndex = previousOffset + index
                        QueueSongRow(
                            item = item,
                            imageUrl = queueArtworkUrl(item.song, null),
                            isCurrent = false,
                            isMuted = true,
                            onClick = { viewModel.replayPrevious(realIndex) },
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
                    itemsIndexed(queueState.upNext, key = { index, item -> "next_${index}_${item.song.id}" }) { index, item ->
                        QueueSongRow(
                            item = item,
                            imageUrl = queueArtworkUrl(item.song, null),
                            isCurrent = false,
                            isMuted = false,
                            onClick = { viewModel.playUpNext(index) },
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
                        itemsIndexed(queueState.autoplay, key = { index, item -> "autoplay_${index}_${item.song.id}" }) { index, item ->
                            QueueSongRow(
                                item = item,
                                imageUrl = queueArtworkUrl(item.song, null),
                                isCurrent = false,
                                isMuted = false,
                                onClick = { viewModel.playAutoplayItem(index) },
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
private fun QueueSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun SmartAutoplayControl(
    isEnabled: Boolean,
    isLoading: Boolean,
    suggestionCount: Int,
    onToggle: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
        animationSpec = tween(durationMillis = 220),
        label = "autoplayContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.46f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 220),
        label = "autoplayBorder"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "autoplayIcon"
    )
    val auraAlpha by animateFloatAsState(
        targetValue = if (isEnabled) 0.26f else 0.06f,
        animationSpec = tween(durationMillis = 260),
        label = "autoplayAura"
    )
    val knobOffset by animateDpAsState(
        targetValue = if (isEnabled) 15.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "autoplayKnob"
    )
    val label = when {
        isLoading -> stringResource(R.string.smart_autoplay_loading)
        isEnabled -> stringResource(R.string.suggestions_on)
        else -> stringResource(R.string.smart_autoplay)
    }

    Box(
        modifier = Modifier
            .widthIn(min = 138.dp, max = 196.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = auraAlpha),
                        MaterialTheme.colorScheme.secondary.copy(alpha = auraAlpha * 0.72f),
                        containerColor
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isEnabled)
            }
            .padding(start = 12.dp, end = 9.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                Icons.Default.AllInclusive,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = label,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isEnabled && suggestionCount > 0) {
                Text(
                    text = suggestionCount.coerceAtMost(99).toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 0.18f else 0.10f))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = knobOffset)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(iconColor)
                    )
                }
            }
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
    val container = when {
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
    }
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.76f) else MaterialTheme.colorScheme.onSurfaceVariant
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(container)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 10.dp, vertical = 9.dp)

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(10.dp),
                contentScale = ContentScale.Crop
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.35f))
                )
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.song.title,
                color = titleColor.copy(alpha = if (isMuted) 0.72f else 1f),
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val reason = item.reason?.takeIf { item.source == QueueSource.AUTOPLAY && it.isNotBlank() }
            Text(
                text = listOfNotNull(item.song.artist, reason).filter { it.isNotBlank() }.joinToString(" - "),
                color = subtitleColor.copy(alpha = if (isMuted) 0.62f else 1f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QueueEmptyState(text: String, loading: Boolean, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun QueueActionsSheet(
    target: QueueActionTarget,
    viewModel: LibraryViewModels,
    onDismiss: () -> Unit,
    onPickPlaylist: (Song) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val song = target.item.song
    val actions = buildList {
        add(QueueActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play_now)) {
            onDismiss()
            when (target.section) {
                QueueSection.PREVIOUS -> viewModel.replayPrevious(target.index)
                QueueSection.UP_NEXT -> viewModel.playUpNext(target.index)
                QueueSection.AUTOPLAY -> viewModel.playAutoplayItem(target.index)
            }
        })
        if (target.section != QueueSection.UP_NEXT || target.index > 0) {
            add(QueueActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next)) {
                onDismiss()
                if (target.section == QueueSection.UP_NEXT) {
                    viewModel.moveQueueItemToNext(target.index)
                } else {
                    viewModel.addSongNext(song)
                }
            })
        }
        if (target.section == QueueSection.UP_NEXT) {
            if (target.index > 0) {
                add(QueueActionSpec(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up)) {
                    onDismiss()
                    viewModel.moveQueueItem(target.index, -1)
                })
            }
            if (target.index < target.sectionSize - 1) {
                add(QueueActionSpec(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down)) {
                    onDismiss()
                    viewModel.moveQueueItem(target.index, 1)
                })
            }
            add(QueueActionSpec(Icons.Default.Delete, stringResource(R.string.remove_from_queue), destructive = true) {
                onDismiss()
                viewModel.removeFromQueue(target.index)
            })
        } else {
            add(QueueActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue)) {
                onDismiss()
                viewModel.addSongToQueue(song)
            })
        }
        add(QueueActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist)) {
            onPickPlaylist(song)
        })
        add(QueueActionSpec(
            Icons.Default.Share,
            stringResource(if (song.hasLocalAudioToShare()) R.string.share_audio_file else R.string.share_song_link)
        ) {
            onDismiss()
            scope.launch {
                val fallbackUrl = if (song.hasLocalAudioToShare()) null else viewModel.resolveShareUrl(song)
                shareQueueSong(context, song, fallbackUrl)
            }
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
    playlists: List<com.jagr.fridamusic.domain.model.Playlist>,
    onDismiss: () -> Unit,
    onSelect: (com.jagr.fridamusic.domain.model.Playlist) -> Unit
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

private fun queueArtworkUrl(song: Song, fallback: String?): String? =
    fallback?.takeIf { it.isNotBlank() }
        ?: song.artworkUri.toString().takeIf { it.isNotBlank() && it != "content://media/external/audio/albumart/0" }

private fun shareQueueSong(context: Context, song: Song, remoteUrl: String?) {
    shareSongAudioOrLink(context, song, remoteUrl, song.title)
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
                currentSong.lyrics.let { lyricsText ->
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
