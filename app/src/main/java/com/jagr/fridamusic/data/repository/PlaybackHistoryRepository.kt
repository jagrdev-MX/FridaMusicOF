package com.jagr.fridamusic.data.repository

import com.jagr.fridamusic.data.local.PlaybackHistoryDao
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity

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

    suspend fun getFullHistory(): List<PlaybackHistoryEntity> {
        return dao.getAllHistory()
    }

    suspend fun getMostPlayed(limit: Int = 20): List<PlaybackHistoryEntity> {
        return dao.getMostPlayed(limit)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }
}
