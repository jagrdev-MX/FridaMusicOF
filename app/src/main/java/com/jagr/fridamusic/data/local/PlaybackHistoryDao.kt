package com.jagr.fridamusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Update
    suspend fun updateHistory(history: PlaybackHistoryEntity)

    @Query(
        """
        SELECT * FROM playback_history
        WHERE songId = :songId
           OR (title = :title AND artist = :artist)
        ORDER BY playedAt DESC
        LIMIT 1
        """
    )
    suspend fun findSongHistory(songId: String, title: String, artist: String): PlaybackHistoryEntity?

    @Query("DELETE FROM playback_history WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query(
        """
        DELETE FROM playback_history
        WHERE songId = :songId
           OR (
                title = :title
            AND artist = :artist
            AND songId LIKE 'http%'
           )
        """
    )
    suspend fun deleteDuplicateEntries(songId: String, title: String, artist: String)

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playedAt DESC
    """)
    suspend fun getAllHistory(): List<PlaybackHistoryEntity>

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playedAt DESC
    """)
    fun observeAllHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playedAt DESC
        LIMIT :limit
    """)
    suspend fun getRecentHistory(limit: Int): List<PlaybackHistoryEntity>

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playedAt DESC
        LIMIT :limit
    """)
    fun observeRecentHistory(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playCount DESC, playedAt DESC
        LIMIT :limit
    """)
    suspend fun getMostPlayed(limit: Int): List<PlaybackHistoryEntity>

    @Query("""
        SELECT * FROM playback_history
        ORDER BY playCount DESC, playedAt DESC
        LIMIT :limit
    """)
    fun observeMostPlayed(limit: Int): Flow<List<PlaybackHistoryEntity>>

    @Query("DELETE FROM playback_history WHERE id NOT IN (SELECT id FROM playback_history ORDER BY playedAt DESC LIMIT :maxItems)")
    suspend fun trimHistory(maxItems: Int = 200)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
