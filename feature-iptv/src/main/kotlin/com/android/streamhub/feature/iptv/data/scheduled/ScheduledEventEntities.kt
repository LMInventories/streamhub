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
    val channelLogoUrl: String?,
    val streamUrl: String,
    val programTitle: String,
    val recordStartEpochSeconds: Long,
    val recordEndEpochSeconds: Long,
    val createdAtEpochSeconds: Long,
)

/**
 * A completed capture, separate from ScheduledRecordingEntity (the request) - the schedule row
 * is deleted once RecordingCaptureService finishes, this is what the Recordings library actually
 * lists and plays. filePath is an absolute path under the app's own files dir (never
 * user-visible/shared storage - no MANAGE_EXTERNAL_STORAGE or scoped-storage dance needed for a
 * file only this app ever reads).
 */
@Entity(tableName = "recorded_items")
data class RecordedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelName: String,
    val channelLogoUrl: String?,
    val programTitle: String,
    val filePath: String,
    val recordedAtEpochSeconds: Long,
    val durationSeconds: Long,
)
