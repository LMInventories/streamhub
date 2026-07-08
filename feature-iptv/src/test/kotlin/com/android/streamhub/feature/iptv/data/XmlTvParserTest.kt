package com.android.streamhub.feature.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XmlTvParserTest {

    @Test
    fun `parses programmes with UTC offset timestamps`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="bbc1.uk"><display-name>BBC One</display-name></channel>
              <programme start="20260707200000 +0000" stop="20260707210000 +0000" channel="bbc1.uk">
                <title>The News</title>
                <desc>Evening news.</desc>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = XmlTvParser.parse(xml.byteInputStream())

        assertEquals(1, programmes.size)
        assertEquals("bbc1.uk", programmes[0].channelId)
        assertEquals("The News", programmes[0].program.title)
        assertEquals(Instant.parse("2026-07-07T20:00:00Z"), programmes[0].program.startAt)
        assertEquals(Instant.parse("2026-07-07T21:00:00Z"), programmes[0].program.endAt)
        assertEquals("Evening news.", programmes[0].program.description)
    }

    @Test
    fun `description is null when the programme has no desc element`() {
        val xml = """
            <tv>
              <programme start="20260707200000 +0000" stop="20260707210000 +0000" channel="x">
                <title>No Description</title>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = XmlTvParser.parse(xml.byteInputStream())

        assertEquals(1, programmes.size)
        assertEquals(null, programmes[0].program.description)
    }

    @Test
    fun `falls back to assuming UTC when no offset is present`() {
        val xml = """
            <tv>
              <programme start="20260707200000" stop="20260707210000" channel="x">
                <title>No Offset</title>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = XmlTvParser.parse(xml.byteInputStream())

        assertEquals(1, programmes.size)
        assertEquals(Instant.parse("2026-07-07T20:00:00Z"), programmes[0].program.startAt)
    }

    @Test
    fun `skips programmes missing required fields`() {
        val xml = """
            <tv>
              <programme start="20260707200000 +0000" channel="x"><title>Missing stop</title></programme>
              <programme start="20260707200000 +0000" stop="20260707210000 +0000" channel="x"></programme>
            </tv>
        """.trimIndent()

        assertTrue(XmlTvParser.parse(xml.byteInputStream()).isEmpty())
    }

    @Test
    fun `groups by channel id as expected by the remote data source`() {
        val xml = """
            <tv>
              <programme start="20260707200000 +0000" stop="20260707210000 +0000" channel="a"><title>A1</title></programme>
              <programme start="20260707210000 +0000" stop="20260707220000 +0000" channel="a"><title>A2</title></programme>
              <programme start="20260707200000 +0000" stop="20260707210000 +0000" channel="b"><title>B1</title></programme>
            </tv>
        """.trimIndent()

        val grouped = XmlTvParser.parse(xml.byteInputStream()).groupBy(keySelector = { it.channelId }, valueTransform = { it.program })

        assertEquals(2, grouped["a"]?.size)
        assertEquals(1, grouped["b"]?.size)
        assertEquals("A1", grouped["a"]?.get(0)?.title)
    }
}
