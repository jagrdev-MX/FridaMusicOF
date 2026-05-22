package com.jagr.fridamusic.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.jagr.fridamusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

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
                override fun onSeekTo(pos: Long) {
                    val intent = Intent("ACTION_SEEK").apply {
                        putExtra("SEEK_POSITION", pos)
                    }
                    sendBroadcast(intent)
                }
            })
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == "STOP_SERVICE") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent.getStringExtra("TITLE") ?: "Unknown"
        val artist = intent.getStringExtra("ARTIST") ?: "Unknown"
        val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
        val albumArtUrl = intent.getStringExtra("ALBUM_ART_URL")
        val currentPosition = intent.getLongExtra("CURRENT_POSITION", 0L)
        val duration = intent.getLongExtra("DURATION", 0L)
        val repeatMode = intent.getStringExtra("REPEAT_MODE") ?: "OFF"
        val isLiked = intent.getBooleanExtra("IS_LIKED", false)

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val repeatIcon = repeatIconFor(repeatMode)
        val likeIcon = if (isLiked) R.drawable.ic_notification_favorite else R.drawable.ic_notification_favorite_border
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, currentPosition, if (isPlaying) 1f else 0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO
                )
                .addCustomAction("ACTION_REPEAT", getString(R.string.repeat), repeatIcon)
                .addCustomAction("ACTION_LIKE", getString(if (isLiked) R.string.unlike else R.string.like), likeIcon)
                .build()
        )

        val fallbackBitmap = BitmapFactory.decodeResource(resources, R.drawable.frida_cover_fallback)
        val initialNotification = buildNotification(title, artist, isPlaying, fallbackBitmap, repeatMode, isLiked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, initialNotification)
        }

        serviceScope.launch {
            var bitmap: Bitmap? = fallbackBitmap
            if (!albumArtUrl.isNullOrBlank()) {
                try {
                    val request = ImageRequest.Builder(this@MusicService)
                        .data(albumArtUrl)
                        .placeholder(R.drawable.frida_cover_fallback)
                        .error(R.drawable.frida_cover_fallback)
                        .fallback(R.drawable.frida_cover_fallback)
                        .size(600)
                        .allowHardware(false)
                        .build()
                    val result = this@MusicService.imageLoader.execute(request)
                    bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: fallbackBitmap
                } catch (e: Exception) {
                    bitmap = fallbackBitmap
                }
            }

            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    .build()
            )

            val notification = buildNotification(title, artist, isPlaying, bitmap, repeatMode, isLiked)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(1, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        bitmap: Bitmap?,
        repeatMode: String,
        isLiked: Boolean
    ): android.app.Notification {
        val openAppIntent = Intent(this, Class.forName("$packageName.MainActivity")).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 99, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getBroadcast(
            this, 0, Intent("ACTION_PLAY_PAUSE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getBroadcast(
            this, 1, Intent("ACTION_PREV"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            this, 2, Intent("ACTION_NEXT"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val repeatIntent = PendingIntent.getBroadcast(
            this, 3, Intent("ACTION_REPEAT"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val likeIntent = PendingIntent.getBroadcast(
            this, 4, Intent("ACTION_LIKE"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = getString(if (isPlaying) R.string.pause else R.string.play)
        val repeatIcon = repeatIconFor(repeatMode)
        val repeatLabel = when (repeatMode) {
            "ONE" -> getString(R.string.repeat_one)
            "ALL" -> getString(R.string.repeat_all)
            else -> getString(R.string.repeat_off)
        }
        val likeIcon = if (isLiked) R.drawable.ic_notification_favorite else R.drawable.ic_notification_favorite_border
        val likeLabel = getString(if (isLiked) R.string.unlike else R.string.like)

        val builder = NotificationCompat.Builder(this, "FRIDA_MUSIC_CHANNEL")
            .setSmallIcon(R.drawable.ic_frida_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .addAction(repeatIcon, repeatLabel, repeatIntent)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.previous), prevIntent)
            .addAction(playPauseIcon, playPauseLabel, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.next), nextIntent)
            .addAction(likeIcon, likeLabel, likeIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)

        builder.setLargeIcon(bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.frida_cover_fallback))

        return builder.build()
    }

    private fun repeatIconFor(repeatMode: String): Int =
        if (repeatMode == "ONE") R.drawable.ic_notification_repeat_one else R.drawable.ic_notification_repeat

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("FRIDA_MUSIC_CHANNEL", "Music Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
