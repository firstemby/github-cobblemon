package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ActorTrackTypes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelinePresentationTest {
    @Test
    fun `large text in a short timeline reserves room for editable tracks`() {
        assertEquals(false, TimelinePresentation.showOverview(94f, 26f))
        assertEquals(true, TimelinePresentation.compactClips(48f, 26f))
    }

    @Test
    fun `roomy timelines retain two line shots and overview navigation`() {
        assertEquals(true, TimelinePresentation.showOverview(172f, 13f))
        assertEquals(false, TimelinePresentation.compactClips(116f, 13f))
    }

    @Test
    fun `tick labels retain the precision of the current zoom`() {
        assertEquals("0.02s", TimelinePresentation.tickLabel(0.02f, 0.01f))
        assertEquals("0.4s", TimelinePresentation.tickLabel(0.4f, 0.2f))
        assertEquals("10s", TimelinePresentation.tickLabel(10f, 5f))
    }

    @Test
    fun `timecode carries rounded milliseconds into minutes`() {
        assertEquals("01:00.000", TimelinePresentation.timecode(59.9999f))
        assertEquals("00:00.000", TimelinePresentation.timecode(-2f))
        assertEquals("02:03.456", TimelinePresentation.timecode(123.456f))
    }

    @Test
    fun `track names are readable without changing their model IDs`() {
        ActorTrackTypes.entries().forEach { entry ->
            assertEquals(entry.label, TimelinePresentation.trackName(entry.trackType))
        }
        assertEquals("custom_track", TimelinePresentation.trackName("custom_track"))
    }

    @Test
    fun `ruler ticks omit negative preroll and include zero`() {
        val ticks = TimelinePresentation.ticks(TimeAxis(100f, 400f, 40f, -0.25f), 2f)
        assertEquals(listOf(0f, 2f, 4f, 6f, 8f), ticks)
    }

    @Test
    fun `ruler tick count is bounded and rejects invalid steps`() {
        assertTrue(TimelinePresentation.ticks(TimeAxis(0f, 400f, 2f, 0f), 0.001f).size <= 1001)
        assertTrue(TimelinePresentation.ticks(TimeAxis(0f, 400f, 2f, 0f), 0f).isEmpty())
    }

    @Test
    fun `overview navigation centers requested time and clamps edges`() {
        val axis = TimeAxis(0f, 400f, 100f, 0f)
        assertEquals(3f, TimelinePresentation.overviewScroll(0.5f, 10f, axis), 0.001f)
        assertEquals(-0.25f, TimelinePresentation.overviewScroll(0f, 10f, axis), 0.001f)
        assertEquals(6.25f, TimelinePresentation.overviewScroll(1f, 10f, axis), 0.001f)
    }
}
