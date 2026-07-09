package com.jagr.fridamusic.presentation.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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


@Composable
internal fun ActionSheetFrame(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    actions: List<ActionSpec>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(18.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            actions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowActions.forEach { action ->
                        ActionTile(
                            icon = action.icon,
                            label = action.label,
                            onClick = action.onClick,
                            destructive = action.destructive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowActions.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}


@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
fun SongActionsSheet(
    song: Song,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenAlbum: (() -> Unit)? = null,
    onOpenArtist: (() -> Unit)? = null,
    onOpenFolder: (() -> Unit)? = null,
    onTagEditor: (() -> Unit)? = null,
    onEditLyrics: (() -> Unit)? = null,
    onBlacklist: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = song.title,
        subtitle = stringResource(R.string.track),
        onDismiss = onDismiss,
        actions = buildList {
            add(ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay))
            add(ActionSpec(Icons.Default.SkipNext, stringResource(R.string.play_next), onClick = onPlayNext))
            add(ActionSpec(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.add_to_queue), onClick = onAddToQueue))
            add(ActionSpec(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.save_to_playlist), onClick = onSaveToPlaylist))
            if (onOpenAlbum != null) add(ActionSpec(Icons.Default.Album, stringResource(R.string.go_to_album), onClick = onOpenAlbum))
            if (onOpenArtist != null) add(ActionSpec(Icons.Default.Person, stringResource(R.string.go_to_artist), onClick = onOpenArtist))
            if (onOpenFolder != null) add(ActionSpec(Icons.Default.Folder, stringResource(R.string.go_to_folder), onClick = onOpenFolder))
            if (onTagEditor != null) add(ActionSpec(Icons.Default.Edit, stringResource(R.string.tag_editor), onClick = onTagEditor))
            if (onEditLyrics != null) add(ActionSpec(Icons.Default.Lyrics, stringResource(R.string.edit_lyrics), onClick = onEditLyrics))
            if (onBlacklist != null) add(ActionSpec(Icons.Default.Block, stringResource(R.string.blacklist), onClick = onBlacklist))
            if (onDetails != null) add(ActionSpec(Icons.Default.Info, stringResource(R.string.details), onClick = onDetails))
            if (onMoveUp != null) {
                add(ActionSpec(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up), onClick = onMoveUp))
            }
            if (onMoveDown != null) {
                add(ActionSpec(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down), onClick = onMoveDown))
            }
            if (onRemoveFromPlaylist != null) {
                add(ActionSpec(Icons.Default.Delete, stringResource(R.string.remove_from_playlist), destructive = true, onClick = onRemoveFromPlaylist))
            }
            add(
                ActionSpec(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isLiked) stringResource(R.string.unlike) else stringResource(R.string.like),
                    onClick = onToggleLike
                )
            )
            add(ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare))
            if (onDelete != null) {
                add(ActionSpec(Icons.Default.Delete, stringResource(R.string.delete_from_device), destructive = true, onClick = onDelete))
            }
        }
    )
}


@Composable
fun HistoryActionsSheet(
    item: PlaybackHistoryEntity,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    ActionSheetFrame(
        title = item.title,
        subtitle = stringResource(R.string.history_tab),
        onDismiss = onDismiss,
        actions = listOf(
            ActionSpec(Icons.Default.PlayArrow, stringResource(R.string.play), onClick = onPlay),
            ActionSpec(Icons.Default.Share, stringResource(R.string.share), onClick = onShare)
        )
    )
}


@Composable
fun SaveToPlaylistSheet(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
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
                text = stringResource(R.string.save_to_playlist),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            EmptyLibraryState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = stringResource(R.string.no_playlists)
            )
        } else {
            playlists.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(playlist) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = pluralStringResource(R.plurals.library_songs_count, playlist.songIds.size, playlist.songIds.size),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridCountSheet(
    selected: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.grid_count),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            (1..4).forEach { count ->
                FilterChip(
                    selected = selected == count,
                    onClick = { onSelected(count) },
                    label = { Text(count.toString(), color = if (selected == count) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
internal fun SortAndFilterSheet(
    tab: LibraryTab,
    selectedSortName: String,
    reversed: Boolean,
    saveSort: Boolean,
    onSortSelected: (LibrarySortOption) -> Unit,
    onReversedChange: (Boolean) -> Unit,
    onSaveSortChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
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
            TextButton(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reset))
            }
            Text(
                text = stringResource(R.string.sort_and_filter),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(72.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.reversed),
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(checked = reversed, onCheckedChange = onReversedChange)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.sort),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sortOptionsFor(tab)) { option ->
                FilterChip(
                    selected = selectedSortName == option.name,
                    onClick = { onSortSelected(option) },
                    label = {
                        Text(
                            when (option) {
                                LibrarySortOption.TITLE -> stringResource(R.string.title_label)
                                LibrarySortOption.DATE -> stringResource(R.string.date)
                                LibrarySortOption.ARTIST -> stringResource(R.string.artists_tab)
                                LibrarySortOption.ALBUM -> stringResource(R.string.album_label)
                                LibrarySortOption.DURATION -> stringResource(R.string.duration_label)
                                LibrarySortOption.SONG_COUNT -> stringResource(R.string.sort_song_count)
                                LibrarySortOption.ALBUM_COUNT -> stringResource(R.string.sort_album_count)
                            },
                            color = if (selectedSortName == option.name) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        if (option == LibrarySortOption.TITLE) {
                            Icon(Icons.Default.SortByAlpha, contentDescription = null)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.save), color = MaterialTheme.colorScheme.onSurface)
            Checkbox(checked = saveSort, onCheckedChange = onSaveSortChange)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(stringResource(R.string.apply), fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}