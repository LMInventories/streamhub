package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Xtream's get_vod_info/get_series_info/get_series responses are notoriously inconsistent
 * between panel implementations - fields that are "always a number" on one panel show up as a
 * quoted string on another (rating/duration_secs/episode_num/season are the known offenders,
 * per community client implementations like go.xtream-codes). Every numeric-ish field here goes
 * through FlexibleStringSerializer and gets parsed defensively rather than trusting a fixed
 * JSON type. ignoreUnknownKeys is already on (IptvNetworkModule), so fields we don't model
 * (seasons[], backdrop_path, etc.) are safely dropped rather than breaking decoding.
 */

@Serializable
data class XtreamVodInfoResponse(
    val info: XtreamVodDetail? = null,
    @SerialName("movie_data")
    val movieData: XtreamVodMovieData? = null,
)

@Serializable
data class XtreamVodMovieData(
    val name: String? = null,
    @SerialName("container_extension")
    val containerExtension: String? = null,
)

@Serializable
data class XtreamVodDetail(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    @SerialName("releasedate")
    val releaseDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",
    val duration: String? = null,
    @SerialName("movie_image")
    val movieImage: String? = null,
    val audio: XtreamStreamProbe? = null,
    val video: XtreamStreamProbe? = null,
)

/** One representative stream's ffprobe summary - Xtream reports a single audio/video stream here, not an enumerable list of every track in a multi-audio file, so this is shown as an info tag rather than a real selectable option. */
@Serializable
data class XtreamStreamProbe(
    @SerialName("codec_name")
    val codecName: String? = null,
    val tags: XtreamStreamTags? = null,
)

@Serializable
data class XtreamStreamTags(
    val language: String? = null,
)

@Serializable
data class XtreamSeriesCategory(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("category_name")
    val categoryName: String,
)

@Serializable
data class XtreamSeries(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("series_id")
    val seriesId: String,
    val name: String,
    val cover: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("category_id")
    val categoryId: String = "",
    // See XtreamVodStream.added's own comment - same convention, same caveats.
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("added")
    val added: String = "",
)

@Serializable
data class XtreamSeriesInfoResponse(
    val info: XtreamSeriesDetail? = null,
    // Keyed by season number as a string (Xtream convention) - trusted over info's own season
    // list, which the community Go client couldn't even give a stable type to.
    val episodes: Map<String, List<XtreamEpisode>> = emptyMap(),
)

@Serializable
data class XtreamSeriesDetail(
    val name: String? = null,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",
)

@Serializable
data class XtreamEpisode(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String,
    val title: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("episode_num")
    val episodeNum: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val season: String = "",
    @SerialName("container_extension")
    val containerExtension: String? = null,
    val info: XtreamEpisodeDetail? = null,
)

@Serializable
data class XtreamEpisodeDetail(
    val plot: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val rating: String = "",
    val duration: String? = null,
    @SerialName("movie_image")
    val movieImage: String? = null,
    val audio: XtreamStreamProbe? = null,
    val video: XtreamStreamProbe? = null,
)
