package com.jagr.fridamusic.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.presentation.theme.LiquidPrimary
import com.jagr.fridamusic.presentation.theme.LiquidTypography

@Composable
fun SettingsScreen(paddingValues: PaddingValues, listState: LazyListState) {
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
        item {
            Text(
                text = "Settings",
                style = LiquidTypography.displayLarge.copy(fontSize = 28.sp),
                color = LiquidPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            SettingsSection(title = "ACCOUNT") {
                AccountItem()
            }
        }

        item {
            SettingsSection(title = "AUDIO EXPERIENCE") {
                var eqEnabled by remember { mutableStateOf(true) }
                var losslessEnabled by remember { mutableStateOf(false) }
                var spatialFocus by remember { mutableFloatStateOf(70f) }

                SettingsToggleItem(
                    icon = Icons.Default.GraphicEq,
                    title = "Equalizer",
                    iconTint = LiquidPrimary,
                    isChecked = eqEnabled,
                    onCheckedChange = { eqEnabled = it }
                )
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsToggleItem(
                    icon = Icons.Default.HighQuality,
                    title = "Lossless Audio",
                    iconTint = Color(0xFFA7C8FF),
                    isChecked = losslessEnabled,
                    onCheckedChange = { losslessEnabled = it }
                )
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsSliderItem(
                    icon = Icons.Default.SpatialAudio,
                    title = "Spatial Focus",
                    iconTint = Color(0xFFD7CFFF),
                    value = spatialFocus,
                    onValueChange = { spatialFocus = it }
                )
            }
        }

        item {
            SettingsSection(title = "GENERAL") {
                var notificationsEnabled by remember { mutableStateOf(true) }

                SettingsNavigationItem(
                    icon = Icons.Default.Timer,
                    title = "Sleep Timer",
                    value = "Off",
                    onClick = {  }
                )
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsToggleItem(
                    icon = Icons.Default.Notifications,
                    title = "Push Notifications",
                    iconTint = Color.White,
                    isChecked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable {  }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = Color(0xFFFFB4AB))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Out",
                        style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFFFB4AB)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = LiquidTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
            content = content
        )
    }
}

@Composable
fun AccountItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {  }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Dev", style = LiquidTypography.bodyLarge, color = Color.White)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = LiquidTypography.bodyLarge, color = Color.White)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LiquidPrimary,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = LiquidTypography.bodyLarge, color = Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    iconTint: Color,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = LiquidTypography.bodyLarge, color = Color.White)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.8f),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Wide", style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Text(text = "Narrow", style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}