package com.jagr.fridamusic.presentation.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.cache.getCacheDataSourceFactory
import com.jagr.fridamusic.data.repository.SettingsManager

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var settingsManager: SettingsManager
    private val servicePlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistServicePlaybackState()
        }
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification_logo)
            }
        setMediaNotificationProvider(notificationProvider)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val cacheFactory = getCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheFactory)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { it.addListener(servicePlayerListener) }

        val intent = Intent(this, Class.forName("$packageName.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        persistServicePlaybackState()
        mediaSession?.run {
            player.removeListener(servicePlayerListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        persistServicePlaybackState()
        player?.pause()
        player?.stop()
        stopSelf()
    }

    private fun persistServicePlaybackState() {
        if (!::settingsManager.isInitialized) return
        if (!settingsManager.saveLastPlayback) {
            settingsManager.clearLastPlayback()
            return
        }
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        val metadata = item.mediaMetadata
        settingsManager.lastSongId = item.mediaId.toLongOrNull() ?: item.mediaId.hashCode().toLong()
        settingsManager.lastSongTitle = metadata.title?.toString()
        settingsManager.lastSongArtist = metadata.artist?.toString()
        settingsManager.lastSongUri = item.localConfiguration?.uri?.toString()
        settingsManager.lastSongArtwork = metadata.artworkUri?.toString()
        settingsManager.lastSongDuration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
        settingsManager.lastPosition = player.currentPosition.coerceAtLeast(0L)
    }
}
