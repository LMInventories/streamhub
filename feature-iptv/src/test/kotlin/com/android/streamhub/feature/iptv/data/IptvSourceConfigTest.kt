package com.android.streamhub.feature.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvSourceConfigTest {

    @Test
    fun `builds a live stream url from base url, credentials, and stream id`() {
        val config = IptvSourceConfig.Xtream(
            baseUrl = "http://example.com:8080",
            username = "user",
            password = "pass",
        )

        assertEquals("http://example.com:8080/live/user/pass/42.ts", config.liveStreamUrl("42"))
    }

    @Test
    fun `strips a trailing slash from the base url`() {
        val config = IptvSourceConfig.Xtream(
            baseUrl = "http://example.com:8080/",
            username = "user",
            password = "pass",
        )

        assertEquals("http://example.com:8080/live/user/pass/42.ts", config.liveStreamUrl("42"))
    }

    @Test
    fun `supports a custom stream extension`() {
        val config = IptvSourceConfig.Xtream(baseUrl = "http://h", username = "u", password = "p")

        assertEquals("http://h/live/u/p/7.m3u8", config.liveStreamUrl("7", extension = "m3u8"))
    }
}
