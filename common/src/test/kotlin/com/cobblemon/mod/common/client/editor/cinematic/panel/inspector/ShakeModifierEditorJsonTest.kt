/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeModifier
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShakeModifierEditorJsonTest {

    @Test
    fun `field edits compose into valid engine JSON and retain the original for undo`() {
        val data = JsonObject().apply {
            addProperty("type", "shake")
            addProperty("positionAmplitude", 0f); addProperty("rotationAmplitude", 0f)
            addProperty("frequency", 8f); addProperty("decay", 0f)
        }
        val before = data.deepCopy()
        val scene = scene(data)
        val editor = ShakeModifierEditor()
        for ((key, value) in mapOf("positionAmplitude" to 0.2f, "rotationAmplitude" to 3f, "frequency" to 12f, "decay" to 1.5f)) {
            assertTrue(editor.applyField(scene, "shot", 0, data, key, value))
        }

        val parsed = ShakeModifier.fromJson(scene.shots.getValue("shot").modifiers[0])
        assertEquals(0.2f, parsed.positionAmplitude)
        assertEquals(3f, parsed.rotationAmplitudeDeg)
        assertEquals(12f, parsed.frequencyHz)
        assertEquals(1.5f, parsed.decaySecs)
        assertEquals(before, data)
        repeat(4) { assertTrue(scene.undo()) }
        assertEquals(before, scene.shots.getValue("shot").modifiers[0])
    }

    @Test
    fun `invalid shake values do not enter history and bounded edits preserve untouched precision`() {
        val data = JsonObject().apply {
            addProperty("type", "shake")
            addProperty("positionAmplitude", 0.123456789)
            addProperty("frequency", 8)
        }
        val scene = scene(data)
        val editor = ShakeModifierEditor()
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(editor.applyField(scene, "shot", 0, data, "frequency", invalid))
        }
        assertFalse(scene.canUndo())
        assertTrue(editor.applyField(scene, "shot", 0, data, "frequency", 100f))
        val edited = scene.shots.getValue("shot").modifiers[0]
        assertEquals(60f, edited.get("frequency").asFloat)
        assertEquals(0.123456789, edited.get("positionAmplitude").asDouble)
        assertTrue(scene.undo())
        assertEquals(data, scene.shots.getValue("shot").modifiers[0])
    }

    private fun scene(modifier: JsonObject) = EditorScene().apply {
        shots["shot"] = EditorShot("shot", JsonObject(), JsonObject()).apply { modifiers.add(modifier) }
    }
}
