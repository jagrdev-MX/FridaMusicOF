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
    version = 3,
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
                    .addMigrations(MIGRATION_2_3)
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
    }
}
