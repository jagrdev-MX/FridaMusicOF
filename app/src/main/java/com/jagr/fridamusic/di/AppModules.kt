package com.jagr.fridamusic.di

import android.content.Context
import com.jagr.fridamusic.data.local.MusicDatabase
import com.jagr.fridamusic.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModules {

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager {
        return SettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return MusicDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun providePlaybackHistoryRepository(db: MusicDatabase): PlaybackHistoryRepository {
        return PlaybackHistoryRepository(db.playbackHistoryDao())
    }

    @Provides
    @Singleton
    fun provideAudioRepository(@ApplicationContext context: Context): AudioRepository {
        return AudioRepository(context)
    }

    @Provides
    @Singleton
    fun provideYouTubeRepository(@ApplicationContext context: Context): YouTubeRepository {
        return YouTubeRepository(context)
    }

    @Provides
    @Singleton
    fun provideArtworkRepository(@ApplicationContext context: Context): ArtworkRepository {
        return ArtworkRepository(context)
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(@ApplicationContext context: Context): LyricsRepository {
        return LyricsRepository(context)
    }

    @Provides
    @Singleton
    fun provideAutoplayRepository(@ApplicationContext context: Context): AutoplayRepository {
        return AutoplayRepository(context)
    }
}
