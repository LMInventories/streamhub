package com.android.streamhub.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackOptionTest {

    private fun audioFormat(id: String, language: String?, label: String? = null): Format =
        Format.Builder()
            .setId(id)
            .setLanguage(language)
            .setLabel(label)
            .setSampleMimeType("audio/mp4a-latm")
            .build()

    private fun textFormat(id: String, language: String?, label: String? = null): Format =
        Format.Builder()
            .setId(id)
            .setLanguage(language)
            .setLabel(label)
            .setSampleMimeType("text/vtt")
            .build()

    @Test
    fun `maps only the requested track type, preserving selection state`() {
        val audioGroup = Tracks.Group(
            TrackGroup(audioFormat("a-en", "en"), audioFormat("a-es", "es")),
            /* adaptiveSupported = */ false,
            /* trackSupport = */ intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            /* trackSelected = */ booleanArrayOf(true, false),
        )
        val textGroup = Tracks.Group(
            TrackGroup(textFormat("s-en", "en", "English"), textFormat("s-fr", "fr", "French")),
            false,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(false, false),
        )
        val tracks = Tracks(listOf(audioGroup, textGroup))

        val audioOptions = tracks.toTrackOptions(C.TRACK_TYPE_AUDIO)
        val subtitleOptions = tracks.toTrackOptions(C.TRACK_TYPE_TEXT)

        assertEquals(2, audioOptions.size)
        assertEquals("0:0", audioOptions[0].id)
        assertTrue(audioOptions[0].isSelected)
        assertFalse(audioOptions[1].isSelected)
        assertEquals("en", audioOptions[0].language)

        assertEquals(2, subtitleOptions.size)
        assertEquals("1:0", subtitleOptions[0].id)
        assertEquals("English", subtitleOptions[0].label)
        assertEquals("French", subtitleOptions[1].label)
    }

    @Test
    fun `falls back to language then a generic label when no explicit label is set`() {
        val group = Tracks.Group(
            TrackGroup(audioFormat("a-1", "de", label = null), audioFormat("a-2", null, label = null)),
            false,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED),
            booleanArrayOf(false, false),
        )
        val options = Tracks(listOf(group)).toTrackOptions(C.TRACK_TYPE_AUDIO)

        assertEquals("DE", options[0].label)
        assertEquals("Audio a-2", options[1].label)
    }

    @Test
    fun `returns an empty list when no groups match the requested type`() {
        val group = Tracks.Group(
            TrackGroup(audioFormat("a-1", "en")),
            false,
            intArrayOf(C.FORMAT_HANDLED),
            booleanArrayOf(false),
        )
        val options = Tracks(listOf(group)).toTrackOptions(C.TRACK_TYPE_TEXT)

        assertTrue(options.isEmpty())
    }
}
