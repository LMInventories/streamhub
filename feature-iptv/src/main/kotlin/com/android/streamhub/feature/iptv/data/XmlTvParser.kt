package com.android.streamhub.feature.iptv.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

data class XmlTvProgramme(val channelId: String, val program: EpgProgram)

/**
 * Parses an XMLTV guide (<programme channel="..." start="..." stop="..."><title>...</title>)
 * into a flat list. Uses SAX rather than StAX - javax.xml.stream isn't part of the Android
 * runtime (it would only work in the host-JVM unit test, then throw NoClassDefFoundError on a
 * real device), whereas javax.xml.parsers/org.xml.sax are available on both.
 */
object XmlTvParser {
    private val offsetFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    private val localFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(xml: String): List<XmlTvProgramme> {
        val results = mutableListOf<XmlTvProgramme>()

        val handler = object : DefaultHandler() {
            var channelId: String? = null
            var programStart: Instant? = null
            var programStop: Instant? = null
            var title: StringBuilder? = null
            var inTitle = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (qName) {
                    "programme" -> {
                        channelId = attributes?.getValue("channel")
                        programStart = parseTimestamp(attributes?.getValue("start"))
                        programStop = parseTimestamp(attributes?.getValue("stop"))
                        title = null
                    }
                    "title" -> {
                        inTitle = true
                        title = StringBuilder()
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (inTitle && ch != null) title?.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "title" -> inTitle = false
                    "programme" -> {
                        val channel = channelId
                        val start = programStart
                        val stop = programStop
                        val text = title?.toString()?.trim()
                        if (!channel.isNullOrBlank() && start != null && stop != null && !text.isNullOrBlank()) {
                            results += XmlTvProgramme(channel, EpgProgram(title = text, startAt = start, endAt = stop))
                        }
                    }
                }
            }
        }

        SAXParserFactory.newInstance().newSAXParser().parse(InputSource(StringReader(xml)), handler)
        return results
    }

    private fun parseTimestamp(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return runCatching { OffsetDateTime.parse(trimmed, offsetFormat).toInstant() }
            .recoverCatching { LocalDateTime.parse(trimmed, localFormat).toInstant(ZoneOffset.UTC) }
            .getOrNull()
    }
}
