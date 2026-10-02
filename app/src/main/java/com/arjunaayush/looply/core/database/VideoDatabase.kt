package com.arjunaayush.looply.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.arjunaayush.looply.core.database.dao.VideoDao
import com.arjunaayush.looply.core.database.entity.VideoEntity

@Database(
    entities = [VideoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao

    companion object {
        private const val DATABASE_NAME = "looply_room.db"

        @Volatile
        private var INSTANCE: VideoDatabase? = null

        fun getInstance(context: Context): VideoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoDatabase::class.java,
                    DATABASE_NAME
                )
                    // Non-destructive: DO NOT use fallbackToDestructiveMigration
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
