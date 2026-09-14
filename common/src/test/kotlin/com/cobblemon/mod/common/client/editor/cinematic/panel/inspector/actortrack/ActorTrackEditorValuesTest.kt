package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActorDirection
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActorTrackEditorValuesTest {
    private fun scene(track: EditorTrack): EditorScene = EditorScene().apply {
        beats["main"] = EditorBeat("main").apply {
            actorDirections["hero"] = EditorActorDirection().apply { tracks.add(track) }
        }
    }

    @Test
    fun `nested edits preserve untouched precision and each undo snapshot`() {
        val target = JsonObject().apply {
            addProperty("x", "v.arena_center_x")
            addProperty("y", 64.123456789)
            addProperty("z", 2.0)
        }
        val track = EditorTrack("look_at", JsonObject().apply { add("target", target) })
        val original = track.data.deepCopy()
        val scene = scene(track)

        assertTrue(editActorTrackVector(scene, "main", "hero", 0, track, "target", mapOf("z" to 3.0)))
        val first = track.data.deepCopy()
        assertEquals(original.getAsJsonObject("target"), target)
        assertEquals("v.arena_center_x", first.getAsJsonObject("target").get("x").asString)
        assertEquals(64.123456789, first.getAsJsonObject("target").get("y").asDouble)
        assertTrue(editActorTrackVector(scene, "main", "hero", 0, track, "target", mapOf("y" to 65.0)))
        val second = track.data.deepCopy()
        assertEquals(3.0, second.getAsJsonObject("target").get("z").asDouble)

        scene.undo()
        assertEquals(first, track.data)
        scene.undo()
        assertEquals(original, track.data)
        scene.redo()
        scene.redo()
        assertEquals(second, track.data)
    }

    @Test
    fun `failed edit does not mutate data or history`() {
        val track = EditorTrack("attach", JsonObject())
        val scene = scene(track)
        assertThrows(IllegalStateException::class.java) {
            editActorTrack(scene, "main", "hero", 0, track) {
                it.trackObject("offset").addProperty("x", 1)
                error("failed edit")
            }
        }
        assertEquals(JsonObject(), track.data)
        assertFalse(scene.isDirty)
        assertFalse(scene.canUndo())
    }

    @Test
    fun `replaced track cannot be edited through its former index`() {
        val track = EditorTrack("attach", JsonObject())
        val scene = scene(track)
        val replacement = EditorTrack("attach", JsonObject())
        scene.beats.getValue("main").actorDirections.getValue("hero").tracks[0] = replacement
        assertFalse(editActorTrack(scene, "main", "hero", 0, track) { error("stale mutation") })
        assertEquals(JsonObject(), replacement.data)
        assertFalse(scene.canUndo())
    }

    @Test
    fun `invalid vectors and unchanged edits leave history empty`() {
        val track = EditorTrack("look_at", JsonObject())
        val scene = scene(track)
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            assertFalse(editActorTrackVector(scene, "main", "hero", 0, track, "target", mapOf("x" to 1.0, "y" to value)))
        }
        assertFalse(editActorTrack(scene, "main", "hero", 0, track) {})
        assertEquals(JsonObject(), track.data)
        assertFalse(scene.canUndo())
        assertFalse(scene.isDirty)
    }

    @Test
    fun `row edits retain the preceding edit after action replaces nested arrays`() {
        val cue = JsonObject().apply { addProperty("time", 1f); addProperty("name", "idle") }
        val track = EditorTrack("animation", JsonObject().apply { add("cues", JsonArray().apply { add(cue) }) })
        val scene = scene(track)
        editActorTrack(scene, "main", "hero", 0, track) { it.get("cues").trackArray()!!.trackRow(0).addProperty("time", 2f) }
        editActorTrack(scene, "main", "hero", 0, track) { it.get("cues").trackArray()!!.trackRow(0).addProperty("name", "attack") }
        val current = track.data.getAsJsonArray("cues")[0].asJsonObject
        assertEquals(2f, current.get("time").asFloat)
        assertEquals("attack", current.get("name").asString)
        assertEquals(1f, cue.get("time").asFloat)
        assertEquals("idle", cue.get("name").asString)
        scene.undo()
        assertEquals(2f, track.data.getAsJsonArray("cues")[0].asJsonObject.get("time").asFloat)
        assertEquals("idle", track.data.getAsJsonArray("cues")[0].asJsonObject.get("name").asString)
    }
}
