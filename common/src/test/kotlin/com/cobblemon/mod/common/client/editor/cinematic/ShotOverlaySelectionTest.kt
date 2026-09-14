package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.client.editor.cinematic.overlay.ShotOverlaySelection
import com.cobblemon.mod.common.client.editor.model.cinematic.*
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShotOverlaySelectionTest {
    @Test
    fun `beat and cut selections show their shots without leaking unrelated beats`() {
        val scene = EditorScene.createDefault()
        listOf("orbit", "trainer", "unused").forEach { scene.shots[it] = EditorShot(it, JsonObject(), JsonObject()) }
        val beat = scene.beats.getValue("main")
        beat.cuts["left"] = EditorCutTrack(mutableListOf(EditorCutEntry(0f, "orbit"), EditorCutEntry(2f, "trainer")))
        beat.cuts["right"] = EditorCutTrack(mutableListOf(EditorCutEntry(0f, "orbit")))
        scene.beats["other"] = EditorBeat("other")
        val selection = SceneSelectionManager()
        fun ids(showAll: Boolean = false) = ShotOverlaySelection.shots(scene, selection, showAll).map { it.id }

        selection.select(SceneSelection.BeatSelected("main"))
        assertEquals(listOf("orbit", "trainer"), ids())
        selection.select(SceneSelection.CutSelected("main", "left", 1))
        assertEquals(listOf("trainer"), ids())
        selection.select(SceneSelection.ShotSelected("unused"))
        assertEquals(listOf("unused"), ids())
        selection.select(SceneSelection.BeatSelected("other"))
        assertTrue(ids().isEmpty())
        assertEquals(listOf("orbit", "trainer", "unused"), ids(true))
        assertFalse(scene.isDirty)
        assertTrue(scene.beats.getValue("other").cuts.isEmpty())
    }
}
