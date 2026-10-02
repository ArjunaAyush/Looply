package com.arjunaayush.looply.domain.usecase

import com.arjunaayush.looply.domain.model.CreatorGroup
import com.arjunaayush.looply.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVideosByCreatorUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    operator fun invoke(): Flow<List<CreatorGroup>> = repository.getCreatorGroups()
}
