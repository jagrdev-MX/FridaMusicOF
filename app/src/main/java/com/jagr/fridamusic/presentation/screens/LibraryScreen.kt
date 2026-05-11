package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels

@Composable
fun Modifier.glassPanel(shape: RoundedCornerShape = RoundedCornerShape(12.dp)): Modifier {
    return this
        .clip(shape)
        // Usamos onSurface para que el cristal se adapte al modo claro/oscuro
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f)
                )
            ),
            shape = shape
        )
}

@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: LibraryViewModels
) {
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())

    val tabs = listOf("Songs", "Playlists", "Albums", "Artists")
    var selectedTab by remember { mutableStateOf(tabs[0]) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            top = 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 20.dp
        )
    ) {
        item {
            Text(
                text = "Library",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .glassPanel(RoundedCornerShape(50))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .clickable { selectedTab = tab }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.05.em,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        when (selectedTab) {
            "Songs" -> {
                item {
                    Text(
                        text = "Recent Songs",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                    )
                }
                items(songs, key = { it.id }) { song ->
                    LibrarySongItem(song, viewModel) { viewModel.playSong(song) }
                }
            }
            "Playlists" -> {
                item {
                    // Create New Playlist Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .glassPanel(RoundedCornerShape(12.dp))
                            .clickable { viewModel.createPlaylist("New Playlist " + (playlists.size + 1)) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(0.08f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Create New Playlist",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                items(playlists) { playlist ->
                    PlaylistListItem(playlist)
                }
            }
            "Albums" -> {
                val distinctAlbums = songs.distinctBy { it.albumId }.take(10)
                val rows = distinctAlbums.chunked(2)
                items(rows) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LibraryAlbumCard(rowItems[0], viewModel, Modifier.weight(1f))
                        if (rowItems.size > 1) {
                            LibraryAlbumCard(rowItems[1], viewModel, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            else -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("Coming Soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistListItem(playlist: com.jagr.fridamusic.domain.model.Playlist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .glassPanel(RoundedCornerShape(8.dp))
            .clickable { /* Open Playlist */ }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                // Puedes mantener este gradiente vibrante o usar MaterialTheme.colorScheme.primary/secondary
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${playlist.songIds.size} songs",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LibraryAlbumCard(song: Song, viewModel: LibraryViewModels, modifier: Modifier = Modifier) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .glassPanel(RoundedCornerShape(12.dp))
            .clickable { viewModel.playSong(song) }
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.8f)
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                        ),
                        startY = 50f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = song.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist ?: "Unknown Artist",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LibrarySongItem(song: Song, viewModel: LibraryViewModels, onClick: () -> Unit) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .glassPanel(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist ?: "Unknown",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}