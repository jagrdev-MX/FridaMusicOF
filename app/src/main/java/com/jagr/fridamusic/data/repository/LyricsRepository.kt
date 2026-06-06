package com.jagr.fridamusic.data.repository

import android.content.Context
import com.jagr.fridamusic.data.repository.SettingsManager
import com.jagr.fridamusic.domain.model.Song
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class LyricsRepository(private val context: Context) {
    private val settingsManager = SettingsManager(context)

    suspend fun getLyrics(song: Song): String? {
        val local = settingsManager.localLyrics(song.id)
        if (local != null) return local
        
        return fetchLyrics(song.artist, song.title)
    }

    private suspend fun fetchLyrics(artist: String, title: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val query = URLEncoder.encode("$artist $title lyrics", "UTF-8")
            val url = "https://lrclib.net/api/search?q=$query"
            val activeConnection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
            }
            connection = activeConnection
            if (activeConnection.responseCode != 200) return null
            
            val response = activeConnection.inputStream.bufferedReader().use { it.readText() }
            val results = org.json.JSONArray(response)
            if (results.length() == 0) return null
            
            val bestMatch = results.getJSONObject(0)
            bestMatch.optString("syncedLyrics").takeIf { it.isNotBlank() }
                ?: bestMatch.optString("plainLyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
