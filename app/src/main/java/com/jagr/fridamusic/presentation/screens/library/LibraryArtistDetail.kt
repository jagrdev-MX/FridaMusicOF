package com.jagr.fridamusic.presentation.screens.library

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaArtworkImage
import com.jagr.fridamusic.presentation.viewmodels.*
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArtistDetailPage(
    artist: LibraryArtist,
    allArtists: List<LibraryArtist>,
    playlists: List<Playlist>,
    isFollowed: Boolean,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit,
    onOpenAlbum: (LibraryAlbum) -> Unit,
    onOpenArtist: (LibraryArtist) -> Unit
) {
    val context = LocalContext.current
    var showActions by rememberSaveable { mutableStateOf(false) }
    var showPlaylistPicker by rememberSaveable { mutableStateOf(false) }
    var selectedArtworkUri by rememberSaveable { mutableStateOf<String?>(null) }
    var sortSongsByName by rememberSaveable { mutableStateOf(false) }
    var sortAlbumsByName by rememberSaveable { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        selectedArtworkUri = uri?.toString()
    }
    val artistAlbums = remember(artist, sortAlbumsByName) {
        artist.songs
            .groupBy { song -> song.album.ifBlank { "${song.albumId}" } }
            .map { (_, albumSongs) ->
                val representative = albumSongs.maxByOrNull { it.dateAdded } ?: albumSongs.first()
                LibraryAlbum(
                    id = representative.albumId,
                    title = representative.album.ifBlank { representative.title },
                    artist = artist.name,
                    representativeSong = representative,
                    newestDateAdded = albumSongs.maxOfOrNull { it.dateAdded } ?: 0L,
                    songCount = albumSongs.size,
                    songs = albumSongs.sortedBy { it.title.lowercase() }
                )
            }
            .let { albums ->
                if (sortAlbumsByName) albums.sortedBy { it.title.lowercase() }
                else albums.sortedByDescending { it.newestDateAdded }
            }
    }
    val leadArtworkUrl by rememberLeadArtworkUrl(
        songs = artist.songs.take(1),
        customCoverUri = null,
        viewModel = viewModel
    )
    val topSongs = remember(artist, sortSongsByName) {
        val songs = if (sortSongsByName) artist.songs.sortedBy { it.title.lowercase() }
        else artist.songs.sortedByDescending { it.dateAdded }
        songs.take(8)
    }
    val singlesAndEps = remember(artistAlbums) {
        artistAlbums.filter { it.songCount <= 4 }.ifEmpty { artistAlbums }.take(8)
    }
    val fromYourLibrary = remember(artist) {
        artist.songs.take(8)
    }
    val fansMightAlsoLike = remember(allArtists, artist.name) {
        allArtists
            .filterNot { it.name == artist.name }
            .sortedByDescending { it.newestDateAdded }
            .take(8)
    }

    DetailPageShell(
        title = artist.name,
        subtitle = stringResource(R.string.artist),
        description = "",
        cover = {
            DetailSongCover(
                song = artist.songs.firstOrNull(),
                viewModel = viewModel,
                shape = CircleShape,
                overrideModel = selectedArtworkUri
            )
        },
        countLabel = pluralStringResource(R.plurals.library_songs_count, artist.songs.size, artist.songs.size),
        backgroundArtUrl = selectedArtworkUri ?: leadArtworkUrl,
        onBack = onBack,
        onMore = { showActions = true },
        onPlay = { playbackViewModel.playSongs(artist.songs) },
        onShuffle = { playbackViewModel.playSongs(artist.songs, shuffle = true) },
        secondaryActions = {
            DetailChipButton(
                icon = if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
                label = if (isFollowed) stringResource(R.string.unfollow) else stringResource(R.string.follow),
                onClick = { viewModel.toggleFollowArtist(artist.name) }
            )
            DetailIconButton(
                icon = Icons.Default.Radio,
                contentDescription = stringResource(R.string.radio),
                onClick = { playbackViewModel.playSongs(artist.songs, shuffle = true) }
            )
            DetailIconButton(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = { shareArtist(context, artist) }
            )
        },
        paddingValues = paddingValues
    ) {
        if (topSongs.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 0) {
                    ArtistSongShelf(
                        title = stringResource(R.string.top_songs),
                        songs = topSongs,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        playlists = playlists
                    )
                }
            }
        }

        if (singlesAndEps.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 1) {
                    ArtistAlbumShelf(
                        title = stringResource(R.string.singles_and_eps),
                        albums = singlesAndEps,
                        viewModel = viewModel,
                        onOpenAlbum = onOpenAlbum
                    )
                }
            }
        }

        if (fromYourLibrary.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 2) {
                    ArtistSongShelf(
                        title = stringResource(R.string.from_your_library),
                        songs = fromYourLibrary,
                        viewModel = viewModel,
                        playbackViewModel = playbackViewModel,
                        playlists = playlists
                    )
                }
            }
        }

        if (fansMightAlsoLike.isNotEmpty()) {
            item {
                AnimatedArtistShelf(order = 3) {
                    RelatedArtistsShelf(
                        artists = fansMightAlsoLike,
                        viewModel = viewModel,
                        onOpenArtist = onOpenArtist
                    )
                }
            }
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ArtistActionsSheet(
                artist = artist,
                isFollowed = isFollowed,
                onDismiss = { showActions = false },
                onPlay = {
                    showActions = false
                    playbackViewModel.playSongs(artist.songs)
                },
                onRadio = {
                    showActions = false
                    playbackViewModel.playSongs(artist.songs, shuffle = true)
                },
                onPlayNext = {
                    showActions = false
                    artist.songs.asReversed().forEach(playbackViewModel::addSongNext)
                },
                onAddToQueue = {
                    showActions = false
                    playbackViewModel.addSongsToQueue(artist.songs)
                },
                onSaveToPlaylist = {
                    showActions = false
                    showPlaylistPicker = true
                },
                onChooseArtwork = {
                    showActions = false
                    artworkPicker.launch(arrayOf("image/*"))
                },
                onSortSongs = {
                    showActions = false
                    sortSongsByName = !sortSongsByName
                },
                onSortAlbums = {
                    showActions = false
                    sortAlbumsByName = !sortAlbumsByName
                },
                onToggleFollow = {
                    showActions = false
                    viewModel.toggleFollowArtist(artist.name)
                },
                onShare = {
                    showActions = false
                    shareArtist(context, artist)
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
                    artist.songs.forEach { song -> viewModel.addSongToPlaylist(playlist, song) }
                    showPlaylistPicker = false
                }
            )
        }
    }
}


@Composable
private fun ArtistAlbumChip(
    album: LibraryAlbum,
    viewModel: LibraryViewModel,
    onClick: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = album.representativeSong) {
        value = viewModel.getSongImageUrl(album.representativeSong)
    }

    Column(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                requestSizePx = 320
            )
        }
        Text(
            text = album.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
private fun AnimatedArtistShelf(
    order: Int,
    content: @Composable () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(order * 70L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 })
    ) {
        content()
    }
}


@Composable
private fun ArtistSongShelf(
    title: String,
    songs: List<Song>,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    playlists: List<Playlist>
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(songs, key = { "artist_shelf_song_${it.id}_$title" }) { song ->
                ArtistSongCard(song = song, viewModel = viewModel, playbackViewModel = playbackViewModel, playlists = playlists)
            }
        }
    }
}


@Composable
private fun ArtistSongCard(
    song: Song,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    playlists: List<Playlist>
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = song) {
        value = viewModel.getSongImageUrl(song)
    }

    Column(
        modifier = Modifier.width(150.dp).clickable { playbackViewModel.playSong(song) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                requestSizePx = 320
            )
        }
        Text(
            text = song.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}


@Composable
private fun ArtistAlbumShelf(
    title: String,
    albums: List<LibraryAlbum>,
    viewModel: LibraryViewModel,
    onOpenAlbum: (LibraryAlbum) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(albums, key = { "artist_shelf_album_${it.id}_${it.title}" }) { album ->
                ArtistAlbumChip(
                    album = album,
                    viewModel = viewModel,
                    onClick = { onOpenAlbum(album) }
                )
            }
        }
    }
}


@Composable
private fun RelatedArtistsShelf(
    artists: List<LibraryArtist>,
    viewModel: LibraryViewModel,
    onOpenArtist: (LibraryArtist) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.fans_might_also_like),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(artists, key = { "related_artist_${it.name}" }) { relatedArtist ->
                RelatedArtistCard(
                    artist = relatedArtist,
                    viewModel = viewModel,
                    onOpen = { onOpenArtist(relatedArtist) }
                )
            }
        }
    }
}


@Composable
private fun RelatedArtistCard(
    artist: LibraryArtist,
    viewModel: LibraryViewModel,
    onOpen: () -> Unit
) {
    val imageUrl by produceState<String?>(initialValue = null, key1 = artist.name) {
        value = artist.songs.firstOrNull()?.let { viewModel.getSongImageUrl(it) }
    }

    Column(
        modifier = Modifier.width(130.dp).clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            FridaArtworkImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                requestSizePx = 288
            )
        }
        Text(
            text = artist.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
private fun ArtistActionsSheet(
    artist: LibraryArtist,
    isFollowed: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onChooseArtwork: () -> Unit,
    onSortSongs: () -> Unit,
    onSortAlbums: () -> Unit,
    onToggleFollow: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = artist.name,
        subtitle = stringResource(R.string.artist),
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.Shuffle, stringResource(R.string.shuffle), onClick = onRadio),
            ActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next), onClick = onPlayNext),
            ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue),
            ActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist), onClick = onSaveToPlaylist),
            ActionSpec(Icons.Default.Image, stringResource(R.string.choose_artwork), onClick = onChooseArtwork),
            ActionSpec(Icons.Default.SortByAlpha, stringResource(R.string.sort_songs), onClick = onSortSongs),
            ActionSpec(Icons.Default.SortByAlpha, stringResource(R.string.sort_albums), onClick = onSortAlbums),
            ActionSpec(
                if (isFollowed) Icons.Default.CheckCircle else Icons.Default.PersonAdd,
                if (isFollowed) stringResource(R.string.unfollow) else stringResource(R.string.follow),
                onClick = onToggleFollow
            ),
            ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare)
        )
    )
}


private fun shareArtist(
    context: Context,
    artist: LibraryArtist
) {
    val text = buildString {
        append(artist.name)
        append("\n")
        append(context.resources.getQuantityString(R.plurals.library_songs_count, artist.songs.size, artist.songs.size))
        artist.songs.take(12).forEachIndexed { index, song ->
            append("\n${index + 1}. ${song.title}")
        }
        if (artist.songs.size > 12) append("\n…")
    }
    shareText(context, artist.name, text)
}