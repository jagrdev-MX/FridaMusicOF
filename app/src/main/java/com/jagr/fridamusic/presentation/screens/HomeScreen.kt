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
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.FridaEmptyState
import com.jagr.fridamusic.presentation.components.FridaSectionHeader
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import java.util.Calendar

@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    songs: List<Song>,
    currentSong: Song?,
    viewModel: LibraryViewModels,
    onSongClick: (Song) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenLibrarySection: (String) -> Unit
) {
    val history by viewModel.fullHistory.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val favorites = remember(playlists) {
        playlists.firstOrNull { it.name == "Favorites" || it.name == "Me gusta" }
    }
    val favoriteSongs = remember(favorites, songs) {
        val ids = favorites?.songIds.orEmpty().toSet()
        songs.filter { it.id in ids }
    }
    val recentlyAdded = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(10) }
    val historySongs = remember(history, songs) {
        history.mapNotNull { entry ->
            songs.firstOrNull { song ->
                song.uri.toString() == entry.songId ||
                    (song.title == entry.title && song.artist == entry.artist)
            }
        }.distinctBy { it.id }
    }
    val recentAlbums = remember(historySongs) {
        historySongs.distinctBy { it.album.ifBlank { it.albumId.toString() } }.take(10)
    }
    val recentArtists = remember(historySongs) {
        historySongs.distinctBy { it.artist }.take(10)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 80.dp,
            start = 20.dp,
            end = 20.dp
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
                onShuffle = { viewModel.shuffleLibrary() }
            )
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

        item {
            HomeArtistCarousel(
                songs = recentArtists,
                viewModel = viewModel,
                onSongClick = onSongClick
            )
        }

        if (songs.isNotEmpty()) {
            item {
                RecentlyPlayedSection(
                    songs = songs,
                    currentSong = currentSong,
                    viewModel = viewModel,
                    onSongClick = onSongClick
                )
            }

            item {
                TopArtistsSection(songs = songs, viewModel = viewModel)
            }
        } else {
            item {
                FridaEmptyState(
                    title = stringResource(R.string.no_local_songs),
                    subtitle = stringResource(R.string.library_empty_hint)
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
        modifier = Modifier.fillMaxWidth(),
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
    viewModel: LibraryViewModels,
    onSongClick: (Song) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FridaSectionHeader(title = title)
        if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_played_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LiquidTypography.bodySmall
            )
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(songs) { _, song ->
                HomeMediaCard(song = song, viewModel = viewModel, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun HomeArtistCarousel(
    songs: List<Song>,
    viewModel: LibraryViewModels,
    onSongClick: (Song) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FridaSectionHeader(title = stringResource(R.string.recent_artists))
        if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_played_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = LiquidTypography.bodySmall
            )
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(songs) { _, song ->
                val artwork by produceState<String?>(null, song) { value = viewModel.getSongImageUrl(song) }
                Column(
                    modifier = Modifier.width(92.dp).clickable { onSongClick(song) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FridaArtworkImage(
                        model = artwork,
                        contentDescription = song.artist,
                        modifier = Modifier.size(84.dp),
                        shape = CircleShape
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
    viewModel: LibraryViewModels,
    onClick: () -> Unit
) {
    val artwork by produceState<String?>(null, song) { value = viewModel.getSongImageUrl(song) }
    Column(
        modifier = Modifier.width(146.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box {
            FridaArtworkImage(
                model = artwork,
                contentDescription = song.title,
                modifier = Modifier.size(146.dp),
                shape = RoundedCornerShape(18.dp)
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
    songs: List<Song>,
    currentSong: Song?,
    viewModel: LibraryViewModels,
    onSongClick: (Song) -> Unit
) {
    val mainSong = remember(songs, currentSong) { currentSong ?: songs.firstOrNull() }
    val smallSong1 = remember(songs) { songs.getOrNull(1) }
    val smallSong2 = remember(songs) { songs.getOrNull(2) }

    val mainSongImageUrl by produceState<String?>(initialValue = null, key1 = mainSong) {
        value = mainSong?.let { viewModel.getSongImageUrl(it) }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.recently_played), style = LiquidTypography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.see_all), style = LiquidTypography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }

        if (mainSong != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSongClick(mainSong) }
            ) {
                FridaArtworkImage(
                    model = mainSongImageUrl,
                    contentDescription = mainSong.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp)
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
                            text = mainSong.title,
                            style = LiquidTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mainSong.artist ?: stringResource(R.string.unknown_artist),
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (smallSong1 != null) {
                SmallTile(song = smallSong1, viewModel = viewModel, modifier = Modifier.weight(1f), onClick = { onSongClick(smallSong1) })
            }
            if (smallSong2 != null) {
                SmallTile(song = smallSong2, viewModel = viewModel, modifier = Modifier.weight(1f), onClick = { onSongClick(smallSong2) })
            }
        }
    }
}

@Composable
private fun SmallTile(song: Song, viewModel: LibraryViewModels, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
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
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: stringResource(R.string.unknown), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TopArtistsSection(songs: List<Song>, viewModel: LibraryViewModels) {
    val unknownStr = stringResource(R.string.unknown)
    val artists = remember(songs, unknownStr) {
        songs.mapNotNull { it.artist }
            .filter { it.isNotBlank() && !it.contains(unknownStr, ignoreCase = true) && !it.contains("unknown", ignoreCase = true) }
            .distinct()
            .take(10)
    }

    if (artists.isEmpty()) return

    Column {
        Text(stringResource(R.string.top_artists), style = LiquidTypography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 20.dp)) {
            itemsIndexed(artists) { index, artistName ->
                ArtistItem(name = artistName, isActive = index == 0, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ArtistItem(name: String, isActive: Boolean, viewModel: LibraryViewModels) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = name) {
        value = viewModel.getArtistImageUrl(name)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(90.dp)) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent, CircleShape).padding(4.dp)) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                FridaArtworkImage(
                    model = imageUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(name, style = LiquidTypography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
