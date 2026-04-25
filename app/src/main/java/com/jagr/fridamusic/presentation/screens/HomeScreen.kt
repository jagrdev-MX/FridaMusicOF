package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(paddingValues: PaddingValues, listState: LazyListState) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 130.dp,
                bottom = paddingValues.calculateBottomPadding() + 80.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item { WelcomeSection() }
            item { RecentlyPlayedSection() }
            item { TopArtistsSection() }
        }
    }
}

@Composable
fun TopArtistsSection() {
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
            val artists = listOf("Luna Ray", "The Dwellers", "Neon Pulse", "Echo Room")
            items(artists.size) { index ->
                ArtistItem(
                    name = artists[index],
                    isActive = index == 0 // Solo el primero tiene el borde activo en tu diseño
                )
            }
        }
    }
}

@Composable
fun ArtistItem(name: String, isActive: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isActive) LiquidPrimary.copy(alpha = 0.3f) else Color.Transparent,
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

@Composable
fun RecentlyPlayedSection() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recently Played", style = LiquidTypography.headlineMedium, color = Color.White)
            Text("See All", style = LiquidTypography.bodySmall, color = LiquidPrimary)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.DarkGray)
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
                Column {
                    Text("NOW PLAYING", style = LiquidTypography.labelSmall, color = LiquidPrimary)
                    Text("Midnight Synthwave", style = LiquidTypography.headlineMedium, color = Color.White)
                    Text("Various Artists", style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.7f))
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SmallTile(title = "Acoustic Chill", subtitle = "Indie Vibes", modifier = Modifier.weight(1f))
            SmallTile(title = "Lo-Fi Beats", subtitle = "Study Session", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SmallTile(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = Modifier
            .liquidGlassEffect(cornerRadius = 12.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = LiquidTypography.bodySmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}

@Composable
fun WelcomeSection() {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (currentHour) {
        in 5..11 -> "Good Morning"
        in 12..17 -> "Good Afternoon"
        in 18..21 -> "Good Evening"
        else -> "Good Night"
    }

    Column {
        Text(
            text = greeting,
            style = LiquidTypography.displayLarge,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your personal soundscape.",
            style = LiquidTypography.bodyLarge,
            color = LiquidOnSurfaceVariant
        )
    }
}