package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Base64

@Serializable
data class XtreamShortEpgResponse(
    @SerialName("epg_listings")
    val epgListings: List<XtreamEpgListing> = emptyList(),
)

@Serializable
data class XtreamEpgListing(
    @SerialName("title")
    val titleBase64: String? = null,
    // Not every provider's panel includes these; prefer them when present since they're
    // unambiguous (no server-timezone guesswork required).
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("start_timestamp")
    val startTimestamp: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("stop_timestamp")
    val stopTimestamp: String? = null,
    // Fallback "yyyy-MM-dd HH:mm:ss" fields some panels send instead - assumed UTC, since the
    // provider rarely states its own timezone. Best-effort; a provider running its EPG in local
    // time will show times off by the UTC offset until we can calibrate against real data.
    @SerialName("start")
    val startLocal: String? = null,
    @SerialName("end")
    val endLocal: String? = null,
)

fun XtreamEpgListing.toEpgProgram(): EpgProgram? {
    val start = resolveInstant(startTimestamp, startLocal) ?: return null
    val end = resolveInstant(stopTimestamp, endLocal) ?: return null
    val title = decodeXtreamText(titleBase64) ?: return null
    return EpgProgram(title = title, startAt = start, endAt = end)
}

private fun resolveInstant(epochSeconds: String?, localDateTime: String?): Instant? {
    epochSeconds?.toLongOrNull()?.let { return Instant.ofEpochSecond(it) }
    return localDateTime?.let { raw ->
        runCatching {
            java.time.LocalDateTime
                .parse(raw.trim().replace(' ', 'T'))
                .toInstant(java.time.ZoneOffset.UTC)
        }.getOrNull()
    }
}

private fun decodeXtreamText(base64OrPlain: String?): String? {
    if (base64OrPlain.isNullOrBlank()) return null
    return runCatching { String(Base64.getDecoder().decode(base64OrPlain)) }
        .getOrDefault(base64OrPlain)
}
