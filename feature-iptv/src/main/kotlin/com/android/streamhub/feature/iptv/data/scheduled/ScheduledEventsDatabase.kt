package com.android.streamhub.feature.iptv.data.scheduled

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScheduledReminderEntity::class, ScheduledRecordingEntity::class, RecordedItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ScheduledEventsDatabase : RoomDatabase() {
    abstract fun scheduledEventsDao(): ScheduledEventsDao
}
