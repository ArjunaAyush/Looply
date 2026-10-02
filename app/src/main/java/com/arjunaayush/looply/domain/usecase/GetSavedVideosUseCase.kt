package com.arjunaayush.looply.domain.usecase

import com.arjunaayush.looply.domain.model.Video
import com.arjunaayush.looply.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSavedVideosUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    operator fun invoke(): Flow<List<Video>> = repository.getAllVideos()
}
