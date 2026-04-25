package com.jagr.fridamusic.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frida_settings", Context.MODE_PRIVATE)

    var filterVoiceNotes: Boolean
        get() = prefs.getBoolean("filter_voice", true)
        set(value) = prefs.edit().putBoolean("filter_voice", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean("gapless", true)
        set(value) = prefs.edit().putBoolean("gapless", value).apply()

    var crossfadeDuration: Float
        get() = prefs.getFloat("crossfade", 2f)
        set(value) = prefs.edit().putFloat("crossfade", value).apply()
}