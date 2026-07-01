package com.jagr.fridamusic.presentation.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jagr.fridamusic.R
import com.jagr.fridamusic.domain.model.Playlist
import com.jagr.fridamusic.domain.model.Song
import com.jagr.fridamusic.presentation.components.FridaEmptyState
import com.jagr.fridamusic.presentation.viewmodels.*

@Composable
fun SmartSongsDetailPage(
    title: String,
    songs: List<Song>,
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    viewModel: LibraryViewModel,
    playbackViewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            bottom = paddingValues.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = { playbackViewModel.playSongs(songs, shuffle = true) },
                    enabled = songs.isNotEmpty()
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle))
                }
            }
        }
        item {
            Text(
                pluralStringResource(R.plurals.library_songs_count, songs.size, songs.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
        if (songs.isEmpty()) {
            item {
                FridaEmptyState(
                    title = stringResource(R.string.nothing_played_yet),
                    subtitle = stringResource(R.string.play_songs_to_build_history)
                )
            }
        } else {
            items(songs, key = { "smart_${it.id}" }) { song ->
                LibrarySongItem(
                    song = song,
                    viewModel = viewModel,
                    playbackViewModel = playbackViewModel,
                    playlists = playlists,
                    onClick = { playbackViewModel.playSong(song, songs) }
                )
            }
        }
    }
}