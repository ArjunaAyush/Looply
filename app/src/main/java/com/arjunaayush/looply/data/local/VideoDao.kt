package com.arjunaayush.looply.data.local

interface VideoDao {
    fun insert(entity: VideoEntity): Long
    fun getAll(): List<VideoEntity>
    fun getById(id: String): VideoEntity?
    fun deleteById(id: String): Int
    fun deleteAll(): Int
    fun getCount(): Int
}
