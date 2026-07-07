package com.android.streamhub.feature.iptv.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes stream_id and category_id when the provider sends JSON numbers`() {
        val raw = """{"stream_id": 1234, "name": "Channel A", "category_id": 5}"""

        val stream = json.decodeFromString<XtreamLiveStream>(raw)

        assertEquals("1234", stream.streamId)
        assertEquals("5", stream.categoryId)
        assertEquals("Channel A", stream.name)
    }

    @Test
    fun `decodes stream_id and category_id when the provider sends JSON strings`() {
        val raw = """{"stream_id": "1234", "name": "Channel A", "category_id": "5"}"""

        val stream = json.decodeFromString<XtreamLiveStream>(raw)

        assertEquals("1234", stream.streamId)
        assertEquals("5", stream.categoryId)
    }
}
