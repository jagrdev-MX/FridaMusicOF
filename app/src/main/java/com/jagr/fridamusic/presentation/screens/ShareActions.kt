package com.jagr.fridamusic.presentation.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Song
import java.io.File

internal fun Song.hasLocalAudioToShare(): Boolean =
    uri.scheme.equals("content", ignoreCase = true) ||
        uri.scheme.equals("file", ignoreCase = true)

internal fun shareSongAudioOrLink(
    context: Context,
    song: Song,
    fallbackUrl: String?,
    chooserTitle: String = context.getString(R.string.share)
) {
    val streamUri = song.shareStreamUri(context)
    val text = buildString {
        append(song.title)
        if (song.artist.isNotBlank()) append(" - ").append(song.artist)
        if (song.album.isNotBlank()) append("\n").append(song.album)
        if (!fallbackUrl.isNullOrBlank()) append("\n").append(fallbackUrl)
    }
    val intent = if (streamUri != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = runCatching { context.contentResolver.getType(streamUri) }
                .getOrNull()
                ?.takeIf { it.startsWith("audio/") }
                ?: "audio/*"
            clipData = ClipData.newUri(context.contentResolver, song.title, streamUri)
            putExtra(Intent.EXTRA_STREAM, streamUri)
            putExtra(Intent.EXTRA_TITLE, song.title)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, song.title)
            putExtra(Intent.EXTRA_SUBJECT, song.title)
            putExtra(Intent.EXTRA_TEXT, fallbackUrl ?: text)
        }
    }
    Log.i("FridaShare", "open type=${if (streamUri != null) "audio" else "link"} song=${song.title}")
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun Song.shareStreamUri(context: Context): Uri? =
    when {
        uri.scheme.equals("content", ignoreCase = true) -> uri
        uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let { path ->
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(path)
                )
            }.getOrNull()
        }
        else -> null
    }
