package com.jagr.fridamusic.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.AppTheme
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: LibraryViewModels,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val filterVoiceNotes by viewModel.filterVoiceNotes.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val gaplessPlayback by viewModel.gaplessPlayback.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val saveLastPlayback by viewModel.saveLastPlayback.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()

    var showTimerDialog by remember { mutableStateOf(false) }

    var showThemeDialog by remember { mutableStateOf(false) }
    val currentTheme by viewModel.currentTheme.collectAsState()

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text("Sleep Timer", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Stop audio after:", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                TextButton(onClick = { viewModel.setSleepTimer(15); showTimerDialog = false }) {
                    Text("15 min", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setSleepTimer(0); showTimerDialog = false }) {
                    Text("Off", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("App Theme", color = MaterialTheme.colorScheme.onSurface) },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Column {
                    AppTheme.values().forEach { themeOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateTheme(themeOption)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == themeOption,
                                onClick = {
                                    viewModel.updateTheme(themeOption)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = themeOption.displayName, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp,
                bottom = paddingValues.calculateBottomPadding() + 40.dp, // Reducimos el padding bottom ya que no hay navbar
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = LiquidTypography.displayLarge.copy(fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsSection(title = "ABOUT") {
                    AppInfoItem()
                }
            }

            item {
                SettingsSection(title = "APPEARANCE") {
                    SettingsNavigationItem(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        value = currentTheme.displayName,
                        onClick = { showThemeDialog = true }
                    )
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.SkipNext,
                        title = "Gapless Playback",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isChecked = gaplessPlayback,
                        onCheckedChange = { viewModel.updateGapless(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsSliderItem(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        title = "Crossfade",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.VoiceOverOff,
                        title = "Filter Voice Notes",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.Lightbulb,
                        title = "Keep Screen On",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isChecked = keepScreenOn,
                        onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    SettingsToggleItem(
                        icon = Icons.Default.History,
                        title = "Remember Last Played",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isChecked = saveLastPlayback,
                        onCheckedChange = { viewModel.updateSaveLastPlayback(it) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 16.dp)
                .size(52.dp)
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Volver",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = LiquidTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LibraryMusic, contentDescription = "App Icon", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Frida Music", style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            Text(text = "Local Audio Player • v1.0.0", style = LiquidTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(text = title, style = LiquidTypography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
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
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = LiquidTypography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = LiquidTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(text = title, style = LiquidTypography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = leftLabel, style = LiquidTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = rightLabel, style = LiquidTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}