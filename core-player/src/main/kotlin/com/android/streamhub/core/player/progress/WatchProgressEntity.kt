package com.android.streamhub.core.player.progress

import androidx.room.Entity

@Entity(tableName = "watch_progress", primaryKeys = ["sourceType", "itemId"])
data class WatchProgressEntity(
    val sourceType: String,
    val itemId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochSeconds: Long,
)
