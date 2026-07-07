package com.android.streamhub.feature.iptv.data

data class M3uChannel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamUrl: String,
)

/**
 * Parses extended-M3U playlists (#EXTM3U / #EXTINF tvg-* attributes) into a flat channel list.
 * Pure function, deliberately independent of any network/Android dependency so it's cheap to
 * unit test against sample playlist text.
 */
object M3uParser {
    private val attributeRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(playlist: String): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        var pendingAttributes: Map<String, String> = emptyMap()
        var pendingName: String? = null
        var index = 0

        for (rawLine in playlist.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#EXTM3U")) continue

            if (line.startsWith("#EXTINF")) {
                pendingAttributes = attributeRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                pendingName = line.substringAfterLast(',', missingDelimiterValue = "").trim()
                continue
            }

            if (line.startsWith("#")) continue // other tags (#EXTGRP, #EXTVLCOPT, ...) - not needed yet

            val name = pendingName?.takeIf { it.isNotBlank() }
                ?: pendingAttributes["tvg-name"]?.takeIf { it.isNotBlank() }
                ?: "Channel ${index + 1}"
            channels += M3uChannel(
                id = pendingAttributes["tvg-id"]?.takeIf { it.isNotBlank() } ?: "m3u-$index",
                name = name,
                logoUrl = pendingAttributes["tvg-logo"],
                groupTitle = pendingAttributes["group-title"],
                streamUrl = line,
            )
            index++
            pendingAttributes = emptyMap()
            pendingName = null
        }

        return channels
    }
}
