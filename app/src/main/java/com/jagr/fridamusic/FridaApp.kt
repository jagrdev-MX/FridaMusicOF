package com.jagr.fridamusic

import android.app.Application
import com.jagr.fridamusic.data.remote.innertube.NewPipeUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FridaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa el motor de extracción de YouTube
        NewPipeUtils.init(this)
    }
}
