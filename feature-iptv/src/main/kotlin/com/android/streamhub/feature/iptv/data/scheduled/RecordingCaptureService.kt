package com.android.streamhub.feature.iptv.data.scheduled

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.android.streamhub.feature.iptv.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val RECORDING_CHANNEL_ID = "live_recording"
private const val NOTIFICATION_ID_BASE = 91_000

/**
 * A raw HTTP byte-copy of the live stream response body to a local file for the scheduled window,
 * not HLS-segment-aware transcoding/remuxing - Xtream/M3U "live" URLs are typically a continuous
 * MPEG-TS response, which is both directly writable as-is and directly playable by Media3's own
 * TS container support afterwards, so nothing fancier is needed for a personal-use DVR.
 *
 * Foreground rather than a plain background service/WorkManager job - it needs to keep running
 * uninterrupted for the whole recording window (which can be well over the ~10 minute ceiling
 * Android puts on background execution), and the persistent notification is also the only
 * visible sign a recording is actually in progress.
 */
@AndroidEntryPoint
class RecordingCaptureService : Service() {

    @Inject lateinit var dao: ScheduledEventsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recordingId = intent?.getLongExtra(EXTRA_RECORDING_ID, -1L) ?: -1L
        if (recordingId < 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Called synchronously, before any suspend work - Android expects startForeground()
        // within seconds of startForegroundService(), well before a Room lookup + HTTP connect
        // could realistically finish.
        ensureRecordingNotificationChannel(this)
        startForegroundCompat(recordingId, buildNotification(title = "Starting recording…", text = null))

        captureJob = scope.launch { capture(recordingId) }
        return START_NOT_STICKY
    }

    // Wrapped end-to-end (not just the HTTP/file portion) so any unexpected failure - including
    // the initial DAO lookup - still lets the service stop itself cleanly instead of an uncaught
    // exception propagating out of this coroutine and crashing the app.
    private suspend fun capture(recordingId: Long) {
        try {
            val recording = dao.getRecordingById(recordingId) ?: return
            startForegroundCompat(recordingId, buildNotification(title = "Recording: ${recording.programTitle}", text = recording.channelName))

            val outputDir = File(filesDir, "recordings").apply { mkdirs() }
            val outputFile = File(outputDir, "${recording.id}_${System.currentTimeMillis()}.ts")
            val endAtMillis = recording.recordEndEpochSeconds * 1000L
            val startedAtMillis = System.currentTimeMillis()

            val succeeded = runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder().url(recording.streamUrl).build()
                client.newCall(request).execute().use { response ->
                    val body = requireNotNull(response.body) { "Empty response body" }
                    body.byteStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (System.currentTimeMillis() < endAtMillis) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }.isSuccess

            if (succeeded && outputFile.length() > 0) {
                dao.insertRecordedItem(
                    RecordedItemEntity(
                        channelName = recording.channelName,
                        channelLogoUrl = recording.channelLogoUrl,
                        programTitle = recording.programTitle,
                        filePath = outputFile.absolutePath,
                        recordedAtEpochSeconds = startedAtMillis / 1000,
                        durationSeconds = (System.currentTimeMillis() - startedAtMillis) / 1000,
                    ),
                )
            } else {
                outputFile.delete()
            }
            dao.deleteRecording(recording.id)
        } catch (e: CancellationException) {
            throw e // must always propagate - swallowing it would break structured concurrency.
        } catch (e: Exception) {
            // Otherwise swallow - a failed/crashed recording attempt shouldn't take the app down with it.
        } finally {
            stopSelfSafely()
        }
    }

    private fun startForegroundCompat(recordingId: Long, notification: Notification) {
        val notificationId = NOTIFICATION_ID_BASE + recordingId.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun buildNotification(title: String, text: String?): Notification =
        NotificationCompat.Builder(this, RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .apply { text?.let { setContentText(it) } }
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        captureJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_RECORDING_ID = "recording_id"

        fun start(context: Context, recordingId: Long) {
            val intent = Intent(context, RecordingCaptureService::class.java)
                .putExtra(EXTRA_RECORDING_ID, recordingId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

private fun ensureRecordingNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
        NotificationChannel(RECORDING_CHANNEL_ID, "Live TV recording", NotificationManager.IMPORTANCE_LOW),
    )
}
