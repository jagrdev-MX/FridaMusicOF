package com.jagr.fridamusic.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "FridaMusicSession").apply {
            isActive = true

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    sendBroadcast(Intent("ACTION_PLAY_PAUSE"))
                }
                override fun onPause() {
                    sendBroadcast(Intent("ACTION_PLAY_PAUSE"))
                }
                override fun onSkipToNext() {
                    sendBroadcast(Intent("ACTION_NEXT"))
                }
                override fun onSkipToPrevious() {
                    sendBroadcast(Intent("ACTION_PREV"))
                }
            })
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra("TITLE") ?: "Unknown"
        val artist = intent?.getStringExtra("ARTIST") ?: "Unknown"
        val isPlaying = intent?.getBooleanExtra("IS_PLAYING", false) ?: false
        val albumArtUrl = intent?.getStringExtra("ALBUM_ART_URL")
        val currentPosition = intent?.getLongExtra("CURRENT_POSITION", 0L) ?: 0L

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, currentPosition, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )

        serviceScope.launch {
            var bitmap: Bitmap? = null
            if (albumArtUrl != null) {
                val request = ImageRequest.Builder(this@MusicService)
                    .data(albumArtUrl)
                    .size(600)
                    .build()
                val result = this@MusicService.imageLoader.execute(request)
                bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }

            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(resources, android.R.drawable.ic_media_play)
            }

            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    .build()
            )

            val openAppIntent = Intent(this@MusicService, Class.forName("$packageName.MainActivity")).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                this@MusicService, 99, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIntent = PendingIntent.getBroadcast(
                this@MusicService, 0, Intent("ACTION_PLAY_PAUSE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevIntent = PendingIntent.getBroadcast(
                this@MusicService, 1, Intent("ACTION_PREV"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIntent = PendingIntent.getBroadcast(
                this@MusicService, 2, Intent("ACTION_NEXT"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

            val notification = NotificationCompat.Builder(this@MusicService, "FRIDA_MUSIC_CHANNEL")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(bitmap)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
                .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .build()

            startForeground(1, notification)
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("FRIDA_MUSIC_CHANNEL", "Music Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}