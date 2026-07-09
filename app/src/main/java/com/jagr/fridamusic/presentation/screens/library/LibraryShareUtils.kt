package com.jagr.fridamusic.presentation.screens.library

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.screens.shareSongAudioOrLink
import com.jagr.fridamusic.presentation.viewmodels.*


fun shareSong(
    context: Context,
    song: Song,
    fallbackUrl: String?
) {
    shareSongAudioOrLink(context, song, fallbackUrl)
}


fun shareHistoryItem(
    context: Context,
    item: PlaybackHistoryEntity,
    fallbackUrl: String?
) {
    val text = buildString {
        append("${item.title} — ${item.artist}")
        if (!fallbackUrl.isNullOrBlank()) append("\n$fallbackUrl")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, item.title)
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, fallbackUrl ?: text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share))
    )
}


fun shareText(
    context: Context,
    title: String,
    text: String
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share))
    )
}


fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}


fun copySongFolderPath(context: Context, song: Song) {
    val path = song.data.ifBlank { song.uri.toString() }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.song_path), path))
    Toast.makeText(context, R.string.path_copied, Toast.LENGTH_SHORT).show()
}


fun requestSongDeletion(
    context: Context,
    song: Song,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
    viewModel: LibraryViewModel
) {
    requestSongsDeletion(context, listOf(song), launcher, viewModel)
}


fun requestSongsDeletion(
    context: Context,
    songs: List<Song>,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
    viewModel: LibraryViewModel
) {
    val contentUris = songs.map { it.uri }.filter { it.scheme.equals("content", ignoreCase = true) }
    if (contentUris.isEmpty()) {
        Toast.makeText(context, R.string.delete_not_available, Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, contentUris)
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        return
    }
    runCatching {
        contentUris.forEach { uri -> context.contentResolver.delete(uri, null, null) }
        viewModel.loadSongs()
    }.onFailure {
        Toast.makeText(context, R.string.delete_permission_required, Toast.LENGTH_LONG).show()
    }
}