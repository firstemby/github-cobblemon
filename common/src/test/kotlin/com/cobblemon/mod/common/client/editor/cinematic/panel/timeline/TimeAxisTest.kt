package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.cinematic.panel.TimelinePlayhead
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimeAxisTest {
    @Test
    fun `timeToX and xToTime round-trip with zoom and pan`() {
        val axis = TimeAxis(originX = 100f, widthPx = 400f, pixelsPerSecond = 40f, scrollSeconds = 2f)
        assertEquals(220f, axis.timeToX(5f), 1e-3f)
        assertEquals(5f, axis.xToTime(220f), 1e-3f)
    }

    @Test
    fun `zoomAroundCursor keeps the time under the cursor fixed`() {
        val axis = TimeAxis(originX = 100f, widthPx = 400f, pixelsPerSecond = 40f, scrollSeconds = 0f)
        val cursorX = 300f
        val tBefore = axis.xToTime(cursorX)
        val zoomed = axis.zoomedAroundCursor(cursorX, factor = 2f)
        assertEquals(tBefore, zoomed.xToTime(cursorX), 1e-3f)
        assertEquals(80f, zoomed.pixelsPerSecond, 1e-3f)
    }

    @Test
    fun `niceTickStep grows as zoom shrinks`() {
        val zoomedIn = TimeAxis(0f, 400f, 200f, 0f).niceTickStep()
        val zoomedOut = TimeAxis(0f, 400f, 10f, 0f).niceTickStep()
        assertTrue(zoomedOut > zoomedIn)
    }

    @Test
    fun `fittedTo frames the whole beat plus margins inside the lane`() {
        val fitted = TimeAxis(originX = 0f, widthPx = 400f, pixelsPerSecond = 80f, scrollSeconds = 9f)
            .fittedTo(duration = 12f, marginSeconds = 0.5f)
        assertEquals(-0.5f, fitted.scrollSeconds, 1e-3f)
        assertEquals(0f, fitted.timeToX(-0.5f), 1e-3f)
        assertEquals(400f, fitted.timeToX(12.5f), 1e-3f)
    }

    @Test
    fun `clampedScrollFor pins the left edge when the whole beat fits`() {
        val axis = TimeAxis(originX = 0f, widthPx = 400f, pixelsPerSecond = 80f, scrollSeconds = 30f)
        assertEquals(-0.25f, axis.clampedScrollFor(duration = 2f, marginSeconds = 0.25f), 1e-3f)
    }

    @Test
    fun `clampedScrollFor stops panning past the end of the beat`() {
        val axis = TimeAxis(originX = 0f, widthPx = 400f, pixelsPerSecond = 80f, scrollSeconds = 900f)
        assertEquals(7.25f, axis.clampedScrollFor(duration = 12f, marginSeconds = 0.25f), 1e-3f)
    }

    @Test
    fun `nearestIndex is measured in pixels so zoom changes what is reachable`() {
        val times = listOf(0f, 0.5f, 1.5f)
        val zoomedOut = TimeAxis(originX = 100f, widthPx = 400f, pixelsPerSecond = 10f, scrollSeconds = 0f)
        assertEquals(1, zoomedOut.nearestIndex(mouseX = 105f, times = times, hitRadiusPx = 6f))
        val zoomedIn = TimeAxis(originX = 100f, widthPx = 400f, pixelsPerSecond = 200f, scrollSeconds = 0f)
        assertEquals(-1, zoomedIn.nearestIndex(mouseX = 153f, times = times, hitRadiusPx = 6f))
    }

    @Test
    fun `scrollToFollow only moves once the playhead leaves the window`() {
        val axis = TimeAxis(originX = 0f, widthPx = 400f, pixelsPerSecond = 80f, scrollSeconds = 2f)
        assertEquals(2f, axis.scrollToFollow(4f), 1e-3f)
        assertEquals(6.5f, axis.scrollToFollow(9f), 1e-3f)
    }

    @Test
    fun `playhead step wraps within a looping beat`() {
        val p = TimelinePlayhead()
        p.setBeatDuration(2f, looping = true)
        p.setTime(1.5f)
        p.step(1.0f)
        Assertions.assertEquals(0.5f, p.time, 1e-3f)
    }
}
