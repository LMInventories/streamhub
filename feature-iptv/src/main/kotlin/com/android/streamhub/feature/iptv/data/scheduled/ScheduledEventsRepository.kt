package com.android.streamhub.feature.iptv.data.scheduled

import com.android.streamhub.feature.iptv.data.EpgProgram
import com.android.streamhub.feature.iptv.data.IptvChannelInfo
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledEventsRepository @Inject constructor(
    private val dao: ScheduledEventsDao,
    private val reminderScheduler: ReminderScheduler,
    private val recordingScheduler: RecordingScheduler,
) {
    fun observeReminders(): Flow<List<ScheduledReminderEntity>> = dao.observeReminders()
    fun observeRecordings(): Flow<List<ScheduledRecordingEntity>> = dao.observeRecordings()
    fun observeRecordedItems(): Flow<List<RecordedItemEntity>> = dao.observeRecordedItems()

    suspend fun addReminder(channel: IptvChannelInfo, program: EpgProgram, leadMinutes: Int) {
        val entity = ScheduledReminderEntity(
            channelId = channel.id,
            channelName = channel.name,
            programTitle = program.title,
            programStartEpochSeconds = program.startAt.epochSecond,
            leadMinutes = leadMinutes,
            createdAtEpochSeconds = Instant.now().epochSecond,
        )
        val id = dao.insertReminder(entity)
        reminderScheduler.schedule(entity.copy(id = id))
    }

    suspend fun removeReminder(reminder: ScheduledReminderEntity) {
        reminderScheduler.cancel(reminder.id)
        dao.deleteReminder(reminder.id)
    }

    /**
     * [startAdjustMinutes]/[endAdjustMinutes] are signed - positive expands the recording window
     * (starts earlier / ends later than the EPG slot), negative shrinks it (starts later / ends
     * earlier), matching "allow for minor adjustments per minute either side of the time slot".
     */
    suspend fun addRecording(channel: IptvChannelInfo, program: EpgProgram, startAdjustMinutes: Int, endAdjustMinutes: Int) {
        val entity = ScheduledRecordingEntity(
            channelId = channel.id,
            channelName = channel.name,
            channelLogoUrl = channel.logoUrl,
            streamUrl = channel.streamUrl,
            programTitle = program.title,
            recordStartEpochSeconds = program.startAt.minusSeconds(startAdjustMinutes * 60L).epochSecond,
            recordEndEpochSeconds = program.endAt.plusSeconds(endAdjustMinutes * 60L).epochSecond,
            createdAtEpochSeconds = Instant.now().epochSecond,
        )
        val id = dao.insertRecording(entity)
        recordingScheduler.scheduleStart(entity.copy(id = id))
    }

    suspend fun removeRecording(recording: ScheduledRecordingEntity) {
        recordingScheduler.cancelStart(recording.id)
        dao.deleteRecording(recording.id)
    }

    suspend fun removeRecordedItem(item: RecordedItemEntity) {
        // Best-effort - a missing/already-deleted file shouldn't block removing the library entry.
        runCatching { File(item.filePath).delete() }
        dao.deleteRecordedItem(item.id)
    }
}
