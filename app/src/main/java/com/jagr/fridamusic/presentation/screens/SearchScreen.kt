package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jagr.fridamusic.data.remote.innertube.ResultType
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: LibraryViewModels,
    onNavigateToArtist: (String, String) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    val localSongs by viewModel.songs.collectAsState()
    val onlineResults by viewModel.youtubeSearchResults.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val filters = remember { listOf("Todo", "Canciones", "Artistas", "Listas") }
    var selectedFilter by remember { mutableStateOf(filters[0]) }

    val filteredOnline = remember(onlineResults, selectedFilter) {
        when (selectedFilter) {
            "Canciones" -> onlineResults.filter { it.type == ResultType.SONG }
            "Artistas" -> onlineResults.filter { it.type == ResultType.ARTIST }
            "Listas" -> onlineResults.filter { it.type == ResultType.PLAYLIST }
            else -> onlineResults
        }
    }

    val filteredLocal = remember(searchQuery, localSongs) {
        if (searchQuery.isBlank()) emptyList()
        else localSongs.filter { it.title.contains(searchQuery, ignoreCase = true) || (it.artist?.contains(searchQuery, ignoreCase = true) == true) }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            delay(500)
            viewModel.searchYouTube(searchQuery)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val bgGradient = remember(primaryColor) {
        Brush.verticalGradient(colors = listOf(primaryColor, Color.Transparent))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.05f)
                .background(bgGradient)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = paddingValues.calculateBottomPadding() + 100.dp
            )
        ) {
            item {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClearQuery = { searchQuery = "" }
                )
            }

            if (searchQuery.isNotBlank()) {
                item {
                    FilterRow(
                        filters = filters,
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it }
                    )
                }
            }

            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                    }
                }
            } else if (searchQuery.isNotBlank()) {

                val topArtistResult = onlineResults.firstOrNull { it.type == ResultType.ARTIST }

                if (topArtistResult != null && (selectedFilter == "Todo" || selectedFilter == "Artistas")) {
                    item {
                        ArtistTopResult(
                            artistName = topArtistResult.title,
                            imageUrl = topArtistResult.thumbnailUrl,
                            onClick = { onNavigateToArtist(topArtistResult.title, topArtistResult.thumbnailUrl) }
                        )
                    }
                }

                if (filteredOnline.isNotEmpty() && selectedFilter != "Artistas") {
                    item {
                        SectionTitle(title = if (selectedFilter == "Todo") "Resultados" else selectedFilter)
                    }
                    items(filteredOnline.filter { it.type != ResultType.ARTIST }) { result ->
                        SpotifyStyleSongItem(
                            title = result.title,
                            artist = result.artist ?: "Artista desconocido",
                            thumbnailUrl = result.thumbnailUrl,
                            isLocal = false,
                            isPlaylist = result.type == ResultType.PLAYLIST,
                            onClick = {
                                if (result.type == ResultType.SONG) viewModel.playYouTubeSong(result)
                            }
                        )
                    }
                }

                if (filteredLocal.isNotEmpty() && (selectedFilter == "Todo" || selectedFilter == "Canciones")) {
                    item {
                        SectionTitle(title = "En tu biblioteca")
                    }
                    items(filteredLocal) { song ->
                        SpotifyStyleSongItem(
                            title = song.title,
                            artist = song.artist ?: "Unknown Artist",
                            isLocal = true,
                            isPlaylist = false,
                            onClick = { viewModel.playSong(song) }
                        )
                    }
                }
            }
        }

        if (isExtracting) {
            ExtractionOverlay()
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClearQuery: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = LiquidTypography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "¿Qué quieres escuchar?",
                            style = LiquidTypography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(filters: List<String>, selectedFilter: String, onFilterSelected: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { filter ->
            val isSelected = selectedFilter == filter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = filter,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun ArtistTopResult(artistName: String, imageUrl: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artistName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = "Artista", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }

        Box(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Seguir", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpotifyStyleSongItem(
    title: String,
    artist: String,
    thumbnailUrl: String = "",
    isLocal: Boolean,
    isPlaylist: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isLocal) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("YT", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(text = if (isPlaylist) "Playlist • $artist" else "Canción • $artist", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isPlaylist) {
                IconButton(onClick = { }) { Icon(Icons.Default.AddCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
            }
            IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun ExtractionOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Cargando pista...",
                style = LiquidTypography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}