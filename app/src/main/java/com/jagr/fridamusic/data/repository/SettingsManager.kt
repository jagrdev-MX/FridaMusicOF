package com.jagr.fridamusic.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frida_music_prefs", Context.MODE_PRIVATE)

    var appThemePreference: Int
        get() = prefs.getInt("app_theme_pref", 0)
        set(value) = prefs.edit().putInt("app_theme_pref", value).apply()

    var enableBlurEffect: Boolean
        get() = prefs.getBoolean("enable_blur_effect", true)
        set(value) = prefs.edit().putBoolean("enable_blur_effect", value).apply()

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

    var playbackQueueJson: String
        get() = prefs.getString("playback_queue_json", "") ?: ""
        set(value) = prefs.edit().putString("playback_queue_json", value).apply()

    var autoplayEnabled: Boolean
        get() = prefs.getBoolean("autoplay_enabled", false)
        set(value) = prefs.edit().putBoolean("autoplay_enabled", value).apply()

    var shuffleEnabled: Boolean
        get() = prefs.getBoolean("shuffle_enabled", false)
        set(value) = prefs.edit().putBoolean("shuffle_enabled", value).apply()

    var repeatModeName: String
        get() = prefs.getString("repeat_mode_name", "OFF") ?: "OFF"
        set(value) = prefs.edit().putString("repeat_mode_name", value).apply()

    var searchHistory: String
        get() = prefs.getString("search_history", "") ?: ""
        set(value) = prefs.edit().putString("search_history", value).apply()

    var librarySortOption: String
        get() = prefs.getString("library_sort_option", "DATE") ?: "DATE"
        set(value) = prefs.edit().putString("library_sort_option", value).apply()

    var librarySortReversed: Boolean
        get() = prefs.getBoolean("library_sort_reversed", false)
        set(value) = prefs.edit().putBoolean("library_sort_reversed", value).apply()

    var albumGridCount: Int
        get() = prefs.getInt("album_grid_count", 2).coerceIn(1, 4)
        set(value) = prefs.edit().putInt("album_grid_count", value.coerceIn(1, 4)).apply()

    var artistGridCount: Int
        get() = prefs.getInt("artist_grid_count", 3).coerceIn(1, 4)
        set(value) = prefs.edit().putInt("artist_grid_count", value.coerceIn(1, 4)).apply()

    var playlistGridCount: Int
        get() = prefs.getInt("playlist_grid_count", 2).coerceIn(1, 4)
        set(value) = prefs.edit().putInt("playlist_grid_count", value.coerceIn(1, 4)).apply()

    var saveLibrarySort: Boolean
        get() = prefs.getBoolean("save_library_sort", false)
        set(value) = prefs.edit().putBoolean("save_library_sort", value).apply()

    var blacklistedSongIds: Set<String>
        get() = prefs.getStringSet("blacklisted_song_ids", emptySet())?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet("blacklisted_song_ids", value).apply()

    fun localLyrics(id: Any): String? =
        prefs.getString("local_lyrics_$id", null)

    fun setLocalLyrics(id: Any, lyrics: String?) {
        val editor = prefs.edit()
        if (lyrics.isNullOrBlank()) {
            editor.remove("local_lyrics_$id")
        } else {
            editor.putString("local_lyrics_$id", lyrics)
        }
        editor.apply()
    }

    fun cachedLyrics(key: String): String? =
        prefs.getString("cached_lyrics_$key", null)

    fun setCachedLyrics(key: String, lyrics: String) {
        prefs.edit().putString("cached_lyrics_$key", lyrics).apply()
    }

    fun playlistCoverUri(playlistId: Long): String? =
        prefs.getString("playlist_cover_$playlistId", null)

    fun setPlaylistCoverUri(playlistId: Long, uri: String?) {
        val editor = prefs.edit()
        if (uri.isNullOrBlank()) {
            editor.remove("playlist_cover_$playlistId")
        } else {
            editor.putString("playlist_cover_$playlistId", uri)
        }
        editor.apply()
    }

    fun playlistSongMetadata(songId: Long): String? =
        prefs.getString("playlist_song_$songId", null)

    fun setPlaylistSongMetadata(songId: Long, metadata: String?) {
        val editor = prefs.edit()
        if (metadata.isNullOrBlank()) {
            editor.remove("playlist_song_$songId")
        } else {
            editor.putString("playlist_song_$songId", metadata)
        }
        editor.apply()
    }

    var followedArtists: Set<String>
        get() = prefs.getStringSet("followed_artists", emptySet())?.toSet().orEmpty()
        set(value) = prefs.edit().putStringSet("followed_artists", value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    var useFloatingNavBar: Boolean
        get() = prefs.getBoolean("use_floating_nav_bar", true)
        set(value) = prefs.edit().putBoolean("use_floating_nav_bar", value).apply()

    var excludedFolderUris: Set<String>
        get() = prefs.getStringSet("excluded_folder_uris", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("excluded_folder_uris", value).apply()

    fun clearLastPlayback() {
        prefs.edit().apply {
            remove("last_song_id")
            remove("last_song_title")
            remove("last_song_artist")
            remove("last_song_uri")
            remove("last_song_artwork")
            remove("last_song_duration")
            remove("last_position")
            remove("playback_queue_json")
        }.apply()
    }
}