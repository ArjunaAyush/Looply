package com.arjunaayush.looply.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VideoDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), VideoDao {

    companion object {
        private const val DATABASE_NAME = "looply_videos.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_VIDEOS = "videos"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_FILE_PATH = "file_path"
        private const val COL_DURATION_MS = "duration_ms"
        private const val COL_SIZE_BYTES = "size_bytes"
        private const val COL_WIDTH = "width"
        private const val COL_HEIGHT = "height"
        private const val COL_CREATED_AT = "created_at"

        @Volatile
        private var instance: VideoDatabase? = null

        fun getInstance(context: Context): VideoDatabase {
            return instance ?: synchronized(this) {
                instance ?: VideoDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_VIDEOS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_FILE_PATH TEXT NOT NULL,
                $COL_DURATION_MS INTEGER NOT NULL DEFAULT 0,
                $COL_SIZE_BYTES INTEGER NOT NULL DEFAULT 0,
                $COL_WIDTH INTEGER NOT NULL DEFAULT 0,
                $COL_HEIGHT INTEGER NOT NULL DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_VIDEOS")
        onCreate(db)
    }

    fun videoDao(): VideoDao = this

    override fun insert(entity: VideoEntity): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, entity.id)
            put(COL_TITLE, entity.title)
            put(COL_FILE_PATH, entity.filePath)
            put(COL_DURATION_MS, entity.durationMs)
            put(COL_SIZE_BYTES, entity.sizeBytes)
            put(COL_WIDTH, entity.width)
            put(COL_HEIGHT, entity.height)
            put(COL_CREATED_AT, entity.createdAt)
        }
        return db.insertWithOnConflict(TABLE_VIDEOS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun getAll(): List<VideoEntity> {
        val db = readableDatabase
        val list = mutableListOf<VideoEntity>()
        val cursor = db.query(
            TABLE_VIDEOS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToEntity(it))
            }
        }
        return list
    }

    override fun getById(id: String): VideoEntity? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_VIDEOS,
            null,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToEntity(it) else null
        }
    }

    override fun deleteById(id: String): Int {
        val db = writableDatabase
        return db.delete(TABLE_VIDEOS, "$COL_ID = ?", arrayOf(id))
    }

    override fun deleteAll(): Int {
        val db = writableDatabase
        return db.delete(TABLE_VIDEOS, null, null)
    }

    override fun getCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_VIDEOS", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun cursorToEntity(cursor: Cursor): VideoEntity {
        return VideoEntity(
            id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
            filePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_FILE_PATH)),
            durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(COL_DURATION_MS)),
            sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SIZE_BYTES)),
            width = cursor.getInt(cursor.getColumnIndexOrThrow(COL_WIDTH)),
            height = cursor.getInt(cursor.getColumnIndexOrThrow(COL_HEIGHT)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        )
    }
}
