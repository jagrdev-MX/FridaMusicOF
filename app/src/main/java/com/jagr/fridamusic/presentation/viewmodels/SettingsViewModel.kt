package com.jagr.fridamusic.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jagr.fridamusic.data.repository.SettingsManager
import com.jagr.fridamusic.domain.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsManager: SettingsManager
) : AndroidViewModel(application) {

    val filterVoiceNotes = MutableStateFlow(settingsManager.filterVoiceNotes)
    val keepScreenOn = MutableStateFlow(settingsManager.keepScreenOn)
    val gaplessPlayback = MutableStateFlow(settingsManager.gaplessPlayback)
    val crossfadeDuration = MutableStateFlow(settingsManager.crossfadeDuration)
    val saveLastPlayback = MutableStateFlow(settingsManager.saveLastPlayback)
    val enableBlurEffect = MutableStateFlow(settingsManager.enableBlurEffect)
    val isAutoPlayEnabled = MutableStateFlow(settingsManager.autoplayEnabled)
    val onboardingCompleted = MutableStateFlow(settingsManager.onboardingCompleted)
    val useFloatingNavBar = MutableStateFlow(settingsManager.useFloatingNavBar)
    val excludedFolderUris = MutableStateFlow(settingsManager.excludedFolderUris)

    private val _currentTheme = MutableStateFlow(
        AppTheme.entries.toTypedArray().getOrNull(settingsManager.appThemePreference) ?: AppTheme.SYSTEM
    )
    val currentTheme = _currentTheme.asStateFlow()

    fun updateTheme(theme: AppTheme) {
        _currentTheme.value = theme
        settingsManager.appThemePreference = theme.ordinal
    }

    fun updateEnableBlurEffect(enabled: Boolean) {
        enableBlurEffect.value = enabled
        settingsManager.enableBlurEffect = enabled
    }

    fun updateFilterVoiceNotes(enabled: Boolean) {
        filterVoiceNotes.value = enabled
        settingsManager.filterVoiceNotes = enabled
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        keepScreenOn.value = enabled
        settingsManager.keepScreenOn = enabled
    }

    fun updateGapless(enabled: Boolean) {
        gaplessPlayback.value = enabled
        settingsManager.gaplessPlayback = enabled
    }

    fun updateCrossfade(duration: Float) {
        crossfadeDuration.value = duration
        settingsManager.crossfadeDuration = duration
    }

    fun updateSaveLastPlayback(enabled: Boolean) {
        saveLastPlayback.value = enabled
        settingsManager.saveLastPlayback = enabled
        if (!enabled) settingsManager.clearLastPlayback()
    }

    fun toggleAutoplay(enabled: Boolean) {
        isAutoPlayEnabled.value = enabled
        settingsManager.autoplayEnabled = enabled
    }

    fun completeOnboarding() {
        onboardingCompleted.value = true
        settingsManager.onboardingCompleted = true
    }

    fun updateNavBarStyle(isFloating: Boolean) {
        useFloatingNavBar.value = isFloating
        settingsManager.useFloatingNavBar = isFloating
    }

    fun addExcludedFolder(uri: String) {
        val current = excludedFolderUris.value.toMutableSet()
        if (current.add(uri)) {
            excludedFolderUris.value = current
            settingsManager.excludedFolderUris = current
        }
    }

    fun removeExcludedFolder(uri: String) {
        val current = excludedFolderUris.value.toMutableSet()
        if (current.remove(uri)) {
            excludedFolderUris.value = current
            settingsManager.excludedFolderUris = current
        }
    }

    fun openSystemEqualizer(onIntent: (android.content.Intent) -> Unit) {
        val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, "com.jagr.fridamusic")
            putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0)
            putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
        }
        onIntent(intent)
    }

    fun loadSongs() {

    }
}
