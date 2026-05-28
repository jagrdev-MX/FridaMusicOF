package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.SubcomposeAsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    currentSong: Song?,
    isPlaying: Boolean,
    albumArtUrl: String?,
    playbackState: Int,
    isLoading: Boolean,
    errorMessage: String?,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit
) {
    val hasSong = currentSong != null
    val isBuffering = playbackState == Player.STATE_BUFFERING
    val showIndeterminateProgress = isLoading || isBuffering
    val normalizedAlbumArt = albumArtUrl?.takeIf { it.isNotBlank() }
    val title = when {
        currentSong != null -> currentSong.title
        isLoading -> stringResource(R.string.loading_track)
        else -> stringResource(R.string.no_song_playing)
    }
    val subtitle = when {
        !errorMessage.isNullOrBlank() -> errorMessage
        currentSong != null -> currentSong.artist
        else -> ""
    }
    val progress = if (duration > 0L) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val artworkPalette by rememberMiniPlayerArtworkPalette(normalizedAlbumArt)
    val containerColor by animateColorAsState(
        targetValue = artworkPalette.container,
        label = "mini-player-container-color"
    )
    val glowStartColor by animateColorAsState(
        targetValue = artworkPalette.glowStart,
        label = "mini-player-glow-start"
    )
    val glowEndColor by animateColorAsState(
        targetValue = artworkPalette.glowEnd,
        label = "mini-player-glow-end"
    )

    val contentColor = Color.White
    val mutedContentColor = Color.White.copy(alpha = 0.7f)
    val accentColor = Color.White
    val interactionSource = remember { MutableInteractionSource() }

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = swipeOffset, label = "swipe_anim")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = hasSong,
                interactionSource = interactionSource,
                indication = null,
                onClick = onExpand
            )
            .pointerInput(hasSong) {
                if (!hasSong) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset > 80f) {
                            onPrevious()
                        }
                        else if (swipeOffset < -80f) {
                            onNext()
                        }
                        swipeOffset = 0f
                    },
                    onDragCancel = { swipeOffset = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeOffset = (swipeOffset + dragAmount).coerceIn(-150f, 150f)
                    }
                )
            }
    ) {
        MiniPlayerBackdrop(
            albumArtUrl = normalizedAlbumArt,
            containerColor = containerColor,
            glowStartColor = glowStartColor,
            glowEndColor = glowEndColor,
            showArtworkGlow = artworkPalette.fromArtwork
        )

        if (showIndeterminateProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = accentColor,
                trackColor = Color.Transparent
            )
        } else if (hasSong && duration > 0L) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = accentColor,
                trackColor = contentColor.copy(alpha = 0.30f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumb(
                albumArtUrl = normalizedAlbumArt,
                hasSong = hasSong,
                hasError = !errorMessage.isNullOrBlank()
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = LiquidTypography.labelSmall,
                        color = if (!errorMessage.isNullOrBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            mutedContentColor
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            MiniPlayerPlayPauseButton(
                isPlaying = isPlaying,
                enabled = hasSong,
                showLoading = showIndeterminateProgress,
                accentColor = accentColor,
                contentColor = contentColor,
                onClick = onPlayPause
            )
        }
    }
}

@Composable
private fun MiniPlayerBackdrop(
    albumArtUrl: String?,
    containerColor: Color,
    glowStartColor: Color,
    glowEndColor: Color,
    showArtworkGlow: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (showArtworkGlow) {
            if (albumArtUrl != null) {
                SubcomposeAsyncImage(
                    model = albumArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.15f
                            scaleX = 1.18f
                            scaleY = 1.18f
                        }
                        .blur(28.dp)
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = (-26).dp, y = (-30).dp)
                    .size(128.dp)
                    .blur(30.dp)
                    .background(glowStartColor.copy(alpha = 0.22f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 22.dp, y = 24.dp)
                    .size(132.dp)
                    .blur(32.dp)
                    .background(glowEndColor.copy(alpha = 0.18f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.16f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun AlbumArtThumb(
    albumArtUrl: String?,
    hasSong: Boolean,
    hasError: Boolean
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        FridaArtworkImage(
            model = albumArtUrl,
            contentDescription = stringResource(R.string.album_art),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun MiniPlayerPlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    showLoading: Boolean,
    accentColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled && !showLoading,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
    ) {
        if (showLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Crossfade(
                targetState = isPlaying,
                label = "mini-player-play-pause"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = if (enabled) {
                        Color.White
                    } else {
                        contentColor.copy(alpha = 0.24f)
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}