package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InspectorValueSafetyTest {
    @Test
    fun `modifier edits compose without mutating the original or neighbouring modifiers`() {
        val scene = scene(JsonObject())
        val modifiers = scene.shots.getValue("shot").modifiers
        val original = json("""{"type":"offset","position":{"x":12345678.125,"y":2,"z":3},"yaw":0}""")
        val neighbour = json("""{"type":"shake","frequency":8}""")
        modifiers.add(original)
        modifiers.add(neighbour)
        val before = original.deepCopy()
        assertTrue(editInspectorModifier(scene, "shot", 0, original) { it.getAsJsonObject("position").addProperty("y", 12) })
        val first = modifiers[0]
        assertTrue(editInspectorModifier(scene, "shot", 0, original) { it.addProperty("yaw", 90) })
        val result = modifiers[0]
        assertEquals(12345678.125, result.getAsJsonObject("position").get("x").asDouble)
        assertEquals(12, result.getAsJsonObject("position").get("y").asInt)
        assertEquals(90, result.get("yaw").asInt)
        assertEquals(0, first.get("yaw").asInt)
        assertEquals(before, original)
        assertEquals(neighbour, modifiers[1])
        assertTrue(scene.undo())
        assertTrue(scene.undo())
        assertEquals(before, modifiers[0])
        assertTrue(scene.redo())
        assertTrue(scene.redo())
        assertEquals(result, modifiers[0])
    }

    @Test
    fun `missing stale and unchanged modifier edits leave history untouched`() {
        val scene = scene(JsonObject())
        val original = json("""{"type":"shake","frequency":8}""")
        val modifiers = scene.shots.getValue("shot").modifiers
        modifiers.add(original)
        assertFalse(editInspectorModifier(scene, "shot", 0, original) { it.addProperty("frequency", 8) })
        assertFalse(editInspectorModifier(scene, "shot", 1, original) { it.addProperty("frequency", 12) })
        assertFalse(editInspectorModifier(scene, "missing", 0, original) { it.addProperty("frequency", 12) })
        modifiers[0] = json("""{"type":"offset"}""")
        assertFalse(editInspectorModifier(scene, "shot", 0, original) { it.addProperty("frequency", 12) })
        assertFalse(scene.canUndo())
        assertFalse(scene.isDirty)
    }

    @Test
    fun `motion vectors preserve untouched coordinates and compose edits on the owned channel`() {
        for (key in listOf("from", "to", "origin", "axis", "pivot", "center", "offset", "fallback")) {
            val source = json("""{"type":"motion","$key":{"x":12345678.125,"y":2,"z":3},"duration":7}""")
            val scene = scene(source)
            val original = source.deepCopy()
            assertTrue(editInspectorPositionVector(scene, "shot", source, key, mapOf("y" to 8.0)))
            assertTrue(editInspectorPositionVector(scene, "shot", source, key, mapOf("z" to 9.0)))
            val result = scene.shots.getValue("shot").positionData
            assertEquals(12345678.125, result.getAsJsonObject(key).get("x").asDouble)
            assertEquals(8.0, result.getAsJsonObject(key).get("y").asDouble)
            assertEquals(9.0, result.getAsJsonObject(key).get("z").asDouble)
            assertEquals(7, result.get("duration").asInt)
            assertEquals(original, source)
            assertTrue(scene.undo())
            assertTrue(scene.undo())
            assertEquals(original, scene.shots.getValue("shot").positionData)
            assertTrue(scene.redo())
            assertTrue(scene.redo())
            assertEquals(result, scene.shots.getValue("shot").positionData)
        }
    }

    @Test
    fun `motion vector rejection is atomic and repairing malformed vectors is undoable`() {
        for (vector in listOf("null", "[]", "true", "42", "\"v.arena_center\"")) {
            val source = json("""{"type":"orbit","center":$vector}""")
            val scene = scene(source)
            for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE)) {
                assertFalse(editInspectorPositionVector(scene, "shot", source, "center", mapOf("x" to 1.0, "y" to invalid)))
            }
            assertFalse(scene.canUndo())
            assertFalse(scene.isDirty)
            assertTrue(editInspectorPositionVector(scene, "shot", source, "center", mapOf("x" to 12.5)))
            assertEquals(12.5, scene.shots.getValue("shot").positionData.getAsJsonObject("center").get("x").asDouble)
            assertTrue(scene.undo())
            assertEquals(source, scene.shots.getValue("shot").positionData)
        }
    }

    @Test
    fun `fixed editors reject nonfinite commits and preserve unedited coordinate precision`() {
        val position = json("""{"type":"fixed","position":{"x":12345678.125,"y":2,"z":3}}""")
        val scene = scene(position)
        val shot = scene.shots.getValue("shot")
        shot.rotationData = json("""{"type":"fixed","yaw":0,"pitch":12.25}""")
        shot.fovData = json("""{"type":"fixed","value":70}""")
        shot.rollData = json("""{"type":"fixed","value":0}""")
        val positionEditor = FixedPositionEditor()
        val rotationEditor = FixedRotationEditor()
        val fovEditor = FixedFovEditor()
        val rollEditor = FixedRollEditor()
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(positionEditor.applyValues(scene, "shot", shot.positionData, mapOf("x" to value.toDouble(), "y" to 6.0)))
            assertFalse(rotationEditor.applyValues(scene, "shot", shot.rotationData, mapOf("yaw" to value)))
            assertFalse(fovEditor.applyValue(scene, "shot", shot.fovData!!, value))
            assertFalse(rollEditor.applyValue(scene, "shot", shot.rollData!!, value))
        }
        assertFalse(scene.canUndo())
        assertFalse(scene.isDirty)
        assertTrue(positionEditor.applyValues(scene, "shot", position, mapOf("y" to 8.0)))
        assertEquals(12345678.125, shot.positionData.getAsJsonObject("position").get("x").asDouble)
        assertEquals(2, position.getAsJsonObject("position").get("y").asInt)
        assertTrue(scene.undo())
        assertEquals(position, shot.positionData)
        assertTrue(rotationEditor.applyValues(scene, "shot", shot.rotationData, mapOf("yaw" to 120f)))
        assertEquals(12.25, shot.rotationData.get("pitch").asDouble)
        assertTrue(fovEditor.applyValue(scene, "shot", shot.fovData!!, 500f))
        assertEquals(179f, shot.fovData!!.get("value").asFloat)
        assertTrue(scene.undo())
        assertEquals(70f, shot.fovData!!.get("value").asFloat)
    }

    @Test
    fun `numeric buffers reject expressions malformed values and float overflow without changing JSON`() {
        for (source in listOf("\"v.arena_center_x\"", "null", "true", "{}", "[]", "1e400", "1e39")) {
            val value = JsonParser.parseString(source)
            val original = value.deepCopy()
            assertEquals(70f, value.inspectorFloat(70f), source)
            assertEquals(original, value)
        }
        assertEquals(-12.5f, JsonParser.parseString("-12.5").inspectorFloat())
        val target = json("""{"type":"position","x":"v.arena_center_x","y":{},"z":1e400}""")
        val before = target.deepCopy()
        val position = LookAtTargetEditor.positionForBuffers(target)
        assertEquals(0.0, position.lengthSqr())
        assertTrue(LookAtTargetEditor.hasUserData(target))
        assertEquals(before, target)
    }

    @Test
    fun `channel edits preserve live source snapshots and compose correctly through undo and redo`() {
        val source = json("""{"type":"fixed","position":{"x":1,"y":2,"z":3}}""")
        val scene = scene(source)
        val original = source.deepCopy()
        assertTrue(editInspectorChannel(scene, "shot", ShotChannel.POSITION, source) {
            it.getAsJsonObject("position").addProperty("x", 8)
        })
        val first = scene.shots.getValue("shot").positionData
        assertEquals(original, source)
        assertTrue(editInspectorChannel(scene, "shot", ShotChannel.POSITION, source) {
            it.getAsJsonObject("position").addProperty("y", 9)
        })
        val result = scene.shots.getValue("shot").positionData
        assertEquals(8, result.getAsJsonObject("position").get("x").asInt)
        assertEquals(9, result.getAsJsonObject("position").get("y").asInt)
        assertEquals(2, first.getAsJsonObject("position").get("y").asInt)
        assertTrue(scene.undo())
        assertEquals(first, scene.shots.getValue("shot").positionData)
        assertTrue(scene.undo())
        assertEquals(original, scene.shots.getValue("shot").positionData)
        assertTrue(scene.redo())
        assertTrue(scene.redo())
        assertEquals(result, scene.shots.getValue("shot").positionData)
    }

    @Test
    fun `stale editor kinds missing shots and no-op changes do not create undo entries`() {
        val source = json("""{"type":"fixed","value":30}""")
        val scene = scene(source)
        assertFalse(editInspectorChannel(scene, "shot", ShotChannel.POSITION, source) { it.addProperty("value", 30) })
        assertFalse(scene.canUndo())
        scene.shots.getValue("shot").positionData = json("""{"type":"orbit","radius":10}""")
        assertFalse(editInspectorChannel(scene, "shot", ShotChannel.POSITION, source) { it.addProperty("value", 60) })
        assertFalse(editInspectorChannel(scene, "missing", ShotChannel.POSITION, source) { it.addProperty("value", 60) })
        assertFalse(scene.canUndo())
    }

    @Test
    fun `Look At confirmation cannot apply to another scene shot or changed target`() {
        val editor = LookAtTargetEditor()
        val scene = scene(json("""{"type":"fixed"}"""))
        val source = scene.shots.getValue("shot").rotationData
        scene.shots["other"] = EditorShot("other", JsonObject(), source.deepCopy())
        val otherScene = scene(JsonObject())
        editor.requestSwap(scene, "shot", source, LookAtKind.ACTOR)
        assertFalse(editor.confirmSwap(scene, "other", scene.shots.getValue("other").rotationData))
        editor.requestSwap(scene, "shot", source, LookAtKind.ACTOR)
        assertFalse(editor.confirmSwap(otherScene, "shot", source))
        editor.requestSwap(scene, "shot", source, LookAtKind.ACTOR)
        source.getAsJsonObject("target").addProperty("x", 12)
        assertFalse(editor.confirmSwap(scene, "shot", source))
        editor.requestSwap(scene, "shot", source, LookAtKind.ACTOR)
        scene.shots.getValue("shot").rotationData = source.deepCopy()
        assertFalse(editor.confirmSwap(scene, "shot", source))
        assertFalse(scene.canUndo())
        assertFalse(otherScene.canUndo())
    }

    @Test
    fun `Look At confirmation changes once and retains the original target for undo`() {
        val editor = LookAtTargetEditor()
        val scene = scene(JsonObject())
        val source = scene.shots.getValue("shot").rotationData
        val before = source.deepCopy()
        editor.requestSwap(scene, "shot", source, LookAtKind.EXPRESSION)
        assertTrue(editor.confirmSwap(scene, "shot", source))
        assertFalse(editor.confirmSwap(scene, "shot", source))
        assertEquals(before, source)
        assertEquals("expression", scene.shots.getValue("shot").rotationData.getAsJsonObject("target").get("type").asString)
        assertTrue(scene.undo())
        assertEquals(before, scene.shots.getValue("shot").rotationData)
        assertFalse(scene.canUndo())
    }

    private fun scene(position: JsonObject) = EditorScene().apply {
        shots["shot"] = EditorShot("shot", position, json("""{"type":"look_at","target":{"type":"position","x":1,"y":64,"z":0}}"""))
    }

    private fun json(source: String) = JsonParser.parseString(source).asJsonObject
}
