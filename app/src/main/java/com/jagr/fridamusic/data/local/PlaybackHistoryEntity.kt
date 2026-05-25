package com.jagr.fridamusic.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["title", "artist"]),
        Index(value = ["playedAt"]),
        Index(value = ["playCount", "playedAt"])
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,

    val playedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)
