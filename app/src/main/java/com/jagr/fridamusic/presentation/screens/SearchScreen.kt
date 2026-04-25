package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.presentation.components.liquidGlassEffect
import com.jagr.fridamusic.presentation.theme.*

@Composable
fun SearchScreen(paddingValues: PaddingValues, listState: LazyListState) {
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
        item { SearchHeaderSection() }
        item { RecentSearchesSection() }
        item { BrowseAllSection() }
    }
}

@Composable
fun SearchHeaderSection() {
    var searchQuery by remember { mutableStateOf("") }

    Column {
        Text(
            text = "Search",
            style = LiquidTypography.displayLarge,
            color = LiquidOnSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassEffect(cornerRadius = 50.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = LiquidOnSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = LiquidTypography.bodyLarge.copy(color = LiquidOnSurface),
                cursorBrush = SolidColor(LiquidPrimary),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Artists, songs, or podcasts",
                            style = LiquidTypography.bodyLarge,
                            color = LiquidOnSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
fun RecentSearchesSection() {
    Column {
        Text(
            text = "Recent Searches",
            style = LiquidTypography.headlineMedium,
            color = LiquidOnSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 20.dp)
        ) {
            val recentItems = listOf("Lofi Beats", "The Weeknd", "Synthwave")
            items(recentItems) { item ->
                RecentSearchChip(text = item)
            }
        }
    }
}

@Composable
fun RecentSearchChip(text: String) {
    Row(
        modifier = Modifier
            .liquidGlassEffect(cornerRadius = 50.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = LiquidOnSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = LiquidTypography.bodySmall,
            color = LiquidOnSurface
        )
    }
}

@Composable
fun BrowseAllSection() {
    Column {
        Text(
            text = "Browse All",
            style = LiquidTypography.headlineMedium,
            color = LiquidOnSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CategoryCard(
                title = "Pop",
                gradientColor = LiquidPrimary,
                modifier = Modifier.weight(1f)
            )
            CategoryCard(
                title = "Electronic",
                gradientColor = LiquidSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CategoryCard(
                title = "Chill",
                gradientColor = LiquidTertiary,
                modifier = Modifier.weight(1f)
            )
            CategoryCard(
                title = "Rock",
                gradientColor = Color(0xFFD7C1C9),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CategoryCard(title: String, gradientColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        gradientColor.copy(alpha = 0.2f),
                        gradientColor.copy(alpha = 0.05f)
                    )
                )
            )
            .liquidGlassEffect(cornerRadius = 16.dp)
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Box(
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 16.dp, y = 16.dp)
                .rotate(15f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )
    }
}