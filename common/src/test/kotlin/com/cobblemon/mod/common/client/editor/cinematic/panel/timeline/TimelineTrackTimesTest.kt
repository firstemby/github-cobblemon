package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import org.junit.jupiter.api.Assertions
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimelineTrackTimesTest {
    private fun track(type: String, build: JsonObject.() -> Unit) =
        EditorTrack(type, JsonObject().apply(build))

    @Test
    fun `reads keyframe_movement times`() {
        val t = track("keyframe_movement") {
            val arr = JsonArray()
            listOf(0f, 1.5f, 3f).forEach { time -> arr.add(JsonObject().apply { addProperty("time", time) }) }
            add("keyframes", arr)
        }
        assertEquals(listOf(0f, 1.5f, 3f), TimelineTrackTimes.times(t))
    }

    @Test
    fun `reads animation_track cue times`() {
        val t = track("animation_track") {
            val arr = JsonArray(); arr.add(JsonObject().apply { addProperty("time", 2f) }); add("cues", arr)
        }
        assertEquals(listOf(2f), TimelineTrackTimes.times(t))
    }

    @Test
    fun `non-timed tracks return empty`() {
        assertEquals(emptyList<Float>(), TimelineTrackTimes.times(track("pose_lock_track") { addProperty("pose", "STAND") }))
        assertEquals(emptyList<Float>(), TimelineTrackTimes.times(track("spline_movement") { addProperty("duration", 4f) }))
    }

    @Test
    fun `withItemRemoved drops the indexed keyframe`() {
        val t = track("keyframe_movement") {
            val arr = JsonArray()
            listOf(0f, 1f, 2f).forEach { time -> arr.add(JsonObject().apply { addProperty("time", time) }) }
            add("keyframes", arr)
        }
        val result = TimelineTrackTimes.withItemRemoved(t, 1)
        val times = result.getAsJsonArray("keyframes").map { it.asJsonObject.get("time").asFloat }
        Assertions.assertEquals(listOf(0f, 2f), times)
    }
}
