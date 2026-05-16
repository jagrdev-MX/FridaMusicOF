package com.jagr.fridamusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playedAt DESC
    """)
    suspend fun getAllHistory(): List<PlaybackHistoryEntity>

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentHistory(limit: Int): List<PlaybackHistoryEntity>

    @Query("DELETE FROM playback_history WHERE id NOT IN (SELECT id FROM playback_history ORDER BY playedAt DESC LIMIT :maxItems)")
    suspend fun trimHistory(maxItems: Int = 200)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}