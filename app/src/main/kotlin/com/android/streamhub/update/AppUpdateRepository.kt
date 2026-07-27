package com.android.streamhub.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.android.streamhub.BuildConfig
import com.android.streamhub.update.di.AppUpdateDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// This app's own repo - not distributed via Play Store, so this is the update channel.
private const val GITHUB_REPO = "LMInventories/streamhub"
private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

// A bit under a day, same reasoning as EpgGridRepository's own refresh interval - avoids the
// check creeping later and later, and keeps this well within GitHub's unauthenticated rate limit.
private val CHECK_INTERVAL_SECONDS = TimeUnit.HOURS.toSeconds(12)

enum class UpdateCheckResult { UPDATE_FOUND, UP_TO_DATE, CHECK_FAILED }

/**
 * Single source of truth for "is there a newer build than this one" - checked against GitHub
 * Releases (see .github/workflows/release.yml, which publishes one per `v<versionCode>` tag).
 * [updateAvailable] is what both the Home banner and the Settings row read from, so a check
 * triggered from either surface (or the automatic app-open hook, AppUpdateCheckEffect) updates
 * both at once rather than each polling independently.
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    @AppUpdateDataStore private val dataStore: DataStore<Preferences>,
) {
    private val lastCheckedKey = longPreferencesKey("app_update_last_checked_epoch_seconds")
    private val dismissedVersionCodeKey = intPreferencesKey("app_update_dismissed_version_code")

    private val _updateAvailable = MutableStateFlow<AppUpdateInfo?>(null)
    val updateAvailable: StateFlow<AppUpdateInfo?> = _updateAvailable

    /** Throttled wrapper around [checkNow] - only actually hits GitHub if the last successful check was more than ~12h ago. Called from the app-open hook (AppUpdateCheckEffect). */
    suspend fun checkIfDue() {
        val lastChecked = dataStore.data.map { it[lastCheckedKey] ?: 0L }.first()
        val now = Instant.now().epochSecond
        if (now - lastChecked < CHECK_INTERVAL_SECONDS) return
        checkNow()
    }

    /**
     * Unconditional GitHub hit - used by [checkIfDue] above and by the manual "Check for Updates"
     * Settings row. [bypassDismiss] lets that manual check surface a version the user already
     * dismissed from the Home banner - dismissing there just means "stop nagging me on Home", not
     * "never tell me about this again even if I explicitly ask".
     */
    suspend fun checkNow(bypassDismiss: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val release = runCatching { fetchLatestRelease() }.getOrNull()
            ?: return@withContext UpdateCheckResult.CHECK_FAILED
        // Only stamped on a successful fetch - a transient GitHub outage shouldn't lock the
        // throttled automatic check out for another ~12h.
        dataStore.edit { it[lastCheckedKey] = Instant.now().epochSecond }

        val remoteVersionCode = release.tag_name.removePrefix("v").toIntOrNull()
            ?: return@withContext UpdateCheckResult.CHECK_FAILED
        val apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url
            ?: return@withContext UpdateCheckResult.CHECK_FAILED

        if (remoteVersionCode <= BuildConfig.VERSION_CODE) {
            _updateAvailable.value = null
            return@withContext UpdateCheckResult.UP_TO_DATE
        }

        val dismissed = dataStore.data.map { it[dismissedVersionCodeKey] ?: 0 }.first()
        if (remoteVersionCode == dismissed && !bypassDismiss) {
            return@withContext UpdateCheckResult.UP_TO_DATE
        }

        _updateAvailable.value = AppUpdateInfo(
            versionCode = remoteVersionCode,
            versionName = release.name ?: release.tag_name,
            downloadUrl = apkUrl,
            releaseUrl = release.html_url,
        )
        UpdateCheckResult.UPDATE_FOUND
    }

    suspend fun dismiss(versionCode: Int) {
        dataStore.edit { it[dismissedVersionCodeKey] = versionCode }
        if (_updateAvailable.value?.versionCode == versionCode) {
            _updateAvailable.value = null
        }
    }

    private fun fetchLatestRelease(): GithubReleaseDto {
        val request = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            // GitHub's REST API 403s a request with no User-Agent header at all.
            .header("User-Agent", "StreamHub-Android-App")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub releases request failed: HTTP ${response.code}")
            val body = response.body?.string() ?: error("Empty GitHub releases response body")
            return json.decodeFromString<GithubReleaseDto>(body)
        }
    }
}
