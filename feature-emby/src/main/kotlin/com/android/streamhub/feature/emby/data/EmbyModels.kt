package com.android.streamhub.feature.emby.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 1 tick = 100ns (Emby's .NET-derived convention, same lineage Jellyfin inherited when it forked
// from Emby's predecessor) - 600_000_000 ticks/min. Unverified against a live Emby server; flagged
// in the Phase 1 plan as a likely-correct-but-unconfirmed assumption.
internal const val TICKS_PER_MS = 10_000L

@Serializable
data class EmbyAuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)

@Serializable
data class EmbyAuthResult(
    @SerialName("User") val user: EmbyUserDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
)

@Serializable
data class EmbyUserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
)

@Serializable
data class EmbyPublicSystemInfo(
    @SerialName("ServerName") val serverName: String? = null,
)

@Serializable
data class EmbyItemsResponse(
    @SerialName("Items") val items: List<EmbyItemDto> = emptyList(),
)

@Serializable
data class EmbyPersonDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    // "Actor" / "Director" / "GuestStar" / etc - Emby's own PersonKind-equivalent string, mirrors
    // Jellyfin's BaseItemPerson.type.
    @SerialName("Type") val type: String? = null,
)

@Serializable
data class EmbyUserDataDto(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Float? = null,
)

@Serializable
data class EmbyMediaSourceDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
)

@Serializable
data class EmbyItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    // "Movie" / "Series" / "Season" / "Episode" / etc.
    @SerialName("Type") val type: String? = null,
    // "movies" / "tvshows" / etc - only present on library (CollectionFolder) items.
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    // Emby's ImageTags is a map keyed by image type ("Primary", "Backdrop", "Logo", ...) to a
    // cache-busting tag - only "Primary" is consumed this pass.
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    // Episode count for a season/series item; null for every other item type.
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("People") val people: List<EmbyPersonDto> = emptyList(),
    @SerialName("UserData") val userData: EmbyUserDataDto? = null,
    @SerialName("MediaSources") val mediaSources: List<EmbyMediaSourceDto> = emptyList(),
)

@Serializable
data class EmbyPlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
)

@Serializable
data class EmbyPlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<EmbyMediaSourceDto> = emptyList(),
)

@Serializable
data class EmbyPlaybackStartRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("CanSeek") val canSeek: Boolean = true,
)

@Serializable
data class EmbyPlaybackProgressRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false,
)

@Serializable
data class EmbyPlaybackStopRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
)
