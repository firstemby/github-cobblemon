package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutTrack
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.client.editor.cinematic.panel.TimelinePlayhead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineBeatSpanTest {

    @Test
    fun `auto beats include cues in their visible timeline span`() {
        val beat = EditorBeat("main", cues = listOf(SceneCue("handoff", 20f)))
        assertEquals(20f, TimelineBeatSpan.contentEnd(beat))
        assertTrue(TimelineBeatSpan.spanOf(beat) > 20f)
    }

    private fun beatWithCuts(vararg times: Float) = EditorBeat(id = "b").apply {
        cuts["primary"] = EditorCutTrack(
            times.mapIndexed { i, t -> EditorCutEntry(t, "shot$i", CutTransition.Instant) }.toMutableList()
        )
    }

    @Test
    fun `an explicit duration wins over the derived one`() {
        val beat = beatWithCuts(0f, 3f).apply { duration = 4f }
        assertEquals(4f, TimelineBeatSpan.spanOf(beat), 1e-4f)
    }

    @Test
    fun `an auto beat keeps headroom past its last cut so the cut still has width`() {
        val beat = beatWithCuts(0f, 14f)
        val span = TimelineBeatSpan.spanOf(beat)
        assertTrue(span > 14f, "expected room past the last cut, got $span")
    }

    @Test
    fun `an empty auto beat still gets a usable window`() {
        assertEquals(TimelineBeatSpan.MINIMUM_SECONDS, TimelineBeatSpan.spanOf(EditorBeat(id = "b")), 1e-4f)
    }

    @Test
    fun `playhead runs free when the beat has no explicit duration`() {
        val ph = TimelinePlayhead()
        ph.setBeatDuration(0f, looping = false)
        ph.setTime(1.5f)
        assertEquals(1.5f, ph.time, 1e-4f)
        ph.step(0.5f)
        assertEquals(2.0f, ph.time, 1e-4f)
    }
}
