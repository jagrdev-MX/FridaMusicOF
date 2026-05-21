package com.jagr.fridamusic.data.remote.innertube

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DeezerApi {
    fun search(query: String): List<YouTubeResult> {
        try {
            val builtUri = Uri.parse("https://api.deezer.com/search")
                .buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("limit", "12")
                .build()

            val url = URL(builtUri.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            if (connection.responseCode != 200) {
                Log.e("DeezerApi", "Error de red: ${connection.responseCode}")
                return emptyList()
            }

            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)

            val tracks = json.getJSONArray("data")
            val results = mutableListOf<YouTubeResult>()

            for (i in 0 until tracks.length()) {
                val track = tracks.getJSONObject(i)

                val id = track.getLong("id").toString()
                val title = track.getString("title")
                val artistName = track.getJSONObject("artist").getString("name")

                val album = track.getJSONObject("album")
                val albumId = album.optLong("id", 0L).toString()
                val albumTitle = album.optString("title", "")
                val imageUrl = album.optString("cover_xl", album.optString("cover_big", ""))

                results.add(YouTubeResult(id, title, artistName, imageUrl, ResultType.SONG))
                if (albumId != "0" && albumTitle.isNotBlank()) {
                    results.add(YouTubeResult("deezer-album-$albumId", albumTitle, artistName, imageUrl, ResultType.ALBUM))
                }
            }
            return results
        } catch (e: Exception) {
            Log.e("DeezerApi", "Error fatal parseando Deezer: ${e.message}")
            return emptyList()
        }
    }
}
