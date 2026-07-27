package com.android.streamhub.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val UPDATE_APK_FILENAME = "streamhub-update.apk"

/**
 * Downloads and hands a new APK off to the system package installer - this app isn't distributed
 * via Play Store, so this is how "Update" in the Home banner / Settings row actually works. Uses
 * plain android.app.DownloadManager rather than Media3's own DownloadManager (core-player) - that
 * one is purpose-built for HLS/DASH media segments and cache eviction, a poor fit for a single
 * APK binary.
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloadManager = ContextCompat.getSystemService(context, DownloadManager::class.java)
    private var pendingDownloadId: Long? = null

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (completedId == -1L || completedId != pendingDownloadId) return
            pendingDownloadId = null
            val uri = runCatching { downloadManager?.getUriForDownloadedFile(completedId) }.getOrNull() ?: return
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(installIntent) }
        }
    }

    init {
        // Registered once for the process lifetime, not tied to any Activity/ViewModel scope -
        // the download can complete while the app is backgrounded, and this class is itself a
        // @Singleton living for the whole app process anyway.
        ContextCompat.registerReceiver(
            context,
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Launch via rememberLauncherForActivityResult - lets the user grant "install unknown apps" for this app specifically, same idiom as the existing POST_NOTIFICATIONS ask in ProgramSchedulingUi.kt. */
    fun requestInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun startDownload(info: AppUpdateInfo) {
        val manager = downloadManager ?: return
        runCatching {
            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("StreamHub update")
                .setDescription("Downloading version ${info.versionName}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // App-scoped external storage - no storage permission needed on API 26+ (this
                // app's own minSdk floor). Can throw IOException if the dir can't be created.
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, UPDATE_APK_FILENAME)
            pendingDownloadId = manager.enqueue(request)
        }
    }
}
