package com.arjunaayush.looply.di

import android.content.Context
import com.arjunaayush.looply.core.database.VideoDatabase
import com.arjunaayush.looply.core.database.dao.PendingReelDao
import com.arjunaayush.looply.core.database.dao.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVideoDatabase(
        @ApplicationContext context: Context
    ): VideoDatabase = VideoDatabase.getInstance(context)

    @Provides
    fun provideVideoDao(
        database: VideoDatabase
    ): VideoDao = database.videoDao()

    @Provides
    fun providePendingReelDao(
        database: VideoDatabase
    ): PendingReelDao = database.pendingReelDao()
}
