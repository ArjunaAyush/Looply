package com.arjunaayush.looply.di

import com.arjunaayush.looply.data.repository.VideoRepositoryImpl
import com.arjunaayush.looply.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(
        impl: VideoRepositoryImpl
    ): VideoRepository
}
