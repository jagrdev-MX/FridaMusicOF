package com.jagr.fridamusic.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.jagr.fridamusic.data.repository.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SetupStep(val stepNumber: Int) {
    WELCOME(0),
    PERMISSIONS_MEDIA(1),
    PERMISSIONS_NOTIFICATIONS(2),
    BACKUP(3),
    FOLDERS(4),
    THEME(5),
    LIBRARY_LAYOUT(6),
    NAVIGATION_STYLE(7),
    ALARMS(8),
    BATTERY(9),
    FINAL(10)
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _currentStep = MutableStateFlow(SetupStep.WELCOME)
    val currentStep = _currentStep.asStateFlow()

    fun nextStep() {
        val currentIndex = SetupStep.entries.indexOf(_currentStep.value)
        if (currentIndex < SetupStep.entries.size - 1) {
            _currentStep.value = SetupStep.entries[currentIndex + 1]
        } else {
            completeOnboarding()
        }
    }

    fun previousStep() {
        val currentIndex = SetupStep.entries.indexOf(_currentStep.value)
        if (currentIndex > 0) {
            _currentStep.value = SetupStep.entries[currentIndex - 1]
        }
    }

    private fun completeOnboarding() {
        settingsManager.onboardingCompleted = true
    }

    fun skipToStep(step: SetupStep) {
        _currentStep.value = step
    }
}
