package com.jagr.fridamusic.data.repository

import com.jagr.fridamusic.data.local.PlaybackHistoryDao
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryRepositoryTest {

    @Test
    fun addToHistory_incrementsCountForRepeatedTrack() = runBlocking {
        val dao = FakePlaybackHistoryDao()
        val repository = PlaybackHistoryRepository(dao)

        repository.addToHistory(history(songId = "content://one", playedAt = 100L))
        repository.addToHistory(history(songId = "content://one", playedAt = 200L))

        val stored = dao.getAllHistory().single()
        assertEquals(2, stored.playCount)
        assertEquals(200L, stored.playedAt)
    }

    @Test
    fun getMostPlayed_ordersByCountThenLatestPlayback() = runBlocking {
        val dao = FakePlaybackHistoryDao()
        val repository = PlaybackHistoryRepository(dao)

        repository.addToHistory(history(songId = "content://one", playedAt = 100L))
        repository.addToHistory(history(songId = "content://two", title = "Two", playedAt = 300L))
        repository.addToHistory(history(songId = "content://one", playedAt = 200L))

        val ordered = repository.getMostPlayed()
        assertEquals(listOf("content://one", "content://two"), ordered.map { it.songId })
    }

    private fun history(
        songId: String,
        title: String = "One",
        playedAt: Long
    ) = PlaybackHistoryEntity(
        songId = songId,
        title = title,
        artist = "Artist",
        artworkUrl = null,
        playedAt = playedAt
    )

    private class FakePlaybackHistoryDao : PlaybackHistoryDao {
        private val items = mutableListOf<PlaybackHistoryEntity>()
        private var nextId = 1L

        override suspend fun insertHistory(history: PlaybackHistoryEntity) {
            items += history.copy(id = nextId++)
        }

        override suspend fun updateHistory(history: PlaybackHistoryEntity) {
            val index = items.indexOfFirst { it.id == history.id }
            items[index] = history
        }

        override suspend fun findSongHistory(
            songId: String,
            title: String,
            artist: String
        ): PlaybackHistoryEntity? =
            items.filter { it.songId == songId || (it.title == title && it.artist == artist) }
                .maxByOrNull { it.playedAt }

        override suspend fun deleteBySongId(songId: String) {
            items.removeAll { it.songId == songId }
        }

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
        }

        override suspend fun deleteDuplicateEntries(songId: String, title: String, artist: String) {
            items.removeAll { it.songId == songId || (it.title == title && it.artist == artist) }
        }

        override suspend fun getAllHistory(): List<PlaybackHistoryEntity> =
            items.sortedByDescending { it.playedAt }

        override fun observeAllHistory(): Flow<List<PlaybackHistoryEntity>> =
            flowOf(items.sortedByDescending { it.playedAt })

        override suspend fun getRecentHistory(limit: Int): List<PlaybackHistoryEntity> =
            getAllHistory().take(limit)

        override fun observeRecentHistory(limit: Int): Flow<List<PlaybackHistoryEntity>> =
            flowOf(items.sortedByDescending { it.playedAt }.take(limit))

        override suspend fun getMostPlayed(limit: Int): List<PlaybackHistoryEntity> =
            items.sortedWith(
                compareByDescending<PlaybackHistoryEntity> { it.playCount }
                    .thenByDescending { it.playedAt }
            ).take(limit)

        override fun observeMostPlayed(limit: Int): Flow<List<PlaybackHistoryEntity>> =
            flowOf(
                items.sortedWith(
                    compareByDescending<PlaybackHistoryEntity> { it.playCount }
                        .thenByDescending { it.playedAt }
                ).take(limit)
            )

        override suspend fun trimHistory(maxItems: Int) {
            val kept = items.sortedByDescending { it.playedAt }.take(maxItems).map { it.id }.toSet()
            items.removeAll { it.id !in kept }
        }

        override suspend fun clearHistory() {
            items.clear()
        }
    }
}
