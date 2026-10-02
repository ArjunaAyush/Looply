package com.arjunaayush.looply.domain.usecase

import com.arjunaayush.looply.domain.repository.VideoRepository
import javax.inject.Inject

class DeleteVideoUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteVideo(id)
    }
}
