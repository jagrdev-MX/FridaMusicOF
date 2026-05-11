package com.jagr.fridamusic

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jagr.fridamusic.data.ads.AdManager
import com.jagr.fridamusic.data.ads.GoogleMobileAdsConsentManager
import com.jagr.fridamusic.presentation.screens.MainScreen
import com.jagr.fridamusic.presentation.theme.FridaMusicTheme
import com.jagr.fridamusic.presentation.viewmodels.AppTheme
import com.jagr.fridamusic.presentation.viewmodels.LibraryViewModels
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModels by viewModels()
    private lateinit var adManager: AdManager
    private lateinit var consentManager: GoogleMobileAdsConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adManager = AdManager.getInstance(applicationContext)
        consentManager = GoogleMobileAdsConsentManager(applicationContext)

        consentManager.requestConsent(this) {
            if (consentManager.canRequestAds) {
                adManager.initialize()
            }
        }

        setContent {
            val currentTheme by libraryViewModel.currentTheme.collectAsState()

            val isDarkTheme = when (currentTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            FridaMusicTheme(darkTheme = isDarkTheme) {
                MainScreen()
            }
        }
    }
}