package com.android.streamhub.core.player.progress

import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.WatchProgress
import com.android.streamhub.core.common.domain.WatchProgressRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepositoryImpl @Inject constructor(
    private val dao: WatchProgressDao,
) : WatchProgressRepository {

    override suspend fun getProgress(sourceType: SourceType, itemId: String): WatchProgress? {
        val entity = dao.get(sourceType.name, itemId) ?: return null
        return WatchProgress(positionMs = entity.positionMs, durationMs = entity.durationMs)
    }

    override suspend fun saveProgress(sourceType: SourceType, itemId: String, positionMs: Long, durationMs: Long) {
        dao.upsert(
            WatchProgressEntity(
                sourceType = sourceType.name,
                itemId = itemId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAtEpochSeconds = Instant.now().epochSecond,
            ),
        )
    }
}
