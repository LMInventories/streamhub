package com.android.streamhub.mock

import androidx.media3.common.MimeTypes
import com.android.streamhub.core.common.domain.MediaSource
import com.android.streamhub.core.common.domain.PlaybackItem
import com.android.streamhub.core.common.domain.SourceType
import com.android.streamhub.core.common.domain.SubtitleTrackRef
import javax.inject.Inject

/**
 * Throwaway Milestone-1 stand-in for a real backend. Exists purely to exercise the
 * player/nav seam end to end before any real IPTV/Jellyfin/Emby adapter exists - delete once
 * a real MediaSource is registered.
 */
class MockMediaSource @Inject constructor() : MediaSource {

    override val sourceType: SourceType = SourceType.MOCK

    private val items = listOf(
        PlaybackItem(
            id = "asset-subtitles",
            sourceType = SourceType.MOCK,
            title = "Bundled clip (sidecar subtitles)",
            subtitle = "Tests subtitle track switching",
            streamUri = "asset:///sample.mp4",
            mimeTypeHint = MimeTypes.VIDEO_MP4,
            subtitleTracks = listOf(
                SubtitleTrackRef(
                    uri = "asset:///sample_en.srt",
                    language = "en",
                    label = "English",
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                ),
                SubtitleTrackRef(
                    uri = "asset:///sample_es.srt",
                    language = "es",
                    label = "Spanish",
                    mimeType = MimeTypes.APPLICATION_SUBRIP,
                ),
            ),
        ),
        PlaybackItem(
            id = "network-hls",
            sourceType = SourceType.MOCK,
            title = "Apple bipbop (adaptive HLS, multi-track audio)",
            subtitle = "Tests HLS playback and audio track switching",
            // Apple's public HLS test asset - includes alternate audio renditions and
            // embedded captions, useful for exercising real adaptive-streaming behaviour.
            streamUri = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
            mimeTypeHint = MimeTypes.APPLICATION_M3U8,
            isLive = false,
        ),
    )

    override suspend fun browse(): List<PlaybackItem> = items

    override suspend fun resolvePlayback(itemId: String): PlaybackItem =
        items.first { it.id == itemId }
}
