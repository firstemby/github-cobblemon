package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimelineSnapTest {
    @Test
    fun `snaps to the nearest candidate within threshold`() {
        val snapped = TimelineSnap.snap(proposed = 2.04f, candidates = listOf(0f, 2f, 5f), thresholdSeconds = 0.1f)
        assertEquals(2f, snapped, 1e-3f)
    }

    @Test
    fun `returns proposed when nothing is within threshold`() {
        val snapped = TimelineSnap.snap(proposed = 3.5f, candidates = listOf(0f, 2f, 5f), thresholdSeconds = 0.1f)
        assertEquals(3.5f, snapped, 1e-3f)
    }
}
