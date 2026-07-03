package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.components.FridaEmptyState
import com.jagr.fridamusic.presentation.components.FridaSectionHeader
import com.jagr.fridamusic.presentation.components.ModernAlbumCard
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
    val history by viewModel.fullHistory.collectAsState()

    val recentlyAdded by produceState(initialValue = emptyList<Song>(), songs) {
        value = withContext(Dispatchers.Default) {
            songs.sortedByDescending { it.dateAdded }.take(10)
        }
    }

    val historySongs by produceState(initialValue = emptyList<Song>(), history, songs) {
        value = withContext(Dispatchers.Default) {
            val songsByUri = songs.associateBy { it.uri.toString() }
            val songsByMetadata = songs.associateBy { "${it.title}\u0000${it.artist}" }
            history.mapNotNull { entry ->
                songsByUri[entry.songId] ?: songsByMetadata["${entry.title}\u0000${entry.artist}"]
            }.distinctBy { it.id }
        }
    }

    val recentAlbums by produceState(initialValue = emptyList<Song>(), historySongs) {
        value = withContext(Dispatchers.Default) {
            historySongs.distinctBy { it.album.ifBlank { it.albumId.toString() } }.take(10)
        }
    }

    val forYouSongs by produceState(initialValue = emptyList<Song>(), songs) {
        value = withContext(Dispatchers.Default) {
            songs.stableHomeShuffle().take(10)
        }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = paddingValues.calculateBottomPadding() + navBarPadding + 100.dp
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
                onShuffle = { playbackViewModel.toggleShuffleMode() }
            )
        }

        item {
            HorizontalSection(
                title = "Para ti",
                songs = forYouSongs,
                viewModel = viewModel,
                onSongClick = onSongClick,
                onPlayClick = { playbackViewModel.playSong(it, forYouSongs) }
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
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).sp
            ),
            color = MaterialTheme.colorScheme.primary
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick)
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Profile",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Estructura de datos para simplificar la creación de los accesos rápidos
private data class QuickAccessItemData(
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickAccessSection(
    onHistory: () -> Unit,
    onFavorites: () -> Unit,
    onMostPlayed: () -> Unit,
    onShuffle: () -> Unit
) {
    val actions = listOf(
        QuickAccessItemData(
            icon = Icons.Default.History,
            label = stringResource(R.string.history_tab),
            color = Color(0xFF60A5FA), // Azul
            onClick = onHistory
        ),
        QuickAccessItemData(
            icon = Icons.Default.Favorite,
            label = stringResource(R.string.favorites_playlist_name),
            color = Color(0xFFF43F5E), // Rosa/Rojo
            onClick = onFavorites
        ),
        QuickAccessItemData(
            icon = Icons.Default.TrendingUp,
            label = stringResource(R.string.most_played),
            color = Color(0xFF34D399), // Verde
            onClick = onMostPlayed
        ),
        QuickAccessItemData(
            icon = Icons.Default.Shuffle,
            label = stringResource(R.string.shuffle),
            color = Color(0xFFFBBF24), // Amarillo/Naranja
            onClick = onShuffle
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        actions.forEach { item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = item.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        // Fondo con 15% de opacidad basado en el color del icono
                        .background(item.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = item.color,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HorizontalSection(
    title: String,
    songs: List<Song>,
    viewModel: LibraryViewModel,
    onSongClick: (Song) -> Unit,
    onPlayClick: (Song) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(songs.size) { index ->
                val song = songs[index]
                val artworkUrl by produceState<String?>(initialValue = null, song) {
                    value = viewModel.getSongImageUrl(song)
                }
                ModernAlbumCard(
                    song = song,
                    artworkUrl = artworkUrl,
                    onClick = { onSongClick(song) },
                    onPlayClick = { onPlayClick(song) }
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
        FridaSectionHeader(title = title, modifier = Modifier.padding(horizontal = 24.dp))
        if (songs.isEmpty()) {
            Text(
                text = stringResource(R.string.nothing_played_yet),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            return@Column
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
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
        }
        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text(
            song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun List<Song>.stableHomeShuffle(): List<Song> {
    val daySeed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toLong()
    return sortedBy { song ->
        val key = song.id xor song.title.hashCode().toLong() xor daySeed
        key * 1103515245L + 12345L
    }
}
