package com.android.streamhub.feature.iptv.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
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
 *
 * Takes a raw InputStream, not a String - some providers' xmltv.php dumps run into the hundreds
 * of MB (observed on a real device: a single response requiring a ~283MB contiguous allocation),
 * and reading that into one String first - as this used to do - reliably OOMs regardless of
 * anything downstream. SAX parses incrementally straight off the stream, so peak memory here is
 * bounded by the *parsed* output, not the raw XML size.
 */
object XmlTvParser {
    private val offsetFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    private val localFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * [keepFrom]/[keepUntil] discard programmes outside the window as they're parsed, rather than
     * after - for a guide with months of data across thousands of channels, keeping only the
     * days the grid actually displays meaningfully bounds the result list's own memory footprint
     * too, on top of the streaming fix above.
     */
    fun parse(input: InputStream, keepFrom: Instant? = null, keepUntil: Instant? = null): List<XmlTvProgramme> {
        val results = mutableListOf<XmlTvProgramme>()

        val handler = object : DefaultHandler() {
            var channelId: String? = null
            var programStart: Instant? = null
            var programStop: Instant? = null
            var title: StringBuilder? = null
            var desc: StringBuilder? = null
            var inTitle = false
            var inDesc = false

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (qName) {
                    "programme" -> {
                        channelId = attributes?.getValue("channel")
                        programStart = parseTimestamp(attributes?.getValue("start"))
                        programStop = parseTimestamp(attributes?.getValue("stop"))
                        title = null
                        desc = null
                    }
                    "title" -> {
                        inTitle = true
                        title = StringBuilder()
                    }
                    "desc" -> {
                        inDesc = true
                        desc = StringBuilder()
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (ch == null) return
                if (inTitle) title?.append(ch, start, length)
                if (inDesc) desc?.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "title" -> inTitle = false
                    "desc" -> inDesc = false
                    "programme" -> {
                        val channel = channelId
                        val start = programStart
                        val stop = programStop
                        val text = title?.toString()?.trim()
                        val descriptionText = desc?.toString()?.trim()?.takeIf(String::isNotBlank)
                        if (!channel.isNullOrBlank() && start != null && stop != null && !text.isNullOrBlank()) {
                            val inWindow = (keepFrom == null || stop >= keepFrom) && (keepUntil == null || start <= keepUntil)
                            if (inWindow) {
                                results += XmlTvProgramme(
                                    channel,
                                    EpgProgram(title = text, startAt = start, endAt = stop, description = descriptionText),
                                )
                            }
                        }
                    }
                }
            }
        }

        SAXParserFactory.newInstance().newSAXParser().parse(InputSource(input), handler)
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
