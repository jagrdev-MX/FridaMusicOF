package com.jagr.fridamusic.data.local

import android.net.Uri
import com.jagr.fridamusic.domain.model.PlaybackQueueState
import com.jagr.fridamusic.domain.model.QueueItem
import com.jagr.fridamusic.domain.model.QueueSource
import com.jagr.fridamusic.domain.model.Song
import org.json.JSONArray
import org.json.JSONObject

object QueueStateSerializer {

    fun encode(state: PlaybackQueueState): String {
        return JSONObject().apply {
            put("source", state.source.name)
            put("sourceName", state.sourceName)
            put("current", state.current?.toJson())
            put("previous", JSONArray().apply { state.previous.forEach { put(it.toJson()) } })
            put("upNext", JSONArray().apply { state.upNext.forEach { put(it.toJson()) } })
            put("autoplay", JSONArray().apply { state.autoplay.forEach { put(it.toJson()) } })
        }.toString()
    }

    fun decode(json: String): PlaybackQueueState? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            PlaybackQueueState(
                source = runCatching { QueueSource.valueOf(root.getString("source")) }.getOrDefault(QueueSource.RESTORED),
                sourceName = root.optString("sourceName"),
                current = root.optJSONObject("current")?.toQueueItem(),
                previous = root.optJSONArray("previous").toQueueItems(),
                upNext = root.optJSONArray("upNext").toQueueItems(),
                autoplay = root.optJSONArray("autoplay").toQueueItems()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun QueueItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("song", song.toJson())
            put("source", source.name)
            put("reason", reason)
            put("userInserted", userInserted)
        }
    }

    private fun Song.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("artist", artist)
            put("data", data)
            put("duration", duration)
            put("albumId", albumId)
            put("uri", uri.toString())
            put("artworkUri", artworkUri.toString())
            put("album", album)
            put("dateAdded", dateAdded)
            put("lyrics", lyrics)
            put("isExplicit", isExplicit)
        }
    }

    private fun JSONObject.toQueueItem(): QueueItem? {
        val songObj = optJSONObject("song") ?: return null
        return QueueItem(
            song = songObj.toSong(),
            source = runCatching { QueueSource.valueOf(optString("source")) }.getOrDefault(QueueSource.RESTORED),
            reason = optString("reason").takeIf { it.isNotBlank() },
            userInserted = optBoolean("userInserted", false)
        )
    }

    private fun JSONObject.toSong(): Song =
        Song(
            id = optLong("id"),
            title = optString("title"),
            artist = optString("artist"),
            data = optString("data"),
            duration = optLong("duration", 0L),
            albumId = optLong("albumId", 0L),
            uri = optString("uri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
            artworkUri = optString("artworkUri").takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Uri.EMPTY,
            album = optString("album"),
            dateAdded = optLong("dateAdded", 0L),
            lyrics = optString("lyrics").takeIf { it.isNotBlank() && it != "null" },
            isExplicit = optBoolean("isExplicit", false)
        )

    private fun JSONArray?.toQueueItems(): List<QueueItem> {
        if (this == null) return emptyList()
        val list = mutableListOf<QueueItem>()
        for (i in 0 until length()) {
            optJSONObject(i)?.toQueueItem()?.let { list.add(it) }
        }
        return list
    }
}