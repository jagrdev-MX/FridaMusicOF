package com.jagr.fridamusic.domain.model

/**
 * [Playlist] represents a user-created collection of songs.
 */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
