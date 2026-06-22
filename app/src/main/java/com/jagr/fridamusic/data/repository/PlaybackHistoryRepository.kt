package com.jagr.fridamusic.data.repository

import com.jagr.fridamusic.data.local.PlaybackHistoryDao
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity
import kotlinx.coroutines.flow.Flow

class PlaybackHistoryRepository(
    private val dao: PlaybackHistoryDao
) {

    suspend fun addToHistory(history: PlaybackHistoryEntity) {
        val existing = dao.findSongHistory(
            songId = history.songId,
            title = history.title,
            artist = history.artist
        )
        if (existing == null) {
            dao.insertHistory(history)
        } else {
            dao.updateHistory(
                existing.copy(
                    songId = history.songId,
                    artworkUrl = history.artworkUrl,
                    playedAt = history.playedAt,
                    playCount = existing.playCount + 1
                )
            )
        }
        dao.trimHistory()
    }

    suspend fun getRecentHistory(limit: Int = 10): List<PlaybackHistoryEntity> {
        return dao.getRecentHistory(limit)
    }

    fun observeRecentHistory(limit: Int = 10): Flow<List<PlaybackHistoryEntity>> {
        return dao.observeRecentHistory(limit)
    }

    suspend fun getFullHistory(): List<PlaybackHistoryEntity> {
        return dao.getAllHistory()
    }

    fun observeFullHistory(): Flow<List<PlaybackHistoryEntity>> {
        return dao.observeAllHistory()
    }

    suspend fun getMostPlayed(limit: Int = 20): List<PlaybackHistoryEntity> {
        return dao.getMostPlayed(limit)
    }

    fun observeMostPlayed(limit: Int = 20): Flow<List<PlaybackHistoryEntity>> {
        return dao.observeMostPlayed(limit)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun removeFromHistory(songId: String, title: String, artist: String) {
        dao.deleteDuplicateEntries(songId, title, artist)
        dao.deleteBySongId(songId)
    }

    suspend fun removeFromHistory(item: PlaybackHistoryEntity) {
        dao.deleteById(item.id)
    }
}
