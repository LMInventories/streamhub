package com.android.streamhub.feature.iptv.data.scheduled

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Same setAndAllowWhileIdle tradeoff as ReminderScheduler - a recording starting/stopping within
 * a couple of minutes of the scheduled edge is acceptable for a personal-use app, and avoids the
 * SCHEDULE_EXACT_ALARM settings-intent grant flow exact alarms need on Android 12+.
 */
@Singleton
class RecordingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java)

    fun scheduleStart(recording: ScheduledRecordingEntity) {
        val triggerAtMillis = recording.recordStartEpochSeconds * 1000L
        // Already started (e.g. the user recorded something already in progress) - start capture
        // immediately rather than silently doing nothing because the alarm trigger time is past.
        if (triggerAtMillis <= System.currentTimeMillis()) {
            RecordingCaptureService.start(context, recording.id)
            return
        }
        runCatching {
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntentFor(recording.id))
        }
    }

    fun cancelStart(recordingId: Long) {
        alarmManager?.cancel(pendingIntentFor(recordingId))
    }

    private fun pendingIntentFor(recordingId: Long): PendingIntent {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).apply {
            putExtra(RecordingAlarmReceiver.EXTRA_RECORDING_ID, recordingId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Distinct request-code space from ReminderScheduler's (which also keys off a Long id
            // cast to Int) - offsetting by a large constant keeps a reminder and a recording that
            // happen to share a raw row id from colliding on the same PendingIntent.
            (recordingId + 500_000_000L).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
