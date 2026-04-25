package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.theme.*

@Composable
fun LibraryScreen(paddingValues: PaddingValues, listState: LazyListState) {
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { LibraryHeaderSection() }
        item { SegmentedControlSection() }
        item { AlbumsGridSection() }
        item { RecentSongsListSection() }
    }
}

@Composable
fun LibraryHeaderSection() {
    Text(
        text = "Library",
        style = LiquidTypography.displayLarge,
        color = LiquidPrimary
    )
}

@Composable
fun SegmentedControlSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassEffect(cornerRadius = 50.dp)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SegmentButton(text = "Songs", isSelected = true, modifier = Modifier.weight(1f))
        SegmentButton(text = "Albums", isSelected = false, modifier = Modifier.weight(1f))
        SegmentButton(text = "Artists", isSelected = false, modifier = Modifier.weight(1f))
        SegmentButton(text = "Folders", isSelected = false, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SegmentButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .clickable {  }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = LiquidTypography.labelSmall,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun AlbumsGridSection() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AlbumCard(title = "Neon Nights", artist = "Synthwave Collective", modifier = Modifier.weight(1f))
            AlbumCard(title = "Pastel Dreams", artist = "Luna Echo", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AlbumCard(title = "Wooden Floors", artist = "The Rustics", modifier = Modifier.weight(1f))
            AlbumCard(title = "Club Anthem", artist = "DJ Horizon", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun AlbumCard(title: String, artist: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = LiquidTypography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentSongsListSection() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Recent Songs",
            style = LiquidTypography.headlineMedium,
            color = LiquidOnSurface,
            modifier = Modifier.padding(bottom = 8.dp)

        )
        SongListItem(title = "Midnight Drive", artist = "Synthwave Collective")
        SongListItem(title = "Starlight Fade", artist = "Luna Echo")
        SongListItem(title = "Dusty Roads", artist = "The Rustics")
    }
}

@Composable
fun SongListItem(title: String, artist: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .liquidGlassEffect(cornerRadius = 12.dp)
            .clickable {  }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = LiquidTypography.bodySmall,
                color = LiquidOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = {  }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}