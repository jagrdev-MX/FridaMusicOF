package com.jagr.fridamusic.presentation.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.rememberMiniPlayerArtworkPalette
import com.jagr.fridamusic.presentation.viewmodels.*


@Composable
fun DetailPageShell(
    title: String,
    subtitle: String,
    description: String,
    cover: @Composable () -> Unit,
    countLabel: String,
    backgroundArtUrl: String?,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    secondaryActions: @Composable RowScope.() -> Unit,
    paddingValues: PaddingValues,
    content: LazyListScope.() -> Unit
) {
    val palette by rememberMiniPlayerArtworkPalette(backgroundArtUrl)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.glowStart.copy(alpha = 0.22f),
                        palette.glowEnd.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        FridaArtworkImage(
            model = backgroundArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp)
                .alpha(0.18f),
            requestSizePx = 192
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = paddingValues.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    IconButton(onClick = onMore) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                }
            }

            item { cover() }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = secondaryActions
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = countLabel,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.play))
                    }
                    TextButton(onClick = onShuffle) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.shuffle))
                    }
                }
            }

            content()
        }
    }
}


@Composable
private fun DetailCoverPlaceholder(
    icon: ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        FridaArtworkImage(
            model = null,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            requestSizePx = 512
        )
    }
}


@Composable
fun rememberLeadArtworkUrl(
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModel
) = produceState<String?>(
    initialValue = customCoverUri,
    key1 = songs,
    key2 = customCoverUri
) {
    value = customCoverUri ?: songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
}


@Composable
fun SmartCollectionCover(
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModel,
    shape: Shape,
    fallbackIcon: ImageVector
) {
    val coverModels by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = songs,
        key2 = customCoverUri
    ) {
        value = if (!customCoverUri.isNullOrBlank()) {
            listOf(customCoverUri)
        } else {
            viewModel.getDistinctSongImageUrls(songs)
        }
    }

    when {
        coverModels.size == 1 -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                FridaArtworkImage(
                    model = coverModels.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    requestSizePx = 512
                )
            }
        }

        coverModels.size >= 2 -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape)
            ) {
                coverModels.chunked(2).take(2).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { model ->
                            FridaArtworkImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                requestSizePx = 256
                            )
                        }
                        if (row.size == 1) {
                            DetailCoverPlaceholderCell(
                                icon = fallbackIcon,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (coverModels.size < 3) {
                    Row(Modifier.weight(1f)) {
                        repeat(2) {
                            DetailCoverPlaceholderCell(
                                icon = fallbackIcon,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        else -> DetailCoverPlaceholder(icon = fallbackIcon)
    }
}


@Composable
private fun DetailCoverPlaceholderCell(
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        FridaArtworkImage(
            model = null,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            requestSizePx = 128
        )
    }
}


@Composable
fun SmartCollectionThumbnail(
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModel,
    fallbackIcon: ImageVector
) {
    val coverModels by produceState<List<String>>(
        initialValue = emptyList(),
        key1 = songs,
        key2 = customCoverUri
    ) {
        value = if (!customCoverUri.isNullOrBlank()) {
            listOf(customCoverUri)
        } else {
            viewModel.getDistinctSongImageUrls(songs)
        }
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            coverModels.size == 1 -> {
                FridaArtworkImage(
                    model = coverModels.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    requestSizePx = 128
                )
            }

            coverModels.size >= 2 -> {
                Column(Modifier.fillMaxSize()) {
                    coverModels.chunked(2).take(2).forEach { row ->
                        Row(Modifier.weight(1f)) {
                            row.forEach { model ->
                                FridaArtworkImage(
                                    model = model,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize(),
                                    requestSizePx = 96
                                )
                            }
                            if (row.size == 1) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                )
                            }
                        }
                    }
                    if (coverModels.size < 3) {
                        Row(Modifier.weight(1f)) {
                            repeat(2) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Icon(
                    fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
fun DetailSongCover(
    song: Song?,
    viewModel: LibraryViewModel,
    shape: Shape,
    overrideModel: String? = null
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = song?.let { viewModel.getSongImageUrl(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        FridaArtworkImage(
            model = overrideModel ?: imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            requestSizePx = 720
        )
    }
}


@Composable
fun DetailChipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}


@Composable
fun DetailIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}