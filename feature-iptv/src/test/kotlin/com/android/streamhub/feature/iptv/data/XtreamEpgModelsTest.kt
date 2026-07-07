package com.android.streamhub.feature.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Base64

class XtreamEpgModelsTest {

    private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun `decodes base64 title and description alongside epoch timestamps`() {
        val listing = XtreamEpgListing(
            titleBase64 = base64("Evening News"),
            startTimestamp = "1000",
            stopTimestamp = "2000",
            descriptionBase64 = base64("Tonight's headlines."),
        )

        val program = listing.toEpgProgram()

        assertEquals("Evening News", program?.title)
        assertEquals("Tonight's headlines.", program?.description)
        assertEquals(Instant.ofEpochSecond(1000), program?.startAt)
        assertEquals(Instant.ofEpochSecond(2000), program?.endAt)
    }

    @Test
    fun `description is null when the provider omits it`() {
        val listing = XtreamEpgListing(
            titleBase64 = base64("Evening News"),
            startTimestamp = "1000",
            stopTimestamp = "2000",
        )

        assertNull(listing.toEpgProgram()?.description)
    }

    @Test
    fun `falls back to raw text when a provider doesn't actually base64-encode despite the convention`() {
        val listing = XtreamEpgListing(
            titleBase64 = "Plain Title",
            startTimestamp = "1000",
            stopTimestamp = "2000",
        )

        assertEquals("Plain Title", listing.toEpgProgram()?.title)
    }
}
