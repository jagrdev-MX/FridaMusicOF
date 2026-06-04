package com.jagr.fridamusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jagr.fridamusic.data.remote.innertube.NewPipeUtils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FridaApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Inicializa el motor de extracción de YouTube
        NewPipeUtils.init(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Reducido del 20% al 15%
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .allowHardware(true) // Bitmaps en memoria de hardware para ahorrar RAM
            .crossfade(true)
            .build()
}
