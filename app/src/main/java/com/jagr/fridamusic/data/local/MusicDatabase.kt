package com.jagr.fridamusic.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaybackHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playback_history ADD COLUMN playCount INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_songId` ON `playback_history` (`songId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_title_artist` ON `playback_history` (`title`, `artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_playedAt` ON `playback_history` (`playedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_playCount_playedAt` ON `playback_history` (`playCount`, `playedAt`)")
            }
        }
    }
}
