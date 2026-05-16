package com.jagr.fridamusic.data.repository

import com.jagr.fridamusic.data.local.PlaybackHistoryDao
import com.jagr.fridamusic.data.local.PlaybackHistoryEntity

class PlaybackHistoryRepository(
    private val dao: PlaybackHistoryDao
) {

    suspend fun addToHistory(history: PlaybackHistoryEntity) {
        dao.insertHistory(history)
        dao.trimHistory()
    }

    suspend fun getRecentHistory(limit: Int = 10): List<PlaybackHistoryEntity> {
        return dao.getRecentHistory(limit)
    }

    suspend fun getFullHistory(): List<PlaybackHistoryEntity> {
        return dao.getAllHistory()
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }
}