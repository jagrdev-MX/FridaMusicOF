package com.jagr.fridamusic

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil.imageLoader
import com.jagr.fridamusic.data.ads.AdManager
import com.jagr.fridamusic.data.ads.GoogleMobileAdsConsentManager
import com.jagr.fridamusic.presentation.screens.MainScreen
import com.jagr.fridamusic.presentation.theme.FridaMusicTheme
import com.jagr.fridamusic.domain.model.AppTheme
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModel
import com.jagr.fridamusic.presentation.viewmodels.PlaybackViewModel
import com.jagr.fridamusic.presentation.viewmodels.SearchViewModel
import com.jagr.fridamusic.presentation.viewmodels.SettingsViewModel
import com.jagr.fridamusic.presentation.viewmodels.QueueViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private lateinit var adManager: AdManager
    private lateinit var consentManager: GoogleMobileAdsConsentManager

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            libraryViewModel.clearCaches()
            imageLoader.memoryCache?.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adManager = AdManager.getInstance(applicationContext)
        consentManager = GoogleMobileAdsConsentManager(applicationContext)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        setContent {
            val currentTheme by settingsViewModel.currentTheme.collectAsState()

            val isDarkTheme = when (currentTheme) {
                com.jagr.fridamusic.domain.model.AppTheme.SYSTEM -> isSystemInDarkTheme()
                com.jagr.fridamusic.domain.model.AppTheme.LIGHT -> false
                com.jagr.fridamusic.domain.model.AppTheme.DARK -> true
            }

            com.jagr.fridamusic.presentation.theme.FridaMusicTheme(darkTheme = isDarkTheme) {
                MainScreen()
            }
        }

        consentManager.requestConsent(this) {
            if (consentManager.canRequestAds) {
                adManager.initialize()
            }
        }
    }
}
