package com.jagr.fridamusic.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.presentation.theme.LiquidPrimary
import com.jagr.fridamusic.presentation.theme.LiquidSurfaceContainer
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels

@Composable
fun SettingsScreen(paddingValues: PaddingValues, listState: LazyListState, viewModel: LibraryViewModels) {
    val context = LocalContext.current

    // Observamos los estados desde el ViewModel
    val filterVoiceNotes by viewModel.filterVoiceNotes.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val gaplessPlayback by viewModel.gaplessPlayback.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()

    // Diálogo para el Sleep Timer
    var showTimerDialog by remember { mutableStateOf(false) }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Sleep Timer", color = Color.White) },
            text = { Text("Stop audio after:", color = Color.White.copy(alpha = 0.7f)) },
            containerColor = LiquidSurfaceContainer,
            confirmButton = {
                TextButton(onClick = { viewModel.setSleepTimer(15); showTimerDialog = false }) {
                    Text("15 min", color = LiquidPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setSleepTimer(0); showTimerDialog = false }) {
                    Text("Off", color = Color.White)
                }
            }
        )
    }

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
            SettingsSection(title = "ABOUT") {
                AppInfoItem()
            }
        }

        item {
            SettingsSection(title = "PLAYBACK") {
                SettingsNavigationItem(
                    icon = Icons.Default.GraphicEq,
                    title = "Equalizer",
                    value = "System",
                    onClick = {
                        try {
                            viewModel.openSystemEqualizer { intent -> context.startActivity(intent) }
                        } catch (e: Exception) {
                            Toast.makeText(context, "No equalizer found on this device", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsToggleItem(
                    icon = Icons.Default.SkipNext,
                    title = "Gapless Playback",
                    iconTint = Color(0xFFA7C8FF),
                    isChecked = gaplessPlayback,
                    onCheckedChange = { viewModel.updateGapless(it) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsSliderItem(
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    title = "Crossfade",
                    iconTint = Color(0xFFD7CFFF),
                    value = crossfadeDuration,
                    valueRange = 0f..10f,
                    leftLabel = "Off",
                    rightLabel = "10s",
                    onValueChange = { viewModel.updateCrossfade(it) }
                )
            }
        }

        item {
            SettingsSection(title = "LIBRARY & STORAGE") {
                SettingsNavigationItem(
                    icon = Icons.Default.Sync,
                    title = "Scan for new music",
                    value = "Update",
                    onClick = {
                        viewModel.loadSongs()
                        Toast.makeText(context, "Scanning library...", Toast.LENGTH_SHORT).show()
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsToggleItem(
                    icon = Icons.Default.VoiceOverOff,
                    title = "Filter Voice Notes",
                    iconTint = Color.White,
                    isChecked = filterVoiceNotes,
                    onCheckedChange = { viewModel.updateFilterVoiceNotes(it) }
                )
            }
        }

        item {
            SettingsSection(title = "GENERAL") {
                SettingsNavigationItem(
                    icon = Icons.Default.Timer,
                    title = "Sleep Timer",
                    value = if (sleepTimer > 0) "$sleepTimer min" else "Off",
                    onClick = { showTimerDialog = true }
                )
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                SettingsToggleItem(
                    icon = Icons.Default.Lightbulb,
                    title = "Keep Screen On",
                    iconTint = Color(0xFFFFE082),
                    isChecked = keepScreenOn,
                    onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                )
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
fun AppInfoItem() {
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
                .clip(RoundedCornerShape(12.dp))
                .background(LiquidPrimary.copy(alpha = 0.2f))
                .border(1.dp, LiquidPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = "App Icon", tint = LiquidPrimary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Frida Music", style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text(text = "Local Audio Player • v1.0.0", style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
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
    valueRange: ClosedFloatingPointRange<Float>,
    leftLabel: String,
    rightLabel: String,
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
            valueRange = valueRange,
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
            Text(text = leftLabel, style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Text(text = rightLabel, style = LiquidTypography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}