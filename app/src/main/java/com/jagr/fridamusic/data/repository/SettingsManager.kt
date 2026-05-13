package com.jagr.fridamusic.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frida_music_prefs", Context.MODE_PRIVATE)

    var filterVoiceNotes: Boolean
        get() = prefs.getBoolean("filter_voice_notes", false)
        set(value) = prefs.edit().putBoolean("filter_voice_notes", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean("gapless_playback", false)
        set(value) = prefs.edit().putBoolean("gapless_playback", value).apply()

    var crossfadeDuration: Float
        get() = prefs.getFloat("crossfade_duration", 0f)
        set(value) = prefs.edit().putFloat("crossfade_duration", value).apply()

    var saveLastPlayback: Boolean
        get() = prefs.getBoolean("save_last_playback", true)
        set(value) = prefs.edit().putBoolean("save_last_playback", value).apply()

    var lastSongId: Long
        get() = prefs.getLong("last_song_id", -1L)
        set(value) = prefs.edit().putLong("last_song_id", value).apply()

    var lastSongTitle: String?
        get() = prefs.getString("last_song_title", null)
        set(value) = prefs.edit().putString("last_song_title", value).apply()

    var lastSongArtist: String?
        get() = prefs.getString("last_song_artist", null)
        set(value) = prefs.edit().putString("last_song_artist", value).apply()

    var lastSongUri: String?
        get() = prefs.getString("last_song_uri", null)
        set(value) = prefs.edit().putString("last_song_uri", value).apply()

    var lastSongArtwork: String?
        get() = prefs.getString("last_song_artwork", null)
        set(value) = prefs.edit().putString("last_song_artwork", value).apply()

    var lastSongDuration: Long
        get() = prefs.getLong("last_song_duration", 0L)
        set(value) = prefs.edit().putLong("last_song_duration", value).apply()

    var lastPosition: Long
        get() = prefs.getLong("last_position", 0L)
        set(value) = prefs.edit().putLong("last_position", value).apply()

    var searchHistory: String
        get() = prefs.getString("search_history", "") ?: ""
        set(value) = prefs.edit().putString("search_history", value).apply()
}