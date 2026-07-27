package com.android.streamhub.update

import kotlinx.serialization.Serializable

/** A newer build than what's currently installed, found via GitHub's Releases API. */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

// Only the fields this app actually reads - the app-wide Json (IptvNetworkModule) already has
// ignoreUnknownKeys = true, so GitHub's much larger real payload parses fine against this.
@Serializable
data class GithubReleaseDto(
    val tag_name: String,
    val name: String? = null,
    val html_url: String,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
data class GithubAssetDto(
    val name: String,
    val browser_download_url: String,
)
