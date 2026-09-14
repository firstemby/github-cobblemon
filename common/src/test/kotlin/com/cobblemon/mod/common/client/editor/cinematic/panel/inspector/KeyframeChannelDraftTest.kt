package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyframeChannelDraftTest {
    private fun scene(channel: ShotChannel, source: JsonObject): EditorScene = EditorScene().apply {
        val shot = EditorShot("shot", JsonObject(), JsonObject())
        channel.set(shot, source)
        shots["shot"] = shot
    }

    private fun source(): JsonObject = JsonParser.parseString("""
        {"type":"keyframe","keyframes":[
          {"time":0,"position":{"x":12345678.125,"y":2,"z":3},"yaw":15,"pitch":5,"fov":70,"roll":10},
          {"time":4,"position":{"x":9,"y":8,"z":7},"yaw":25,"pitch":15,"fov":80,"roll":20}
        ]}
    """).asJsonObject

    @Test
    fun `all channels compose row edits while preserving original snapshots and undo history`() {
        for (channel in ShotChannel.entries) {
            val original = source()
            val before = original.deepCopy()
            val scene = scene(channel, original)
            val draft = KeyframeChannelDraft(scene, "shot", channel, original)
            assertTrue(draft.editValues(0, mapOf("time" to 2f)))
            val first = draft.data
            val field = when (channel) {
                ShotChannel.POSITION -> "time"
                ShotChannel.ROTATION -> "yaw"
                ShotChannel.FOV -> "fov"
                ShotChannel.ROLL -> "roll"
            }
            if (channel == ShotChannel.POSITION) {
                assertTrue(draft.editPosition(0, mapOf("y" to 12.0)))
                assertEquals(12345678.125, draft.data.keyframeRows()!![0].asJsonObject.getAsJsonObject("position").get("x").asDouble)
            } else {
                assertTrue(draft.editValues(0, mapOf(field to 40f)))
                assertEquals(40f, draft.data.keyframeRows()!![0].asJsonObject.get(field).asFloat)
            }
            val second = draft.data
            assertEquals(2f, second.keyframeRows()!![0].asJsonObject.get("time").asFloat)
            assertEquals(before, original)
            assertEquals(before.keyframeRows()!![1], second.keyframeRows()!![1])
            assertTrue(scene.undo())
            assertEquals(first, channel.get(scene.shots.getValue("shot")))
            assertTrue(scene.undo())
            assertEquals(before, channel.get(scene.shots.getValue("shot")))
            assertTrue(scene.redo())
            assertTrue(scene.redo())
            assertEquals(second, channel.get(scene.shots.getValue("shot")))
        }
    }

    @Test
    fun `external same-kind replacement and removal invalidate a draft`() {
        for (channel in ShotChannel.entries) {
            val source = source()
            val scene = scene(channel, source)
            val draft = KeyframeChannelDraft(scene, "shot", channel, source)
            val replacement = source.deepCopy()
            channel.set(scene.shots.getValue("shot"), replacement)
            assertFalse(draft.editValues(0, mapOf("time" to 1f)))
            assertEquals(source, replacement)
            scene.shots.remove("shot")
            assertFalse(draft.edit { error("stale mutation") })
            assertFalse(scene.canUndo())
        }
    }

    @Test
    fun `invalid numeric edits and missing rows leave data and history untouched`() {
        val source = source()
        val scene = scene(ShotChannel.POSITION, source)
        val before = source.deepCopy()
        val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.POSITION, source)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(draft.editValues(0, mapOf("time" to 2f, "yaw" to value)))
            assertFalse(draft.editPosition(0, mapOf("x" to 1.0, "y" to value.toDouble())))
        }
        assertFalse(draft.editPosition(0, mapOf("x" to Double.MAX_VALUE)))
        assertFalse(draft.editValues(-1, mapOf("time" to 1f)))
        assertFalse(draft.editValues(2, mapOf("time" to 1f)))
        assertFalse(draft.remove(2))
        assertFalse(draft.editValues(0, mapOf("time" to 0f)))
        assertEquals(before, source)
        assertFalse(scene.canUndo())
        assertFalse(scene.isDirty)
    }

    @Test
    fun `a failing edit cannot expose partially modified JSON`() {
        val source = source()
        val before = source.deepCopy()
        val scene = scene(ShotChannel.ROTATION, source)
        val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.ROTATION, source)
        assertThrows(IllegalStateException::class.java) {
            draft.editRow(0) { it.addProperty("yaw", 90); error("failed edit") }
        }
        assertEquals(before, source)
        assertFalse(scene.canUndo())
        assertFalse(scene.isDirty)
    }

    @Test
    fun `malformed rows and missing arrays are repaired only by undoable edits`() {
        for (value in listOf("null", "{}", "true", "\"v.keyframes\"")) {
            val source = JsonObject().apply { addProperty("type", "keyframe"); add("keyframes", JsonParser.parseString(value)) }
            val before = source.deepCopy()
            val scene = scene(ShotChannel.FOV, source)
            val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.FOV, source)
            assertFalse(draft.editValues(0, mapOf("fov" to 80f)))
            assertEquals(before, source)
            assertFalse(scene.canUndo())
            assertTrue(draft.append(JsonObject().apply { addProperty("time", 0f); addProperty("fov", 70f) }))
            assertEquals(1, draft.data.keyframeRows()!!.size())
            scene.undo()
            assertEquals(before, scene.shots.getValue("shot").fovData)
        }
        val source = JsonObject().apply { addProperty("type", "keyframe"); add("keyframes", JsonArray().apply { add(true) }) }
        val scene = scene(ShotChannel.FOV, source)
        val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.FOV, source)
        assertTrue(draft.editValues(0, mapOf("time" to -1f, "fov" to 500f)))
        val row = draft.data.keyframeRows()!![0].asJsonObject
        assertEquals(0f, row.get("time").asFloat)
        assertEquals(179f, row.get("fov").asFloat)
        scene.undo()
        assertEquals(source, scene.shots.getValue("shot").fovData)
    }

    @Test
    fun `remove uses the latest row edits and remains reversible`() {
        val source = source()
        val scene = scene(ShotChannel.ROLL, source)
        val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.ROLL, source)
        draft.editValues(1, mapOf("roll" to 50f))
        val edited = draft.data
        assertTrue(draft.remove(0))
        assertEquals(50f, draft.data.keyframeRows()!![0].asJsonObject.get("roll").asFloat)
        scene.undo()
        assertEquals(edited, scene.shots.getValue("shot").rollData)
    }
}
