package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.FridaEmptyState
import com.jagr.fridamusic.presentation.components.FridaSectionHeader
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModel
import com.jagr.fridamusic.presentation.viewmodels.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    songs: List<Song>,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onSongClick: (Song) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenLibrarySection: (String) -> Unit
) {
    val recentHistory by viewModel.recentHistory.collectAsState()
    val history by viewModel.fullHistory.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val queueState by playbackViewModel.queueState.collectAsState()

    val favorites by produceState<Playlist?>(initialValue = null, playlists) {
        value = withContext(Dispatchers.Default) {
            playlists.firstOrNull { it.name == "Favorites" || it.name == "Me gusta" }
        }
    }

    val recentlyAdded by produceState<List<Song>>(initialValue = emptyList(), songs) {
        value = withContext(Dispatchers.Default) {
            songs.sortedByDescending { it.dateAdded }.take(10)
        }
    }

    val historySongs by produceState<List<Song>>(initialValue = emptyList(), history, songs) {
        value = withContext(Dispatchers.Default) {
            val songsByUri = songs.associateBy { it.uri.toString() }
            val songsByMetadata = songs.associateBy { "${it.title}\u0000${it.artist}" }
            history.mapNotNull { entry ->
                songsByUri[entry.songId] ?: songsByMetadata["${entry.title}\u0000${entry.artist}"]
            }.distinctBy { it.id }
        }
    }

    val recentAlbums by produceState<List<Song>>(initialValue = emptyList(), historySongs) {
        value = withContext(Dispatchers.Default) {
            historySongs.distinctBy { it.album.ifBlank { it.albumId.toString() } }.take(10)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item {
            WelcomeSection(onProfileClick = onNavigateToSettings)
        }

        item {
            QuickAccessSection(
                onHistory = { onOpenLibrarySection("HISTORY") },
                onFavorites = { onOpenLibrarySection("FAVORITES") },
                onMostPlayed = { onOpenLibrarySection("MOST_PLAYED") },
                onShuffle = {
                    if (queueState.current == null && songs.isNotEmpty()) {
                        playbackViewModel.playSongs(songs, shuffle = true)
                    } else {
                        playbackViewModel.toggleShuffleMode()
                    }
                }
            )
        }

        item {
            RecentlyPlayedSection(
                history = recentHistory,
                viewModel = viewModel,
                onHistoryClick = { item ->
                    playbackViewModel.playHistoryItem(item, songs)
                },
                onSeeAll = { onOpenLibrarySection("HISTORY") }
            )
        }

        item {
            TopArtistsSection(history = history, viewModel = viewModel)
        }

        item {
            HomeSongCarousel(
                title = stringResource(R.string.recently_added_songs),
                songs = recentlyAdded,
                viewModel = viewModel,
                onSongClick = onSongClick
            )
        }

        item {
            HomeSongCarousel(
                title = stringResource(R.string.recently_played_albums),
                songs = recentAlbums,
                viewModel = viewModel,
                onSongClick = onSongClick
            )
        }

        if (songs.isEmpty() && history.isEmpty()) {
            item {
                FridaEmptyState(
                    title = stringResource(R.string.no_local_songs),
                    subtitle = stringResource(R.string.library_empty_hint),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickAccessSection(
    onHistory: () -> Unit,
    onFavorites: () -> Unit,
    onMostPlayed: () -> Unit,
    onShuffle: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Default.History, stringResource(R.string.history_tab), onHistory),
        Triple(Icons.Default.Favorite, stringResource(R.string.favorites_playlist_name), onFavorites),
        Triple(Icons.Default.TrendingUp, stringResource(R.string.most_played), onMostPlayed),
        Triple(Icons.Default.Shuffle, stringResource(R.string.shuffle), onShuffle)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.forEach { (icon, label, action) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f))
                    .clickable(onClick = action)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = LiquidTypography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeSongCarousel(
    title: String,
    songs: List<Song>,
    viewModel: LibraryViewModel,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FridaSectionHeader(title = title, modifier = Modifier.padding(horizontal = 20.dp))
        if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_played_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LiquidTypography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = songs,
                key = { _, song -> "home_song_${song.id}" },
                contentType = { _, _ -> "home_song_card" }
            ) { _, song ->
                HomeMediaCard(song = song, viewModel = viewModel, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun HomeArtistCarousel(
    songs: List<Song>,
    viewModel: LibraryViewModel,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FridaSectionHeader(
            title = stringResource(R.string.recent_artists),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_played_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LiquidTypography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = songs,
                key = { _, song -> "home_artist_${song.artist}_${song.id}" },
                contentType = { _, _ -> "home_artist_card" }
            ) { _, song ->
                val artwork by produceState<String?>(initialValue = null, song) { value = viewModel.getSongImageUrl(song) }
                Column(
                    modifier = Modifier.width(92.dp).clickable { onSongClick(song) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FridaArtworkImage(
                        model = artwork,
                        contentDescription = song.artist,
                        modifier = Modifier.size(84.dp),
                        shape = CircleShape,
                        contentScale = ContentScale.Crop,
                        requestSizePx = 180
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LiquidTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMediaCard(
    song: Song,
    viewModel: LibraryViewModel,
    onClick: () -> Unit
) {
    val artwork by produceState<String?>(initialValue = null, song) { value = viewModel.getSongImageUrl(song) }
    Column(
        modifier = Modifier.width(146.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box {
            FridaArtworkImage(
                model = artwork,
                contentDescription = song.title,
                modifier = Modifier.size(146.dp),
                shape = RoundedCornerShape(18.dp),
                requestSizePx = 320
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
            }
        }
        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LiquidTypography.bodySmall)
        Text(
            song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LiquidTypography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
    viewModel: LibraryViewModel,
    onHistoryClick: (PlaybackHistoryEntity) -> Unit,
    onSeeAll: () -> Unit
) {
    val mainSong = history.getOrNull(0)
    val secondaryHistory = remember(history) { history.drop(1) }

    val mainSongImageUrl by produceState<String?>(initialValue = null, key1 = mainSong) {
        value = mainSong?.let { viewModel.getHistoryImageUrl(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.recently_played), style = LiquidTypography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = stringResource(R.string.see_all),
                style = LiquidTypography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onSeeAll)
            )
        }

        if (mainSong != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .aspectRatio(2f / 1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onHistoryClick(mainSong) }
            ) {
                FridaArtworkImage(
                    model = mainSongImageUrl,
                    contentDescription = mainSong.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    contentScale = ContentScale.Crop,
                    requestSizePx = 640
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
                        Text(stringResource(R.string.last_played), style = LiquidTypography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = mainSong.title,
                            style = LiquidTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mainSong.artist.ifBlank { stringResource(R.string.unknown_artist) },
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
            Text(
                text = stringResource(R.string.nothing_played_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LiquidTypography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        if (secondaryHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(
                    items = secondaryHistory,
                    key = { _, item -> "recent_history_${item.id}" },
                    contentType = { _, _ -> "recent_history_tile" }
                ) { _, historyItem ->
                    SmallTile(
                        history = historyItem,
                        viewModel = viewModel,
                        modifier = Modifier.fillParentMaxWidth(0.45f),
                        onClick = { onHistoryClick(historyItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallTile(
    history: PlaybackHistoryEntity,
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = history) {
        value = viewModel.getHistoryImageUrl(history)
    }

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
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = history.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Crop,
                requestSizePx = 96
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(history.title, style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(history.artist.ifBlank { stringResource(R.string.unknown) }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class HomeArtistStat(
    val name: String,
    val artworkUrl: String?,
    val playCount: Int,
    val lastPlayedAt: Long
)

@Composable
private fun TopArtistsSection(history: List<PlaybackHistoryEntity>, viewModel: LibraryViewModel) {
    val unknownStr = stringResource(R.string.unknown)

    val artists by produceState<List<HomeArtistStat>>(initialValue = emptyList(), history, unknownStr) {
        value = withContext(Dispatchers.Default) {
            history
                .filter { item ->
                    item.artist.isNotBlank() &&
                            !item.artist.contains(unknownStr, ignoreCase = true) &&
                            !item.artist.contains("unknown", ignoreCase = true)
                }
                .groupBy { it.artist.trim().lowercase() }
                .values
                .map { plays ->
                    val latest = plays.maxBy { it.playedAt }
                    HomeArtistStat(
                        name = latest.artist.trim(),
                        artworkUrl = latest.artworkUrl?.takeIf { it.isNotBlank() },
                        playCount = plays.sumOf { it.playCount },
                        lastPlayedAt = latest.playedAt
                    )
                }
                .sortedWith(
                    compareByDescending<HomeArtistStat> { it.playCount }
                        .thenByDescending { it.lastPlayedAt }
                )
                .take(10)
        }
    }

    if (artists.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.top_artists),
            style = LiquidTypography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = artists,
                key = { _, artist -> "top_artist_${artist.name}" },
                contentType = { _, _ -> "top_artist_card" }
            ) { index, artist ->
                ArtistItem(artist = artist, isActive = index == 0, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ArtistItem(
    artist: HomeArtistStat,
    isActive: Boolean,
    viewModel: LibraryViewModel
) {
    val imageUrl by produceState<String?>(initialValue = artist.artworkUrl, key1 = artist) {
        value = artist.artworkUrl ?: viewModel.getArtistImageUrl(artist.name)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent, CircleShape).padding(4.dp)) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                FridaArtworkImage(
                    model = imageUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    requestSizePx = 180
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(artist.name, style = LiquidTypography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
