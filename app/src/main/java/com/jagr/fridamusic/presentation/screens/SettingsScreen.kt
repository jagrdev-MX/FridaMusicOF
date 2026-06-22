package com.jagr.fridamusic.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import com.jagr.fridamusic.R
import com.jagr.fridamusic.presentation.theme.LiquidTypography
import com.jagr.fridamusic.presentation.viewmodels.PlaybackViewModel
import com.jagr.fridamusic.presentation.viewmodels.SettingsViewModel
import com.jagr.fridamusic.domain.model.AppTheme
import kotlin.math.roundToInt

val betaTestersList = listOf(
    "Vicente Contreras",
    "Tester Beta",
    "Dev Colaborador",
    "Usuario Gamma"
)

@Composable
fun SettingsScreen(
    paddingValues: PaddingValues,
    listState: LazyListState,
    viewModel: SettingsViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val filterVoiceNotes by viewModel.filterVoiceNotes.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val gaplessPlayback by viewModel.gaplessPlayback.collectAsState()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsState()
    val saveLastPlayback by viewModel.saveLastPlayback.collectAsState()
    val sleepTimer by playbackViewModel.sleepTimerState.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()

    val enableBlurEffect by viewModel.enableBlurEffect.collectAsState()
    var showTimerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    if (showTimerDialog) {
        SleepTimerDialog(
            activeMinutes = sleepTimer.minutes,
            activeEndOfSong = sleepTimer.endOfSong,
            onDismiss = { showTimerDialog = false },
            onSetTimer = { minutes, endOfSong -> playbackViewModel.setSleepTimer(minutes, endOfSong) },
            onCancelTimer = { playbackViewModel.cancelSleepTimer() }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onDismiss = { showThemeDialog = false },
            onSelectTheme = { viewModel.updateTheme(it) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 80.dp,
                bottom = paddingValues.calculateBottomPadding() + 40.dp,
                start = 20.dp,
                end = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings),
                    style = LiquidTypography.displayLarge.copy(fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                SettingsSection(title = stringResource(R.string.about)) {
                    AppInfoItem()
                }
            }

            item {
                SettingsSection(title = "Agradecimientos Especiales") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(betaTestersList) { testerName ->
                            BetaTesterItem(testerName)
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.appearance)) {
                    SettingsNavigationItem(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.theme),
                        value = currentTheme.displayName,
                        onClick = { showThemeDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.BlurOn,
                        title = "Efecto Blur",
                        isChecked = enableBlurEffect,
                        onCheckedChange = { viewModel.updateEnableBlurEffect(it) }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.playback)) {
                    SettingsNavigationItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.equalizer),
                        value = stringResource(R.string.system),
                        onClick = {
                            try {
                                viewModel.openSystemEqualizer { intent -> context.startActivity(intent) }
                            } catch (e: Exception) {
                                val msg = context.getString(R.string.no_equalizer_found)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.SkipNext,
                        title = stringResource(R.string.gapless_playback),
                        isChecked = gaplessPlayback,
                        onCheckedChange = { viewModel.updateGapless(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsSliderItem(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        title = stringResource(R.string.crossfade),
                        value = crossfadeDuration,
                        valueRange = 0f..10f,
                        leftLabel = stringResource(R.string.off_label),
                        rightLabel = "10s",
                        onValueChange = { viewModel.updateCrossfade(it) }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.library_storage)) {
                    SettingsNavigationItem(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.scan_new_music),
                        value = stringResource(R.string.update),
                        onClick = {
                            viewModel.loadSongs()
                            val msg = context.getString(R.string.scanning_library)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.VoiceOverOff,
                        title = stringResource(R.string.filter_voice_notes),
                        isChecked = filterVoiceNotes,
                        onCheckedChange = { viewModel.updateFilterVoiceNotes(it) }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.general)) {
                    SettingsNavigationItem(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.sleep_timer),
                        value = if (sleepTimer.minutes > 0) stringResource(R.string.min_format, sleepTimer.minutes) else stringResource(R.string.off_label),
                        onClick = { showTimerDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), thickness = 1.dp)
                    SettingsToggleItem(
                        icon = Icons.Default.Lightbulb,
                        title = stringResource(R.string.keep_screen_on),
                        isChecked = keepScreenOn,
                        onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                    )
                    SettingsToggleItem(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.remember_last_played),
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
private fun BetaTesterItem(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = LiquidTypography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SleepTimerDialog(
    activeMinutes: Int,
    activeEndOfSong: Boolean,
    onDismiss: () -> Unit,
    onSetTimer: (Int, Boolean) -> Unit,
    onCancelTimer: () -> Unit
) {
    var minutes by remember { mutableFloatStateOf(activeMinutes.takeIf { it > 0 }?.toFloat() ?: 5f) }
    var endOfSong by remember { mutableStateOf(activeEndOfSong) }
    val roundedMinutes = ((minutes / 5f).roundToInt() * 5).coerceIn(5, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.stop_audio_after), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.min_format, roundedMinutes),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = LiquidTypography.displayLarge.copy(fontSize = 36.sp)
                )
                Slider(
                    value = minutes,
                    onValueChange = { minutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 120).toFloat() },
                    valueRange = 5f..120f,
                    steps = 22,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { endOfSong = !endOfSong }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = endOfSong, onCheckedChange = { endOfSong = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.end_of_song), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(onClick = { onSetTimer(roundedMinutes, endOfSong); onDismiss() }) {
                Text(stringResource(R.string.start), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancelTimer(); onDismiss() }) {
                Text(stringResource(R.string.off_label), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onSelectTheme: (AppTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_theme), color = MaterialTheme.colorScheme.onSurface) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                AppTheme.entries.forEach { themeOption ->
                    val themeName = when(themeOption) {
                        AppTheme.SYSTEM -> stringResource(R.string.theme_system)
                        AppTheme.LIGHT -> stringResource(R.string.theme_light)
                        AppTheme.DARK -> stringResource(R.string.theme_dark)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTheme(themeOption)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == themeOption,
                            onClick = {
                                onSelectTheme(themeOption)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = themeName, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun AppInfoItem() {
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
            Text(text = stringResource(R.string.app_name), style = LiquidTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
            Text(text = stringResource(R.string.local_audio_player) + " • v2.0.0", style = LiquidTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
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
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
private fun SettingsNavigationItem(
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
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
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
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
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
