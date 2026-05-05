package com.jagr.fridamusic

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jagr.fridamusic.data.ads.AdManager
import com.jagr.fridamusic.data.ads.GoogleMobileAdsConsentManager
import com.jagr.fridamusic.presentation.screens.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            MainScreen()
        }
    }
}
