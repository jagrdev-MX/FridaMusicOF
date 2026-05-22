package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.components.rememberFridaArtworkRequest
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    songs: List<Song>,
    currentSong: Song?,
    recentHistory: List<PlaybackHistoryEntity>,
    fullHistory: List<PlaybackHistoryEntity>,
    playlists: List<Playlist>,
    viewModel: LibraryViewModels,
    onSongClick: (Song) -> Unit,
    onHistoryClick: (PlaybackHistoryEntity) -> Unit,
    onSeeAllHistory: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val showScrollShortcut by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 420 }
    }
    val quickPicks = remember(songs, fullHistory) { buildQuickPicks(songs, fullHistory) }
    val dailyDiscover = remember(songs, fullHistory) { buildDailyDiscover(songs, fullHistory) }
    val oldFavorites = remember(songs, fullHistory) { buildOldFavorites(songs, fullHistory) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                bottom = paddingValues.calculateBottomPadding() + 112.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                WelcomeSection(onProfileClick = onNavigateToSettings)
            }

            item {
                RecentlyPlayedSection(
                    history = recentHistory,
                    onHistoryClick = onHistoryClick,
                    onSeeAll = onSeeAllHistory
                )
            }

            item {
                TopArtistsSection(
                    songs = songs,
                    history = fullHistory,
                    playlists = playlists,
                    viewModel = viewModel,
                    onNavigateToArtist = onNavigateToArtist
                )
            }

            if (quickPicks.isNotEmpty()) {
                item {
                    HomeSongRail(
                        title = stringResource(R.string.quick_picks),
                        songs = quickPicks,
                        viewModel = viewModel,
                        onSongClick = onSongClick
                    )
                }
            }

            if (dailyDiscover.isNotEmpty()) {
                item {
                    HomeSongRail(
                        title = stringResource(R.string.daily_discover),
                        songs = dailyDiscover,
                        viewModel = viewModel,
                        onSongClick = onSongClick
                    )
                }
            }

            if (oldFavorites.isNotEmpty()) {
                item {
                    HomeSongRail(
                        title = stringResource(R.string.fresh_finds_old_favorites),
                        songs = oldFavorites,
                        viewModel = viewModel,
                        onSongClick = onSongClick
                    )
                }
            }
        }

        HomeScrollShortcut(
            visible = showScrollShortcut,
            bottomPadding = paddingValues.calculateBottomPadding() + 88.dp,
            onClick = {
                scope.launch { listState.animateScrollToItem(0) }
            },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun WelcomeSection(onProfileClick: () -> Unit) {
    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> stringResource(R.string.good_morning)
        in 12..17 -> stringResource(R.string.good_afternoon)
        in 18..21 -> stringResource(R.string.good_evening)
        else -> stringResource(R.string.good_night)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = LiquidTypography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.personal_soundscape),
                style = LiquidTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Profile and Settings",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RecentlyPlayedSection(
    history: List<PlaybackHistoryEntity>,
    onHistoryClick: (PlaybackHistoryEntity) -> Unit,
    onSeeAll: () -> Unit
) {
    val mainItem = history.getOrNull(0)
    val smallItem1 = history.getOrNull(1)
    val smallItem2 = history.getOrNull(2)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.recently_played), style = LiquidTypography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                stringResource(R.string.see_all),
                style = LiquidTypography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }

        if (mainItem != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onHistoryClick(mainItem) }
            ) {
                AsyncImage(
                    model = rememberFridaArtworkRequest(mainItem.artworkUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 100f
                            )
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(stringResource(R.string.now_playing), style = LiquidTypography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = mainItem.title,
                            style = LiquidTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mainItem.artist.ifBlank { stringResource(R.string.unknown_artist) },
                            style = LiquidTypography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                    }
                }
            }
        } else {
            EmptyHomeState(text = stringResource(R.string.no_history))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (smallItem1 != null) {
                SmallHistoryTile(item = smallItem1, modifier = Modifier.weight(1f), onClick = { onHistoryClick(smallItem1) })
            }
            if (smallItem2 != null) {
                SmallHistoryTile(item = smallItem2, modifier = Modifier.weight(1f), onClick = { onHistoryClick(smallItem2) })
            }
        }
    }
}

@Composable
private fun SmallHistoryTile(item: PlaybackHistoryEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = rememberFridaArtworkRequest(item.artworkUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.artist.ifBlank { stringResource(R.string.unknown) }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TopArtistsSection(
    songs: List<Song>,
    history: List<PlaybackHistoryEntity>,
    playlists: List<Playlist>,
    viewModel: LibraryViewModels,
    onNavigateToArtist: (String, String) -> Unit
) {
    val unknownStr = stringResource(R.string.unknown)
    val artists = remember(songs, history, playlists, unknownStr) {
        buildTopArtists(songs, history, playlists, unknownStr)
    }

    if (artists.isEmpty()) return

    Column {
        Text(stringResource(R.string.top_artists), style = LiquidTypography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 20.dp)) {
            itemsIndexed(artists, key = { _, artistName -> artistName }) { index, artistName ->
                ArtistItem(
                    name = artistName,
                    isActive = index == 0,
                    viewModel = viewModel,
                    onClick = { imageUrl -> onNavigateToArtist(artistName, imageUrl.orEmpty()) }
                )
            }
        }
    }
}

@Composable
private fun ArtistItem(
    name: String,
    isActive: Boolean,
    viewModel: LibraryViewModels,
    onClick: (String?) -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = name) {
        value = viewModel.getArtistImageUrl(name)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(90.dp)
            .clickable { onClick(imageUrl) }
    ) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent, CircleShape).padding(4.dp)) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = rememberFridaArtworkRequest(imageUrl),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(name, style = LiquidTypography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeSongRail(
    title: String,
    songs: List<Song>,
    viewModel: LibraryViewModels,
    onSongClick: (Song) -> Unit
) {
    Column {
        Text(
            title,
            style = LiquidTypography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 20.dp)) {
            items(songs, key = { "home_rail_${it.id}_${it.title}" }) { song ->
                SongRailCard(song = song, viewModel = viewModel, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun SongRailCard(song: Song, viewModel: LibraryViewModels, onClick: () -> Unit) {
    val imageUrl by produceState<String?>(initialValue = song.artworkUri.toString(), key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }

    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = rememberFridaArtworkRequest(imageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Text(
            song.title,
            style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyHomeState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun HomeScrollShortcut(
    visible: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.padding(end = 22.dp, bottom = bottomPadding)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .liquidGlassEffect(23.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.back_to_top),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun buildTopArtists(
    songs: List<Song>,
    history: List<PlaybackHistoryEntity>,
    playlists: List<Playlist>,
    unknownLabel: String
): List<String> {
    val score = linkedMapOf<String, Int>()

    fun addArtist(artist: String, points: Int) {
        val clean = artist.trim()
        if (clean.isBlank() || clean.contains("unknown", ignoreCase = true) || clean.contains(unknownLabel, ignoreCase = true)) return
        score[clean] = (score[clean] ?: 0) + points
    }

    history.take(100).forEachIndexed { index, item -> addArtist(item.artist, (160 - index).coerceAtLeast(8)) }
    songs.forEach { addArtist(it.artist, 6) }
    val songsById = songs.associateBy { it.id }
    playlists.forEach { playlist ->
        playlist.songIds.mapNotNull { songsById[it] }.forEach { addArtist(it.artist, 14) }
    }

    return score.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(10)
}

private fun buildQuickPicks(songs: List<Song>, history: List<PlaybackHistoryEntity>): List<Song> {
    val byMetadata = songs.associateBy { metadataKey(it.title, it.artist) }
    val fromHistory = history.mapNotNull { byMetadata[metadataKey(it.title, it.artist)] }
    return (fromHistory + songs.sortedByDescending { it.dateAdded })
        .distinctBy { it.id }
        .take(14)
}

private fun buildDailyDiscover(songs: List<Song>, history: List<PlaybackHistoryEntity>): List<Song> {
    val played = history.map { metadataKey(it.title, it.artist) }.toSet()
    return songs
        .filterNot { metadataKey(it.title, it.artist) in played }
        .sortedWith(compareByDescending<Song> { it.dateAdded }.thenBy { it.title })
        .take(14)
}

private fun buildOldFavorites(songs: List<Song>, history: List<PlaybackHistoryEntity>): List<Song> {
    val byMetadata = songs.associateBy { metadataKey(it.title, it.artist) }
    return history
        .drop(3)
        .mapNotNull { byMetadata[metadataKey(it.title, it.artist)] }
        .distinctBy { it.id }
        .take(14)
}

private fun metadataKey(title: String, artist: String): String =
    "${title.trim().lowercase()}|${artist.trim().lowercase()}"
