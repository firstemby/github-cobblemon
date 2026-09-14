package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CutLaneMathTest {
    @Test fun `dragging a middle coincident cut preserves chronological order`() {
        for (proposed in listOf(-1f, 0f, 1f)) {
            val times = mutableListOf(0f, 0f, 0f)
            times[1] = CutLaneMath.clampToMonotonic(times, 1, proposed, 10f)
            assertTrue(times.zipWithNext().all { (a, b) -> a <= b })
        }
    }


    @Test
    fun `clampToMonotonic keeps time strictly between previous and next cut`() {
        val times = listOf(0f, 3f, 7f, 10f)
        val clamped = CutLaneMath.clampToMonotonic(cutTimes = times, index = 2, proposedTime = 15f, beatDuration = 12f, minGap = 0.1f)
        assertTrue(clamped < 10f && clamped > 3f)
    }

    @Test
    fun `clampToMonotonic does not throw when neighbors are closer than 2 minGap`() {
        val times = listOf(0f, 0f)
        val clamped = CutLaneMath.clampToMonotonic(
            cutTimes = times, index = 0, proposedTime = 5f, beatDuration = 10f, minGap = 0.05f
        )
        assertEquals(0f, clamped, 1e-3f)
    }

    @Test
    fun `blockBounds spans from this cut to the next through the shared axis`() {
        val times = listOf(0f, 3f, 7f)
        val axis = TimeAxis(originX = 100f, widthPx = 400f, pixelsPerSecond = 40f, scrollSeconds = 0f)
        val (x0, x1) = CutLaneMath.blockBounds(cutTimes = times, index = 1, axis = axis, beatDuration = 10f)
        assertEquals(220f, x0, 1e-3f)
        assertEquals(380f, x1, 1e-3f)
    }

    @Test
    fun `blockAt picks the block the cursor is actually over`() {
        val bounds = listOf(100f to 220f, 220f to 380f, 380f to 500f)
        assertEquals(1, CutLaneMath.blockAt(mouseX = 300f, blockBoundsPx = bounds))
        assertEquals(-1, CutLaneMath.blockAt(mouseX = 999f, blockBoundsPx = bounds))
    }

    @Test
    fun `blockAt agrees with the bounds the renderer drew after a pan`() {
        // Hit-testing must consume rendered bounds so panning cannot desynchronize input and drawing.
        val times = listOf(0f, 3f, 7f)
        val axis = TimeAxis(originX = 100f, widthPx = 400f, pixelsPerSecond = 40f, scrollSeconds = 2.5f)
        val bounds = times.indices.map { CutLaneMath.blockBounds(times, it, axis, beatDuration = 10f) }
        val midOfSecond = (bounds[1].first + bounds[1].second) / 2f
        assertEquals(1, CutLaneMath.blockAt(mouseX = midOfSecond, blockBoundsPx = bounds))
    }
}
