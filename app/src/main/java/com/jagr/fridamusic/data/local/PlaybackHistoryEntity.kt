package com.jagr.fridamusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,

    val playedAt: Long = System.currentTimeMillis()
)