package com.jagr.fridamusic.domain.model

import androidx.compose.runtime.Immutable

/**
 * [Playlist] represents a user-created collection of songs.
 */
@Immutable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val songIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
