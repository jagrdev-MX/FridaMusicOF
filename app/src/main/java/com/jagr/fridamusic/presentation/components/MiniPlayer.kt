package com.jagr.fridamusic.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.SubcomposeAsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.theme.LiquidTypography

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
    val contentColor by animateColorAsState(
        targetValue = artworkPalette.onContainer,
        label = "mini-player-content-color"
    )
    val mutedContentColor by animateColorAsState(
        targetValue = artworkPalette.onContainerMuted,
        label = "mini-player-muted-content-color"
    )
    val containerBorder by animateColorAsState(
        targetValue = artworkPalette.border,
        label = "mini-player-border-color"
    )
    val accentColor by animateColorAsState(
        targetValue = artworkPalette.accent,
        label = "mini-player-accent-color"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, containerBorder, RoundedCornerShape(20.dp))
            .clickable(
                enabled = hasSong,
                interactionSource = interactionSource,
                indication = null,
                onClick = onExpand
            )
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
                trackColor = contentColor.copy(alpha = 0.10f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
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

            MiniPlayerControlButton(
                icon = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                enabled = hasSong,
                contentColor = contentColor,
                onClick = onPrevious
            )
            MiniPlayerPlayPauseButton(
                isPlaying = isPlaying,
                enabled = hasSong,
                showLoading = showIndeterminateProgress,
                accentColor = accentColor,
                contentColor = contentColor,
                onClick = onPlayPause
            )
            MiniPlayerControlButton(
                icon = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.next),
                enabled = hasSong,
                contentColor = contentColor,
                onClick = onNext
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
            .background(containerColor)
    ) {
        if (showArtworkGlow) {
            if (albumArtUrl != null) {
                SubcomposeAsyncImage(
                    model = albumArtUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 0.24f
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
            .clip(RoundedCornerShape(10.dp))
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
        if (albumArtUrl != null) {
            SubcomposeAsyncImage(
                model = albumArtUrl,
                contentDescription = stringResource(R.string.album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { AlbumArtFallback(hasSong = hasSong, hasError = hasError) },
                error = { AlbumArtFallback(hasSong = hasSong, hasError = true) }
            )
        } else {
            AlbumArtFallback(hasSong = hasSong, hasError = hasError)
        }
    }
}

@Composable
private fun AlbumArtFallback(
    hasSong: Boolean,
    hasError: Boolean
) {
    val iconColor by animateColorAsState(
        targetValue = when {
            hasError -> MaterialTheme.colorScheme.error
            hasSong -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        },
        label = "album-art-fallback-color"
    )

    Icon(
        imageVector = if (hasError) Icons.Default.ErrorOutline else Icons.Default.MusicNote,
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun MiniPlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    contentColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                contentColor.copy(alpha = 0.90f)
            } else {
                contentColor.copy(alpha = 0.24f)
            },
            modifier = Modifier.size(22.dp)
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
