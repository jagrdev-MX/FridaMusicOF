package com.jagr.fridamusic.domain.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val data: String,
    val duration: Long,
    val albumId: Long,
    val uri: Uri,
    val artworkUri: Uri
)