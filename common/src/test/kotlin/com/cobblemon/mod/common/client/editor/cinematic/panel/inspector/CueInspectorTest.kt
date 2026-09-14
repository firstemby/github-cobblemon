package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CueInspectorTest {
    @Test fun `cue identifiers retain the full character limit in UTF8 buffers`() {
        val editor = CueInspector()
        val id = "é".repeat(128)
        val buffer = editor.idBuffer(id)
        assertEquals(id, buffer.get())
        buffer.set("界".repeat(128))
        assertEquals("界".repeat(128), buffer.get())
        val scene = EditorScene.createDefault()
        val beat = scene.beats.getValue("main")
        beat.cues = listOf(SceneCue("original", 0f))
        assertTrue(editor.applyCue(scene, beat, 0, buffer.get(), 1f))
        assertEquals(buffer.get(), beat.cues.single().id)
        scene.undo()
        assertEquals("original", beat.cues.single().id)
    }

    @Test fun `invalid playheads duplicate identifiers and stale beat references cannot change cues`() {
        val editor = CueInspector()
        val scene = EditorScene.createDefault()
        val beat = scene.beats.getValue("main")
        beat.duration = 3f
        beat.cues = listOf(SceneCue("cue_1", 0f))
        scene.beats["other"] = EditorBeat("other").apply { cues = listOf(SceneCue("other", 0f)) }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(editor.addCue(scene, beat, value))
            assertFalse(editor.applyCue(scene, beat, 0, "valid", value))
        }
        assertFalse(editor.applyCue(scene, beat, 0, "other", 1f))
        assertFalse(editor.applyCue(scene, beat, 0, "x".repeat(129), 1f))
        assertFalse(editor.applyCue(scene, beat, 0, "valid", 4f))
        assertFalse(editor.applyCue(scene, beat, 0, "cue_1", 0f))
        assertFalse(scene.canUndo())
        assertTrue(editor.addCue(scene, beat, 99f))
        assertEquals(SceneCue("cue_2", 3f), beat.cues.last())
        scene.undo()
        scene.beats[beat.id] = EditorBeat(beat.id)
        assertFalse(editor.addCue(scene, beat, 1f))
        assertFalse(editor.applyCue(scene, beat, 0, "changed", 1f))
    }
}
