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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.theme.LiquidOnSurfaceVariant
import com.jagr.fridamusic.presentation.theme.LiquidPrimary
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import java.util.Calendar

@Composable
fun HomeScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            top = 20.dp,
            bottom = paddingValues.calculateBottomPadding() + 80.dp,
            start = 20.dp,
            end = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item {
            WelcomeSection(onProfileClick = onNavigateToSettings)
        }

        if (songs.isNotEmpty()) {
            item {
                RecentlyPlayedSection(
                    songs = songs,
                    currentSong = currentSong,
                    onSongClick = onSongClick
                )
            }

            item {
                TopArtistsSection(songs = songs)
            }
        }
    }
}

@Composable
fun WelcomeSection(onProfileClick: () -> Unit) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (currentHour) {
        in 5..11 -> "Good Morning"
        in 12..17 -> "Good Afternoon"
        in 18..21 -> "Good Evening"
        else -> "Good Night"
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
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your personal liquid soundscape.",
                style = LiquidTypography.bodyLarge,
                color = LiquidOnSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile and Settings",
                tint = Color.White
            )
        }
    }
}

@Composable
fun RecentlyPlayedSection(songs: List<Song>, currentSong: Song?, onSongClick: (Song) -> Unit) {
    // Tomamos la canción actual si hay una, sino la primera de la lista
    val mainSong = currentSong ?: songs.firstOrNull()
    val smallSong1 = songs.getOrNull(1)
    val smallSong2 = songs.getOrNull(2)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recently Played", style = LiquidTypography.headlineMedium, color = Color.White)
            Text("See All", style = LiquidTypography.bodySmall, color = LiquidPrimary, fontWeight = FontWeight.Medium)
        }

        if (mainSong != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.DarkGray)
                    .clickable { onSongClick(mainSong) }
            ) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("NOW PLAYING", style = LiquidTypography.labelSmall, color = LiquidPrimary)
                        Text(
                            text = mainSong.title,
                            style = LiquidTypography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mainSong.artist ?: "Unknown Artist",
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
                            .background(LiquidPrimary.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (smallSong1 != null) {
                SmallTile(
                    song = smallSong1,
                    modifier = Modifier.weight(1f),
                    onClick = { onSongClick(smallSong1) }
                )
            }
            if (smallSong2 != null) {
                SmallTile(
                    song = smallSong2,
                    modifier = Modifier.weight(1f),
                    onClick = { onSongClick(smallSong2) }
                )
            }
        }
    }
}

@Composable
fun SmallTile(song: Song, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = LiquidTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist ?: "Unknown",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TopArtistsSection(songs: List<Song>) {
    // Filtramos la lista de canciones para obtener artistas únicos reales
    val artists = songs.mapNotNull { it.artist }
        .filter { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) }
        .distinct()
        .take(10)

    if (artists.isEmpty()) return

    Column {
        Text(
            text = "Top Artists",
            style = LiquidTypography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 20.dp)
        ) {
            itemsIndexed(artists) { index, artistName ->
                ArtistItem(
                    name = artistName,
                    isActive = index == 0 // El primero tiene el borde activo tal como en tu diseño
                )
            }
        }
    }
}

@Composable
fun ArtistItem(name: String, isActive: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(90.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isActive) LiquidPrimary.copy(alpha = 0.4f) else Color.Transparent,
                    shape = CircleShape
                )
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = name,
            style = LiquidTypography.bodySmall,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}