package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.data.remote.innertube.YouTubeResult
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.theme.*
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: LibraryViewModels
) {
    var searchQuery by remember { mutableStateOf("") }
    val localSongs by viewModel.songs.collectAsState()
    val onlineResults by viewModel.youtubeSearchResults.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiquidBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(LiquidPrimary, Color.Transparent, LiquidSecondary)
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = paddingValues.calculateBottomPadding() + 100.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Explore",
                        style = LiquidTypography.displayLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .liquidGlassEffect(cornerRadius = 32.dp)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(0.6f))
                            Spacer(Modifier.width(12.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = LiquidTypography.bodyLarge.copy(color = Color.White),
                                cursorBrush = SolidColor(LiquidPrimary),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search YouTube Music...",
                                            style = LiquidTypography.bodyLarge,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null, tint = Color.White.copy(0.6f))
                                }
                            }
                        }
                    }
                }
            }

            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = LiquidPrimary,
                            strokeWidth = 4.dp
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                if (onlineResults.isNotEmpty()) {
                    item { SectionHeader("From YouTube", Icons.Default.Public) }
                    items(onlineResults) { result ->
                        LiquidSearchItem(
                            title = result.title,
                            artist = result.artist,
                            thumbnailUrl = result.thumbnailUrl,
                            onClick = { viewModel.playYouTubeSong(result) }
                        )
                    }
                }

                if (filteredLocal.isNotEmpty()) {
                    item { SectionHeader("Your Library", Icons.Default.MusicNote) }
                    items(filteredLocal) { song ->
                        LiquidSearchItem(
                            title = song.title,
                            artist = song.artist ?: "Unknown Artist",
                            onClick = { viewModel.playSong(song) }
                        )
                    }
                }
            }
        }

        if (isExtracting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = LiquidPrimary, strokeWidth = 4.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Igniting Motor...", style = LiquidTypography.titleMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(icon, null, tint = LiquidPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = LiquidTypography.titleSmall,
            color = LiquidPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LiquidSearchItem(
    title: String,
    artist: String,
    thumbnailUrl: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .liquidGlassEffect(cornerRadius = 20.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.MusicNote, null, tint = LiquidPrimary.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = LiquidTypography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = LiquidPrimary.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
    }
}