package com.android.streamhub.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val UPDATE_APK_FILENAME = "streamhub-update.apk"

/**
 * Downloads and hands a new APK off to the system package installer - this app isn't distributed
 * via Play Store, so this is how "Update" in the Settings row actually works. Mirrors
 * inspectpro-mobile's own proven updateService.ts flow (download with progress into a private
 * cache dir, content:// URI via FileProvider, ACTION_VIEW install intent) rather than
 * android.app.DownloadManager, which this used previously: that relied on a system component that
 * turned out to be unreliable across real devices - it silently never even started a download on
 * some Android TV builds, and its ACTION_DOWNLOAD_COMPLETE broadcast-based install handoff wasn't
 * firing reliably on phone either. A direct HTTP download we fully control end-to-end (no system
 * service, no broadcast receiver) sidesteps both.
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Launch via rememberLauncherForActivityResult - lets the user grant "install unknown apps" for this app specifically, same idiom as the existing POST_NOTIFICATIONS ask in ProgramSchedulingUi.kt. */
    fun requestInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Downloads [info]'s APK into this app's private cache dir (no storage permission needed on
     * any API level), reporting [onProgress] as bytes arrive, then launches the system package
     * installer against it via a FileProvider content URI - same shape as inspectpro-mobile's
     * downloadAndInstall(). Throws on a download failure - callers should catch/report, same as
     * every other network call in this app.
     */
    suspend fun downloadAndInstall(info: AppUpdateInfo, onProgress: (Float) -> Unit) {
        val dest = File(context.cacheDir, UPDATE_APK_FILENAME)
        withContext(Dispatchers.IO) {
            if (dest.exists()) dest.delete()

            val request = Request.Builder().url(info.downloadUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Update download failed: HTTP ${response.code}")
                val body = response.body ?: error("Empty update download response")
                val totalBytes = body.contentLength()
                var bytesRead = 0L
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) onProgress((bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Belt-and-suspenders alongside FLAG_GRANT_READ_URI_PERMISSION above - some OEM package
        // installers resolved via an implicit ACTION_VIEW intent don't reliably receive that grant,
        // and silently failing to read the content:// URI is exactly what surfaces to the user as
        // "There was a problem parsing the package" instead of a clearer permission error.
        context.packageManager.queryIntentActivities(installIntent, 0).forEach { resolveInfo ->
            context.grantUriPermission(resolveInfo.activityInfo.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        // Deliberately not deleted here - startActivity() only launches the installer
        // asynchronously and returns immediately, well before a separate process has actually
        // opened and read the file; deleting it right away raced that read and was the other half
        // of the same "problem parsing the package" failure. The download step above already
        // deletes any previous leftover file before writing a new one, which is enough cleanup.
    }
}
