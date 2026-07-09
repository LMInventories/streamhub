package com.android.streamhub.feature.iptv.data.scheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Only carries the recording's Room id, same reasoning as ReminderAlarmReceiver - the service
 * looks the rest up fresh rather than trusting stale Intent extras. No goAsync()/coroutine work
 * happens here directly - starting a foreground service is synchronous and fast enough to run
 * straight in onReceive(), unlike the reminder's own suspend Room lookup.
 */
class RecordingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val recordingId = intent.getLongExtra(EXTRA_RECORDING_ID, -1L)
        if (recordingId < 0) return
        RecordingCaptureService.start(context, recordingId)
    }

    companion object {
        const val EXTRA_RECORDING_ID = "recording_id"
    }
}
