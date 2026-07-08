package com.android.streamhub.feature.iptv.data.scheduled

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledEventsDao {
    @Query("SELECT * FROM scheduled_reminders ORDER BY programStartEpochSeconds ASC")
    fun observeReminders(): Flow<List<ScheduledReminderEntity>>

    @Query("SELECT * FROM scheduled_reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ScheduledReminderEntity?

    @Insert
    suspend fun insertReminder(entity: ScheduledReminderEntity): Long

    @Query("DELETE FROM scheduled_reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("SELECT * FROM scheduled_recordings ORDER BY recordStartEpochSeconds ASC")
    fun observeRecordings(): Flow<List<ScheduledRecordingEntity>>

    @Insert
    suspend fun insertRecording(entity: ScheduledRecordingEntity): Long

    @Query("DELETE FROM scheduled_recordings WHERE id = :id")
    suspend fun deleteRecording(id: Long)
}
