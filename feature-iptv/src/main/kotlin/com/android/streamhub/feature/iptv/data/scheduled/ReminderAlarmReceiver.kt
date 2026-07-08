package com.android.streamhub.feature.iptv.data.scheduled

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.android.streamhub.feature.iptv.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val REMINDER_CHANNEL_ID = "epg_reminders"
private const val REMINDER_NOTIFICATION_ID_BASE = 90_000

/**
 * Only carries the reminder's Room id in the Intent - looks the rest up fresh when the alarm
 * fires, rather than baking programme details into the alarm's extras, so this stays correct
 * even if something about the reminder changed between scheduling and firing.
 */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: ScheduledEventsDao

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return

        // BroadcastReceivers are killed shortly after onReceive() returns - goAsync() extends
        // that just long enough for the suspend Room lookup below to finish.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.getReminderById(reminderId)?.let { reminder -> postNotification(context, reminder) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, reminder: ScheduledReminderEntity) {
        ensureReminderNotificationChannel(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val startTime = DateTimeFormatter.ofPattern("HH:mm")
            .format(Instant.ofEpochSecond(reminder.programStartEpochSeconds).atZone(ZoneId.systemDefault()))
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(reminder.programTitle)
            .setContentText("${reminder.channelName} · starts $startTime")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID_BASE + reminder.id.toInt(), notification)
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}

fun ensureReminderNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(REMINDER_CHANNEL_ID, "Programme reminders", NotificationManager.IMPORTANCE_HIGH),
    )
}
