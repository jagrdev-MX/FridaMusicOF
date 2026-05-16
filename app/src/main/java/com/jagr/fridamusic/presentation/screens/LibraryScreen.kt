package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import android.net.Uri
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels

@Composable
fun LibraryScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: LibraryViewModels
) {
    val songs by viewModel.songs.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val recentHistory by viewModel.recentHistory.collectAsState()

    val tabs = listOf(
        stringResource(R.string.songs_tab),
        stringResource(R.string.playlists_tab),
        stringResource(R.string.albums_tab),
        stringResource(R.string.artists_tab)
    )
    var selectedTab by remember { mutableStateOf(tabs[0]) }

    val chunkedAlbums = remember(songs) {
        songs.distinctBy { it.albumId }.take(10).chunked(2)
    }

    var playlistToDelete by remember { mutableStateOf<com.jagr.fridamusic.domain.model.Playlist?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("¿Eliminar Playlist?") },
            text = { Text("¿Estás seguro de que deseas eliminar '${playlistToDelete?.name}'? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistToDelete?.let { viewModel.deletePlaylist(it) }
                        playlistToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
            bottom = paddingValues.calculateBottomPadding() + 20.dp
        )
    ) {
        item {
            Text(
                text = stringResource(R.string.library),
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
                    .liquidGlassEffect(24.dp)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        when (selectedTab) {
            tabs[0] -> {
                item {
                    Text(
                        text = "Recent Songs",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (recentHistory.isNotEmpty()) {
                    items(
                        recentHistory,
                        key = { "recent_${it.id}" }
                    ) { history ->
                        val parsedUri = Uri.parse(history.songId)

                        val song = Song(
                            id = history.songId.hashCode().toLong(),
                            title = history.title,
                            artist = history.artist,
                            data = history.songId,
                            duration = 0L,
                            albumId = 0L,
                            uri = parsedUri,
                            artworkUri = history.artworkUrl?.let { Uri.parse(it) } ?: Uri.EMPTY
                        )

                        LibrarySongItem(song, viewModel) {
                            viewModel.playSong(song)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                item {
                    Text(
                        text = "Local Songs",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                items(
                    songs,
                    key = { "local_${it.id}" }
                ) { song ->
                    LibrarySongItem(song, viewModel) {
                        viewModel.playSong(song)
                    }
                }
            }
            tabs[1] -> {
                item {
                    val newPlaylistName = stringResource(R.string.new_playlist_format, playlists.size + 1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .liquidGlassEffect(12.dp)
                            .clickable { viewModel.createPlaylist(newPlaylistName) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(0.08f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.create_new_playlist), color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
                items(playlists) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        onPlay = { /* Implementar play playlist */ },
                        onShuffle = { /* Implementar aleatorio playlist */ },
                        onDelete = { playlistToDelete = playlist } // Abre el diálogo
                    )
                }
            }
            tabs[2] -> {
                items(chunkedAlbums) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
                        Text(stringResource(R.string.coming_soon), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlist: com.jagr.fridamusic.domain.model.Playlist,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .liquidGlassEffect(12.dp)
            .clickable { /* Navegar al detalle */ }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.songs_count, playlist.songIds.size), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Reproducir") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                    onClick = {
                        showMenu = false
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reproducción aleatoria") },
                    leadingIcon = { Icon(Icons.Default.Shuffle, null) },
                    onClick = {
                        showMenu = false
                        onShuffle()
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                DropdownMenuItem(
                    text = { Text("Eliminar playlist", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryAlbumCard(song: Song, viewModel: LibraryViewModels, modifier: Modifier = Modifier) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) { value = viewModel.getSongImageUrl(song) }
    Box(modifier = modifier.aspectRatio(1f).liquidGlassEffect(16.dp).clickable { viewModel.playSong(song) }) {
        if (imageUrl != null) AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.8f))
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.9f)), startY = 50f)))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(song.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: stringResource(R.string.unknown_artist), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LibrarySongItem(song: Song, viewModel: LibraryViewModels, onClick: () -> Unit) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) { value = viewModel.getSongImageUrl(song) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .liquidGlassEffect(8.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist ?: stringResource(R.string.unknown), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}