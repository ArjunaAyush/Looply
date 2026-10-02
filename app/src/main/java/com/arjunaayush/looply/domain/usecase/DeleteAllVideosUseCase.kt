package com.arjunaayush.looply.domain.usecase

import com.arjunaayush.looply.domain.repository.VideoRepository
import javax.inject.Inject

class DeleteAllVideosUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke() {
        repository.deleteAllVideos()
    }
}
