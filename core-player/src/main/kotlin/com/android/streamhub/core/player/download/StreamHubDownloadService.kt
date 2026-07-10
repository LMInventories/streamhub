package com.android.streamhub.core.player.download

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.android.streamhub.core.player.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val FOREGROUND_NOTIFICATION_ID = 4001
private const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "streamhub_downloads"

/**
 * Media3 requires downloads to run inside a DownloadService while any are active, so there's a
 * foreground notification while a download is in progress (Android's own requirement for any
 * long-running background work, not something this app added). No Scheduler - downloads pause if
 * the app process is killed and resume next time this service starts (e.g. the user reopening the
 * app), rather than persisting across reboots via JobScheduler/WorkManager - a reasonable scope
 * cut for a personal-use app, not something a multi-user/production app would need to skip.
 */
@UnstableApi
@AndroidEntryPoint
class StreamHubDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel_name,
    0,
) {
    @Inject
    lateinit var downloadManagerInstance: DownloadManager

    private val notificationHelper by lazy { DownloadNotificationHelper(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = downloadManagerInstance

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements,
        )
}
