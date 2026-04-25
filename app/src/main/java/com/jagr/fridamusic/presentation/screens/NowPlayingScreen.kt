package com.jagr.fridamusic.presentation.screens

import android.graphics.Color.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.presentation.theme.*

@Composable
fun NowPlayingScreen(onCollapse: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
    ) {
        AmbientBackgroundGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NowPlayingTopBar(onCollapse = onCollapse)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 28.dp)
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlbumArtSection(modifier = Modifier.weight(1f, fill = false))

                Column(
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    TrackInfoSection()
                    SeekBarSection()
                    PlayerControlsSection()
                }
            }
        }

        NowPlayingBottomBar(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun AmbientBackgroundGlow() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = (-50).dp)
                .size(300.dp)
                .background(LiquidPrimary.copy(alpha = 0.15f), CircleShape)
                .blur(80.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .size(350.dp)
                .background(LiquidTertiary.copy(alpha = 0.15f), CircleShape)
                .blur(100.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .background(Color(0xFFFFAFD5).copy(alpha = 0.05f), CircleShape)
                .blur(80.dp)
        )
    }
}

@Composable
fun NowPlayingTopBar(onCollapse: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White.copy(alpha = 0.6f))
        }
        Text(
            text = "NOW PLAYING",
            style = LiquidTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFFFF99CC),
            letterSpacing = 1.sp
        )
        IconButton(onClick = {  }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun AlbumArtSection(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(LiquidPrimary.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                .blur(30.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .background(LiquidSurfaceContainer.copy(alpha = 0.3f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
        }
    }
}

@Composable
fun TrackInfoSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ethereal Drift",
                style = LiquidTypography.displayLarge.copy(fontSize = 32.sp),
                color = LiquidOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Luna Shadows",
                style = LiquidTypography.bodyLarge,
                color = LiquidOnSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = {  }) {
            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = Color(0xFFFF99CC), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun SeekBarSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(LiquidSurfaceHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.33f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFF99CC), Color(0xFFBBB0FD))
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1:24", style = LiquidTypography.labelSmall, color = LiquidOnSurfaceVariant.copy(alpha = 0.6f))
            Text("-2:21", style = LiquidTypography.labelSmall, color = LiquidOnSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun PlayerControlsSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Refresh, contentDescription = "Shuffle", tint = LiquidOnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(28.dp).clickable {  })
        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = LiquidOnSurface, modifier = Modifier.size(40.dp).clickable {  })
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
                .clickable {  },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = LiquidOnSurface, modifier = Modifier.size(40.dp).clickable {  })
        Icon(Icons.Default.Refresh, contentDescription = "Repeat", tint = LiquidOnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(28.dp).clickable {  })
    }
}

@Composable
fun NowPlayingBottomBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 24.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {  }) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Queue", tint = Color(0xFFFF99CC))
        }
        IconButton(onClick = {  }) {
            Icon(Icons.AutoMirrored.Filled.Subject, contentDescription = "Lyrics", tint = Color.White.copy(alpha = 0.4f))
        }
        IconButton(onClick = {  }) {
            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White.copy(alpha = 0.4f))
        }
    }
}