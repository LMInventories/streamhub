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
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Played") val played: Boolean = false,
)

// Same shape/field-name semantics as Jellyfin's MediaStream (shared .NET-lineage API design) -
// UNVERIFIED against a live Emby server, same caveat as EmbyApi.getLatestItems below. If wrong,
// the fix is scoped to this DTO plus EmbyBrowseRepository.toItemInfo's extraction block, which is
// the only place these fields are read.
@Serializable
data class EmbyMediaStreamDto(
    // "Video" / "Audio" / "Subtitle".
    @SerialName("Type") val type: String? = null,
    @SerialName("Index") val index: Int? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsDefault") val isDefault: Boolean = false,
)

// UNVERIFIED nested shape (mediaSourceId -> width -> tile info) for Emby's scrubbing-preview
// sprite sheets - no public Emby API docs were found for this despite searching, this is a
// best guess mirroring Jellyfin's own SDK TrickplayInfo field names/semantics (plausible given
// Emby/Jellyfin's shared lineage, but not confirmed). Degrades gracefully: if the real JSON
// shape differs, this field just fails to deserialize per-item (or decodes to an empty map,
// depending on how strict the Json config is) and EmbyItemInfo.trickplayInfo stays null -  same
// "feature silently absent" contract as everywhere else in this module. See
// EmbyBrowseRepository.toItemInfo's trickplay extraction block.
@Serializable
data class EmbyTrickplayTileDto(
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    // Columns/rows of thumbnails packed into one sprite-sheet image.
    @SerialName("TileWidth") val tileWidth: Int = 0,
    @SerialName("TileHeight") val tileHeight: Int = 0,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    // Milliseconds between consecutive thumbnails.
    @SerialName("Interval") val interval: Int = 0,
)

@Serializable
data class EmbyMediaSourceDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("Name") val name: String? = null,
    // UNVERIFIED shape - see EmbyMediaStreamDto's own doc.
    @SerialName("MediaStreams") val mediaStreams: List<EmbyMediaStreamDto> = emptyList(),
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
    // UNVERIFIED - see EmbyTrickplayTileDto's doc. Keyed by media source id, then by tile width
    // (as a string, mirroring how Jellyfin's own SDK model represents this map in JSON).
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, EmbyTrickplayTileDto>> = emptyMap(),
)

@Serializable
data class EmbyPlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    // Bps, not Mbps - same unit/nullable-Int shape as Jellyfin SDK's PlaybackInfoDto.maxStreamingBitrate
    // (confirmed by JellyfinBrowseRepository.getStreamUrl's own Mbps-to-bps conversion assigning
    // straight into that field with no widening call). Null means unlimited/direct-play - only
    // ever narrows what the server sends, never forces transcoding of already-fits content.
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
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
