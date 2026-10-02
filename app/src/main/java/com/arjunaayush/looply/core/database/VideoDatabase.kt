package com.arjunaayush.looply.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arjunaayush.looply.core.database.dao.PendingReelDao
import com.arjunaayush.looply.core.database.dao.VideoDao
import com.arjunaayush.looply.core.database.entity.PendingReelEntity
import com.arjunaayush.looply.core.database.entity.VideoEntity

@Database(
    entities = [VideoEntity::class, PendingReelEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun pendingReelDao(): PendingReelDao

    companion object {
        private const val DATABASE_NAME = "looply_room.db"

        val MIGRATION_1_TO_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_reels (
                        mediaId TEXT NOT NULL PRIMARY KEY,
                        shortcode TEXT,
                        ownerUsername TEXT,
                        caption TEXT,
                        progressiveUrl TEXT,
                        dashManifest TEXT,
                        thumbnailUrl TEXT,
                        urlExpiresAtEpochSec INTEGER,
                        capturedAtMillis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_reels_status ON pending_reels(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_reels_shortcode ON pending_reels(shortcode)")
            }
        }

        @Volatile
        private var INSTANCE: VideoDatabase? = null

        fun getInstance(context: Context): VideoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_TO_2)
                    // Non-destructive: DO NOT use fallbackToDestructiveMigration
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
