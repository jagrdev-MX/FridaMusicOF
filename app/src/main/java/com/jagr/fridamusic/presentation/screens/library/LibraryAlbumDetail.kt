package com.jagr.fridamusic.presentation.screens.library

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.presentation.viewmodels.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumDetailPage(
    album: LibraryAlbum,
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit,
    onOpenArtist: (() -> Unit)?
) {
    val context = LocalContext.current
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var showTagEditor by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.loadSongs()
    }
    val leadArtworkUrl by rememberLeadArtworkUrl(
        songs = listOf(album.representativeSong),
        customCoverUri = null,
        viewModel = viewModel
    )

    DetailPageShell(
        title = album.title,
        subtitle = album.artist,
        description = "",
        cover = {
            DetailSongCover(
                song = album.representativeSong,
                viewModel = viewModel,
                shape = RoundedCornerShape(24.dp)
            )
        },
        countLabel = pluralStringResource(R.plurals.library_songs_count, album.songCount, album.songCount),
        backgroundArtUrl = leadArtworkUrl,
        onBack = onBack,
        onMore = { showActions = true },
        onPlay = { playbackViewModel.playSongs(album.songs) },
        onShuffle = { playbackViewModel.playSongs(album.songs, shuffle = true) },
        secondaryActions = {
            DetailChipButton(
                icon = Icons.Default.Radio,
                label = stringResource(R.string.radio),
                onClick = { playbackViewModel.playSongs(album.songs, shuffle = true) }
            )
            DetailIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = { shareAlbum(context, album) }
            )
        },
        paddingValues = paddingValues
    ) {
        items(album.songs, key = { "album_detail_${it.id}" }) { song ->
            LibrarySongItem(
                song = song,
                viewModel = viewModel,
                playbackViewModel = playbackViewModel,
                playlists = playlists,
                onClick = { playbackViewModel.playSong(song) }
            )
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CollectionActionsSheet(
                title = album.title,
                subtitle = album.artist,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    playbackViewModel.playSongs(album.songs)
                },
                onShuffle = {
                    showActions = false
                    playbackViewModel.playSongs(album.songs, shuffle = true)
                },
                onAddToQueue = {
                    showActions = false
                    playbackViewModel.addSongsToQueue(album.songs)
                },
                onPlayNext = {
                    showActions = false
                    album.songs.asReversed().forEach(playbackViewModel::addSongNext)
                },
                onSaveToPlaylist = {
                    showActions = false
                    showPlaylistPicker = true
                },
                onGoToArtist = onOpenArtist?.let { openArtist ->
                    {
                        showActions = false
                        openArtist()
                    }
                },
                onTagEditor = {
                    showActions = false
                    showTagEditor = true
                },
                onShare = {
                    showActions = false
                    shareAlbum(context, album)
                },
                onDelete = {
                    showActions = false
                    confirmDelete = true
                }
            )
        }
    }

    if (showPlaylistPicker) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SaveToPlaylistSheet(
                playlists = playlists,
                onDismiss = { showPlaylistPicker = false },
                onSelect = { playlist ->
                    album.songs.forEach { song -> viewModel.addSongToPlaylist(playlist, song) }
                    showPlaylistPicker = false
                }
            )
        }
    }

    if (showTagEditor) {
        AlertDialog(
            onDismissRequest = { showTagEditor = false },
            title = { Text(stringResource(R.string.tag_editor)) },
            text = { Text(stringResource(R.string.tag_editor_safe_note)) },
            confirmButton = {
                TextButton(onClick = { showTagEditor = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_from_device)) },
            text = { Text(stringResource(R.string.delete_album_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    requestSongsDeletion(context, album.songs, deleteLauncher, viewModel)
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CollectionActionsSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onSaveToPlaylist: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onTagEditor: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    ActionSheetFrame(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        actions = buildList {
            add(ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay))
            add(ActionSpec(Icons.Default.Shuffle, stringResource(R.string.shuffle), onClick = onShuffle))
            if (onPlayNext != null) add(ActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next), onClick = onPlayNext))
            add(ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue))
            if (onSaveToPlaylist != null) add(ActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist), onClick = onSaveToPlaylist))
            if (onGoToArtist != null) add(ActionSpec(Icons.Default.Person, stringResource(R.string.go_to_artist), onClick = onGoToArtist))
            if (onTagEditor != null) add(ActionSpec(Icons.Default.Edit, stringResource(R.string.tag_editor), onClick = onTagEditor))
            add(ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare))
            if (onDelete != null) add(ActionSpec(Icons.Default.Delete, stringResource(R.string.delete_from_device), destructive = true, onClick = onDelete))
        }
    )
}


private fun shareAlbum(
    context: Context,
    album: LibraryAlbum
) {
    val text = buildString {
        append(album.title)
        if (album.artist.isNotBlank()) append(" — ${album.artist}")
        append("\n")
        append(context.resources.getQuantityString(R.plurals.library_songs_count, album.songCount, album.songCount))
        album.songs.take(12).forEachIndexed { index, song ->
            append("\n${index + 1}. ${song.title}")
        }
        if (album.songs.size > 12) append("\n…")
    }
    shareText(context, album.title, text)
}