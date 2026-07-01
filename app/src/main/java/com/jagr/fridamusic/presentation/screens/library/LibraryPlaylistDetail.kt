package com.jagr.fridamusic.presentation.screens.library

import android.content.Intent
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.QueueSource
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.viewmodels.*


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailPage(
    playlist: Playlist,
    songs: List<Song>,
    playlists: List<Playlist>,
    customCoverUri: String?,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var songSortName by rememberSaveable(playlist.id) {
        mutableStateOf(PlaylistSongSortOption.DEFAULT.name)
    }
    val songSortOption = remember(songSortName) {
        runCatching { PlaylistSongSortOption.valueOf(songSortName) }
            .getOrDefault(PlaylistSongSortOption.DEFAULT)
    }
    val displayedSongs = remember(songs, songSortOption) {
        songs.sortedPlaylistSongs(songSortOption)
    }
    val leadArtworkUrl by rememberLeadArtworkUrl(
        songs = displayedSongs,
        customCoverUri = customCoverUri,
        viewModel = viewModel
    )
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setPlaylistCoverUri(playlist.id, uri.toString())
        }
    }

    DetailPageShell(
        title = playlist.name,
        subtitle = stringResource(R.string.playlist_label),
        description = playlist.description.orEmpty(),
        cover = {
            SmartCollectionCover(
                songs = displayedSongs,
                customCoverUri = customCoverUri,
                viewModel = viewModel,
                shape = RoundedCornerShape(24.dp),
                fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic
            )
        },
        countLabel = pluralStringResource(R.plurals.library_songs_count, displayedSongs.size, displayedSongs.size),
        backgroundArtUrl = leadArtworkUrl,
        onBack = onBack,
        onMore = { showActions = true },
        onPlay = { playbackViewModel.playSongs(displayedSongs) },
        onShuffle = { playbackViewModel.playSongs(displayedSongs, shuffle = true) },
        secondaryActions = {
            DetailChipButton(
                icon = Icons.Default.Radio,
                label = stringResource(R.string.radio),
                onClick = { playbackViewModel.playSongs(displayedSongs, shuffle = true) }
            )
            DetailIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = { sharePlaylist(context, playlist, displayedSongs) }
            )
        },
        paddingValues = paddingValues
    ) {
        if (displayedSongs.isEmpty()) {
            item {
                EmptyLibraryState(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    title = stringResource(R.string.no_local_songs)
                )
            }
        } else {
            item {
                PlaylistSongSortSelector(
                    selected = songSortOption,
                    onSelected = { option -> songSortName = option.name }
                )
            }
            itemsIndexed(displayedSongs, key = { _, song -> "playlist_detail_${playlist.id}_${song.id}" }) { index, song ->
                LibrarySongItem(
                    song = song,
                    viewModel = viewModel,
                    playbackViewModel = playbackViewModel,
                    playlists = playlists,
                    onClick = {
                        playbackViewModel.playSong(
                            song = song,
                            queue = displayedSongs,
                            source = QueueSource.PLAYLIST,
                            sourceName = playlist.name
                        )
                    },
                    onPlayAction = {
                        playbackViewModel.playSong(
                            song = song,
                            queue = displayedSongs,
                            source = QueueSource.PLAYLIST,
                            sourceName = playlist.name
                        )
                    },
                    playlist = playlist,
                    canMoveUp = songSortOption == PlaylistSongSortOption.CUSTOM && index > 0,
                    canMoveDown = songSortOption == PlaylistSongSortOption.CUSTOM && index < displayedSongs.lastIndex,
                    onRemoveFromPlaylist = {
                        viewModel.removeSongFromPlaylist(playlist, song.id)
                    },
                    onMoveUp = {
                        viewModel.moveSongInPlaylist(playlist, song.id, -1)
                    },
                    onMoveDown = {
                        viewModel.moveSongInPlaylist(playlist, song.id, 1)
                    }
                )
            }
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PlaylistActionsSheet(
                playlist = playlist,
                onDismiss = { showActions = false },
                onEdit = {
                    showActions = false
                    showEditSheet = true
                },
                onPlay = {
                    showActions = false
                    playbackViewModel.playSongs(displayedSongs)
                },
                onShuffle = {
                    showActions = false
                    playbackViewModel.playSongs(displayedSongs, shuffle = true)
                },
                onAddToQueue = {
                    showActions = false
                    playbackViewModel.addPlaylistToQueue(playlist, displayedSongs)
                },
                onShare = {
                    showActions = false
                    sharePlaylist(context, playlist, displayedSongs)
                },
                onChangeCover = {
                    showActions = false
                    coverPicker.launch(arrayOf("image/*"))
                },
                onRemoveCover = if (customCoverUri != null) {
                    {
                        showActions = false
                        viewModel.setPlaylistCoverUri(playlist.id, null)
                    }
                } else {
                    null
                },
                onDelete = {
                    showActions = false
                    viewModel.deletePlaylist(playlist)
                    onBack()
                }
            )
        }
    }

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EditPlaylistSheet(
                playlist = playlist,
                onDismiss = { showEditSheet = false },
                onSave = { name, description ->
                    viewModel.updatePlaylistDetails(playlist, name, description)
                    showEditSheet = false
                }
            )
        }
    }
}

@Composable
private fun PlaylistSongSortSelector(
    selected: PlaylistSongSortOption,
    onSelected: (PlaylistSongSortOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.playlist_song_order),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlaylistSongSortOption.entries, key = { it.name }) { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            when (option) {
                                PlaylistSongSortOption.DEFAULT -> stringResource(R.string.default_order)
                                PlaylistSongSortOption.DATE -> stringResource(R.string.date)
                                PlaylistSongSortOption.ARTIST -> stringResource(R.string.artists_tab)
                                PlaylistSongSortOption.TITLE -> stringResource(R.string.title_label)
                                PlaylistSongSortOption.CUSTOM -> stringResource(R.string.custom_order)
                            },
                            color = if (selected == option) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (option) {
                                PlaylistSongSortOption.DEFAULT -> Icons.Default.Refresh
                                PlaylistSongSortOption.DATE -> Icons.Default.History
                                PlaylistSongSortOption.ARTIST -> Icons.Default.Person
                                PlaylistSongSortOption.TITLE -> Icons.Default.SortByAlpha
                                PlaylistSongSortOption.CUSTOM -> Icons.Default.Edit
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}


@Composable
fun CreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var playlistName by rememberSaveable { mutableStateOf("") }
    var playlistDescription by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.create_playlist_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.playlist_name)) },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
            },
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = playlistDescription,
            onValueChange = { playlistDescription = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.playlist_description)) },
            leadingIcon = {
                Icon(Icons.Default.MusicNote, contentDescription = null)
            },
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                onCreate(
                    playlistName.trim(),
                    playlistDescription.trim().takeIf { it.isNotBlank() }
                )
            },
            enabled = playlistName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.create), fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}


@Composable
fun EditPlaylistSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var playlistName by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    var playlistDescription by rememberSaveable(playlist.id) { mutableStateOf(playlist.description.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.edit_playlist),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(22.dp))

        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.playlist_name)) },
            leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null)
            },
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = playlistDescription,
            onValueChange = { playlistDescription = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.playlist_description)) },
            leadingIcon = {
                Icon(Icons.Default.MusicNote, contentDescription = null)
            },
            shape = RoundedCornerShape(20.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                onSave(
                    playlistName.trim(),
                    playlistDescription.trim().takeIf { it.isNotBlank() }
                )
            },
            enabled = playlistName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.save_changes), fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}


@Composable
fun PlaylistActionsSheet(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onChangeCover: (() -> Unit)?,
    onRemoveCover: (() -> Unit)?,
    onDelete: () -> Unit
) {
    ActionSheetFrame(
        title = playlist.name,
        subtitle = stringResource(R.string.playlist_label),
        onDismiss = onDismiss,
        actions = buildList {
            if (onEdit != null) {
                add(ActionSpec(Icons.Default.Edit, stringResource(R.string.edit_playlist), onClick = onEdit))
            }
            add(ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay))
            add(ActionSpec(Icons.Default.Shuffle, stringResource(R.string.shuffle), onClick = onShuffle))
            add(ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue))
            add(ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare))
            if (onChangeCover != null) {
                add(ActionSpec(Icons.Default.Image, stringResource(R.string.change_cover), onClick = onChangeCover))
            }
            if (onRemoveCover != null) {
                add(ActionSpec(Icons.Default.Delete, stringResource(R.string.remove_cover), onClick = onRemoveCover))
            }
            add(ActionSpec(Icons.Default.Delete, stringResource(R.string.delete_playlist), destructive = true, onClick = onDelete))
        }
    )
}


fun sharePlaylist(
    context: Context,
    playlist: Playlist,
    songs: List<Song>
) {
    val text = buildString {
        append(playlist.name)
        append("\n")
        append(context.resources.getQuantityString(R.plurals.library_songs_count, songs.size, songs.size))
        if (!playlist.description.isNullOrBlank()) {
            append("\n")
            append(playlist.description)
        }
        songs.take(12).forEachIndexed { index, song ->
            append("\n${index + 1}. ${song.title}")
            if (song.artist.isNotBlank()) append(" — ${song.artist}")
        }
        if (songs.size > 12) append("\n…")
    }
    shareText(context, playlist.name, text)
}