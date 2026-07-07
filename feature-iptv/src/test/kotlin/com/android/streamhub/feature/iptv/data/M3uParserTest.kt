package com.android.streamhub.feature.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses channels with full tvg attributes`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://logos/bbc1.png" group-title="News",BBC One
            http://stream.example/bbc1.m3u8
            #EXTINF:-1 tvg-id="bbc2.uk" tvg-logo="http://logos/bbc2.png" group-title="News",BBC Two
            http://stream.example/bbc2.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(playlist)

        assertEquals(2, channels.size)
        assertEquals("bbc1.uk", channels[0].id)
        assertEquals("BBC One", channels[0].name)
        assertEquals("http://logos/bbc1.png", channels[0].logoUrl)
        assertEquals("News", channels[0].groupTitle)
        assertEquals("http://stream.example/bbc1.m3u8", channels[0].streamUrl)
        assertEquals("bbc2.uk", channels[1].id)
        assertEquals("BBC Two", channels[1].name)
    }

    @Test
    fun `falls back to a generated id and index name when attributes are missing`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,
            http://stream.example/unnamed.ts
        """.trimIndent()

        val channels = M3uParser.parse(playlist)

        assertEquals(1, channels.size)
        assertEquals("m3u-0", channels[0].id)
        assertEquals("Channel 1", channels[0].name)
        assertNull(channels[0].logoUrl)
    }

    @Test
    fun `ignores unrelated tags and blank lines`() {
        val playlist = """
            #EXTM3U

            #EXTGRP:News
            #EXTINF:-1 tvg-id="x",X Channel
            http://stream.example/x.ts

        """.trimIndent()

        val channels = M3uParser.parse(playlist)

        assertEquals(1, channels.size)
        assertEquals("X Channel", channels[0].name)
    }

    @Test
    fun `returns an empty list for an empty playlist`() {
        assertTrue(M3uParser.parse("#EXTM3U").isEmpty())
    }
}
