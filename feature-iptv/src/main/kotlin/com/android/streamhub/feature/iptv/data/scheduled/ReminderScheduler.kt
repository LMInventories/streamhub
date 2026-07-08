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
 * Uses setAndAllowWhileIdle (inexact, Doze-aware) rather than setExactAndAllowWhileIdle -
 * exact alarms need the special SCHEDULE_EXACT_ALARM permission on Android 12+, which needs its
 * own settings-intent grant flow. A reminder firing within a couple of minutes of the requested
 * lead time is an acceptable tradeoff against that complexity for a personal-use app.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java)

    fun schedule(reminder: ScheduledReminderEntity) {
        val triggerAtMillis = (reminder.programStartEpochSeconds - reminder.leadMinutes * 60L) * 1000L
        if (triggerAtMillis <= System.currentTimeMillis()) return

        runCatching {
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntentFor(reminder.id))
        }
    }

    fun cancel(reminderId: Long) {
        alarmManager?.cancel(pendingIntentFor(reminderId))
    }

    private fun pendingIntentFor(reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
