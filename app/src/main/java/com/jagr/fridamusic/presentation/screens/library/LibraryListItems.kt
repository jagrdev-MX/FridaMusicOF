package com.jagr.fridamusic.presentation.screens.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.screens.hasLocalAudioToShare
import com.jagr.fridamusic.presentation.viewmodels.*
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongItem(
    song: Song,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    playlists: List<Playlist>,
    onClick: () -> Unit,
    onPlayAction: (() -> Unit)? = null,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    playlist: Playlist? = null,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }
    val isLiked = remember(playlists, song.id) {
        playlists.any { it.name == "Me gusta" && song.id in it.songIds }
    }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var showLyricsEditor by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showTagEditor by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var lyricsDraft by remember { mutableStateOf(viewModel.localLyrics(song)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.loadSongs()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                requestSizePx = 128
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (song.isExplicit) {
                    RestrictionBadge(text = "E")
                }
                Text(
                    text = buildString {
                        append(song.artist.ifBlank { context.getString(R.string.unknown) })
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = { showActions = true }) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SongActionsSheet(
                song = song,
                isLiked = isLiked,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    onPlayAction?.invoke() ?: playbackViewModel.playSong(song)
                },
                onPlayNext = {
                    showActions = false
                    playbackViewModel.addSongNext(song)
                },
                onAddToQueue = {
                    showActions = false
                    playbackViewModel.addSongToQueue(song)
                },
                onSaveToPlaylist = {
                    showActions = false
                    showPlaylistPicker = true
                },
                onToggleLike = {
                    showActions = false
                    viewModel.toggleLike(song)
                },
                onOpenAlbum = onOpenAlbum?.let {
                    { showActions = false; it() }
                },
                onOpenArtist = onOpenArtist?.let {
                    { showActions = false; it() }
                },
                onOpenFolder = {
                    showActions = false
                    copySongFolderPath(context, song)
                },
                onTagEditor = {
                    showActions = false
                    showTagEditor = true
                },
                onEditLyrics = {
                    showActions = false
                    lyricsDraft = viewModel.localLyrics(song)
                    showLyricsEditor = true
                },
                onBlacklist = {
                    showActions = false
                    viewModel.toggleBlacklist(song)
                },
                onDetails = {
                    showActions = false
                    showDetails = true
                },
                onDelete = {
                    showActions = false
                    confirmDelete = true
                },
                onRemoveFromPlaylist = onRemoveFromPlaylist?.let {
                    {
                        showActions = false
                        it()
                    }
                },
                onMoveUp = if (playlist != null && canMoveUp && onMoveUp != null) {
                    {
                        showActions = false
                        onMoveUp()
                    }
                } else {
                    null
                },
                onMoveDown = if (playlist != null && canMoveDown && onMoveDown != null) {
                    {
                        showActions = false
                        onMoveDown()
                    }
                } else {
                    null
                },
                onShare = {
                    showActions = false
                    scope.launch {
                        shareSong(
                            context = context,
                            song = song,
                            fallbackUrl = if (song.hasLocalAudioToShare()) null else viewModel.resolveShareUrl(song)
                        )
                    }
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
                    viewModel.addSongToPlaylist(playlist, song)
                    showPlaylistPicker = false
                }
            )
        }
    }

    if (showLyricsEditor) {
        AlertDialog(
            onDismissRequest = { showLyricsEditor = false },
            title = { Text(stringResource(R.string.edit_lyrics)) },
            text = {
                OutlinedTextField(
                    value = lyricsDraft ?: "",
                    onValueChange = { lyricsDraft = it },
                    label = { Text(stringResource(R.string.lyrics)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveLocalLyrics(song, lyricsDraft ?: "")
                    showLyricsEditor = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showLyricsEditor = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDetails || showTagEditor) {
        AlertDialog(
            onDismissRequest = { showDetails = false; showTagEditor = false },
            title = {
                Text(if (showTagEditor) stringResource(R.string.tag_editor) else stringResource(R.string.details))
            },
            text = {
                Text(
                    buildString {
                        append(song.title).append("\n")
                        append(song.artist).append("\n")
                        append(song.album.ifBlank { context.getString(R.string.unknown_album) }).append("\n")
                        append(formatDuration(song.duration))
                        if (showTagEditor) {
                            append("\n\n").append(context.getString(R.string.tag_editor_safe_note))
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false; showTagEditor = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_from_device)) },
            text = { Text(stringResource(R.string.delete_song_message, song.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    requestSongDeletion(context, song, deleteLauncher, viewModel)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySongItem(
    item: PlaybackHistoryEntity,
    songs: List<Song>,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    playlists: List<Playlist>,
    onClick: () -> Unit
) {
    val linkedSong = remember(item, songs) {
        songs.firstOrNull { song ->
            song.uri.toString() == item.songId ||
                    (song.title == item.title && song.artist == item.artist)
        }
    }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            FridaArtworkImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                requestSizePx = 128
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (linkedSong?.isExplicit == true) {
                    RestrictionBadge(text = "E")
                }
                Text(
                    text = item.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = { showActions = true }) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showActions && linkedSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SongActionsSheet(
                song = linkedSong,
                isLiked = playlists.any { it.name == "Me gusta" && linkedSong.id in it.songIds },
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    playbackViewModel.playSong(linkedSong)
                },
                onPlayNext = {
                    showActions = false
                    playbackViewModel.addSongNext(linkedSong)
                },
                onAddToQueue = {
                    showActions = false
                    playbackViewModel.addSongToQueue(linkedSong)
                },
                onSaveToPlaylist = {
                    showActions = false
                    showPlaylistPicker = true
                },
                onToggleLike = {
                    showActions = false
                    viewModel.toggleLike(linkedSong)
                },
                onShare = {
                    showActions = false
                    scope.launch {
                        shareSong(
                            context = context,
                            song = linkedSong,
                            fallbackUrl = if (linkedSong.hasLocalAudioToShare()) null else viewModel.resolveShareUrl(linkedSong)
                        )
                    }
                }
            )
        }
    } else if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            HistoryActionsSheet(
                item = item,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    playbackViewModel.playHistoryItem(item, songs)
                },
                onShare = {
                    showActions = false
                    if (linkedSong != null) {
                        scope.launch {
                            shareSong(
                                context = context,
                                song = linkedSong,
                                fallbackUrl = if (linkedSong.hasLocalAudioToShare()) null else viewModel.resolveShareUrl(linkedSong)
                            )
                        }
                    } else {
                        scope.launch {
                            shareHistoryItem(
                                context = context,
                                item = item,
                                fallbackUrl = viewModel.resolveShareUrl(item.title, item.artist)
                            )
                        }
                    }
                }
            )
        }
    }

    if (showPlaylistPicker && linkedSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SaveToPlaylistSheet(
                playlists = playlists,
                onDismiss = { showPlaylistPicker = false },
                onSelect = { playlist ->
                    viewModel.addSongToPlaylist(playlist, linkedSong)
                    showPlaylistPicker = false
                }
            )
        }
    }
}


@Composable
private fun ArtistListItem(
    artist: LibraryArtist,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    onPlay: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = artist.name) {
        value = artist.songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                requestSizePx = 128
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.library_songs_count, artist.songs.size, artist.songs.size),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddToQueue: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartCollectionThumbnail(
            songs = songs,
            customCoverUri = customCoverUri,
            viewModel = viewModel,
            fallbackIcon = Icons.AutoMirrored.Filled.QueueMusic
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.library_songs_count, playlist.songIds.size, playlist.songIds.size),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { showActions = true }) {
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onPlay()
                },
                onShuffle = {
                    showActions = false
                    onShuffle()
                },
                onAddToQueue = {
                    showActions = false
                    onAddToQueue()
                },
                onShare = {
                    showActions = false
                    sharePlaylist(context, playlist, songs)
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
                    onDelete()
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
private fun LibraryAlbumCard(
    album: LibraryAlbum,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = album.representativeSong) {
        value = viewModel.getSongImageUrl(album.representativeSong)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = album.title,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                requestSizePx = 320
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f))
                        )
                    )
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = album.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = album.artist,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ArtistGridCard(
    artist: LibraryArtist,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artwork by produceState<String?>(initialValue = null, key1 = artist.name) {
        value = viewModel.getArtistImageUrl(artist.name)
            ?: artist.songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FridaArtworkImage(
            model = artwork,
            contentDescription = artist.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = CircleShape,
            contentScale = ContentScale.Crop,
            requestSizePx = 320
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Text(
            pluralStringResource(R.plurals.library_songs_count, artist.songs.size, artist.songs.size),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistGridCard(
    playlist: Playlist,
    songs: List<Song>,
    customCoverUri: String?,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActions by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val artwork by produceState<String?>(initialValue = customCoverUri, key1 = customCoverUri, key2 = songs) {
        value = customCoverUri ?: songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
    ) {
        FridaArtworkImage(
            model = artwork,
            contentDescription = playlist.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            requestSizePx = 320
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(R.plurals.library_songs_count, playlist.songIds.size, playlist.songIds.size),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            IconButton(
                onClick = { showActions = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PlaylistActionsSheet(
                playlist = playlist,
                onDismiss = { showActions = false },
                onPlay = { playbackViewModel.playPlaylist(playlist, viewModel.songsForPlaylist(playlist)) },
                onShuffle = { playbackViewModel.playPlaylist(playlist, viewModel.songsForPlaylist(playlist), shuffle = true) },
                onAddToQueue = { playbackViewModel.addPlaylistToQueue(playlist, viewModel.songsForPlaylist(playlist)) },
                onShare = { showActions = false; sharePlaylist(context, playlist, songs) },
                onChangeCover = null,
                onRemoveCover = null,
                onDelete = { showActions = false; onDelete() }
            )
        }
    }
}


@Composable
fun LibraryCollectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    viewModel: LibraryViewModel,
    coverSongs: List<Song>,
    customCoverUri: String? = null,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartCollectionThumbnail(
            songs = coverSongs,
            customCoverUri = customCoverUri,
            viewModel = viewModel,
            fallbackIcon = icon
        )

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onPlay) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onOpen()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shuffle)) },
                    leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onShuffle()
                    }
                )
            }
        }
    }
}

@Composable
private fun RestrictionBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
