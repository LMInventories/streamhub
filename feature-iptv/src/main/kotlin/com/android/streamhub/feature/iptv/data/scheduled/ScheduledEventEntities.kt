package com.android.streamhub.feature.iptv.data.scheduled

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_reminders")
data class ScheduledReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val programTitle: String,
    val programStartEpochSeconds: Long,
    val leadMinutes: Int,
    val createdAtEpochSeconds: Long,
)

/**
 * recordStart/recordEndEpochSeconds are the already-adjusted times (EPG start/end plus the
 * user's per-minute padding), not the raw EPG slot - the capture mechanism just needs a plain
 * time window, it doesn't need to know it was ever adjusted.
 */
@Entity(tableName = "scheduled_recordings")
data class ScheduledRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val channelName: String,
    val streamUrl: String,
    val programTitle: String,
    val recordStartEpochSeconds: Long,
    val recordEndEpochSeconds: Long,
    val createdAtEpochSeconds: Long,
)
