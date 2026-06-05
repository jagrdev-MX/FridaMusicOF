package com.jagr.fridamusic.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import kotlinx.coroutines.*

class MusicService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val ACTION_PLAY_SONG = "ACTION_PLAY_SONG"
    }

    private data class NotificationState(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val position: Long,
        val duration: Long
    )

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var artworkJob: Job? = null
    private var cachedArtworkUrl: String? = null
    private var cachedArtwork: Bitmap? = null
    private var latestNotificationState: NotificationState? = null
    private val fallbackArtwork: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        BitmapFactory.decodeResource(resources, R.drawable.frida_artwork_fallback)
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "FridaMusicSession").apply {
            isActive = true

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (requestAudioFocus()) {
                        sendBroadcast(Intent("ACTION_PLAY_PAUSE"))
                    }
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

    fun playSong(song: Song) {
        if (requestAudioFocus()) {
            val intent = Intent(ACTION_PLAY_SONG).apply {
                putExtra("SONG_DATA", song.data)
            }
            sendBroadcast(intent)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                sendBroadcast(Intent("ACTION_PLAY_PAUSE"))
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                sendBroadcast(Intent("ACTION_PLAY_PAUSE"))
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            }
        }
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
        val albumArtUrl = intent.getStringExtra("ALBUM_ART_URL")?.takeIf { it.isNotBlank() }
        val currentPosition = intent.getLongExtra("CURRENT_POSITION", 0L)
        val duration = intent.getLongExtra("DURATION", 0L)
        val notificationState = NotificationState(title, artist, isPlaying, currentPosition, duration)
        latestNotificationState = notificationState
        val immediateArtwork = cachedArtwork.takeIf { albumArtUrl == cachedArtworkUrl } ?: fallbackArtwork
        publishNotification(notificationState, immediateArtwork, enterForeground = true)

        if (albumArtUrl != cachedArtworkUrl) {
            cachedArtworkUrl = albumArtUrl
            cachedArtwork = null
            artworkJob?.cancel()
            if (albumArtUrl != null) {
                artworkJob = serviceScope.launch {
                    val bitmap = runCatching {
                        val request = ImageRequest.Builder(this@MusicService)
                            .data(albumArtUrl)
                            .size(320)
                            .allowHardware(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                        val result = this@MusicService.imageLoader.execute(request)
                        (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    }.getOrNull() ?: return@launch

                    withContext(Dispatchers.Main.immediate) {
                        if (cachedArtworkUrl != albumArtUrl) return@withContext
                        cachedArtwork = bitmap
                        latestNotificationState?.let { latest ->
                            publishNotification(latest, bitmap, enterForeground = false)
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun publishNotification(state: NotificationState, bitmap: Bitmap, enterForeground: Boolean) {
        val playbackState = if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(playbackState, state.position, if (state.isPlaying) 1f else 0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, state.duration)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .build()
        )
        val notification = buildNotification(state.title, state.artist, state.isPlaying, bitmap)
        if (enterForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        } else {
            getSystemService(NotificationManager::class.java).notify(1, notification)
        }
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean, bitmap: Bitmap): android.app.Notification {
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

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, "FRIDA_MUSIC_CHANNEL")
            .setSmallIcon(R.drawable.ic_frida_notification)
            .setContentTitle(title)
            .setContentText(artist)
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

        return builder.setLargeIcon(bitmap).build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("FRIDA_MUSIC_CHANNEL", "Music Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
