/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrbitPositionEditorJsonTest {

    @Test
    fun `default factory round-trips through engine fromJson`() {
        val data = ShotSourceTypes.entryFor(SourceChannel.POSITION, "orbit")!!.defaultJson()
        val parsed = OrbitPosition.fromJson(data)
        assertEquals(10f, parsed.radius)
        assertEquals(30f, parsed.angularVelocityDegPerSec)
    }

    @Test
    fun `radius edits reject nonfinite input and clamp negative input without losing undo`() {
        val data = ShotSourceTypes.entryFor(SourceChannel.POSITION, "orbit")!!.defaultJson()
        val scene = EditorScene().apply { shots["orbit"] = EditorShot("orbit", data, JsonObject()) }
        val editor = OrbitPositionEditor()
        for (radius in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(editor.applyRadius(scene, "orbit", data, radius))
        }
        assertFalse(scene.canUndo())
        assertTrue(editor.applyRadius(scene, "orbit", data, -5f))
        assertEquals(0.01f, scene.shots.getValue("orbit").positionData.get("radius").asFloat)
        assertEquals(10f, data.get("radius").asFloat)
        assertTrue(scene.undo())
        assertEquals(data, scene.shots.getValue("orbit").positionData)
    }
}
