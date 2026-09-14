/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SplinePosition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SplinePositionEditorJsonTest {

    @Test
    fun `same frame draft edits accumulate and undo independently`() {
        val scene = EditorScene.createDefault()
        val source = newData()
        val original = source.deepCopy()
        val shot = EditorShot("path", source, JsonObject())
        scene.shots[shot.id] = shot
        val draft = SplinePositionEditor.Draft(scene, shot.id, source)
        draft.commit(draft.data.deepCopy().apply { addProperty("duration", 8f) })
        val first = shot.positionData.deepCopy()
        draft.commit(draft.data.deepCopy().apply {
            getAsJsonArray("controlPoints")[0].asJsonObject.addProperty("x", 3.0)
        })
        assertEquals(8f, shot.positionData.get("duration").asFloat)
        assertEquals(3.0, shot.positionData.getAsJsonArray("controlPoints")[0].asJsonObject.get("x").asDouble)
        assertEquals(original, source)
        assertTrue(scene.undo())
        assertEquals(first, shot.positionData)
        draft.commit(draft.data.deepCopy().apply { addProperty("duration", 12f) })
        assertEquals(first, shot.positionData)
        assertTrue(scene.undo())
        assertEquals(original, shot.positionData)
    }

    private fun newData(): JsonObject = JsonObject().apply {
        addProperty("type", "spline")
        addProperty("duration", 5f)
        add("controlPoints", JsonArray().apply {
            add(JsonObject().apply { addProperty("x", 0.0); addProperty("y", 64.0); addProperty("z", 0.0) })
            add(JsonObject().apply { addProperty("x", 10.0); addProperty("y", 64.0); addProperty("z", 0.0) })
        })
    }

    @Test
    fun `appendControlPoint adds a point and SplinePosition_fromJson accepts it`() {
        val data = newData()
        SplinePositionEditor.appendControlPoint(data, Vec3(5.0, 66.0, 3.0))

        val parsed = SplinePosition.fromJson(data)
        assertEquals(3, parsed.controlPoints.size)
        assertEquals(Vec3(5.0, 66.0, 3.0), parsed.controlPoints[2])
    }

    @Test
    fun `removeControlPoint refuses to drop below 2 points`() {
        val data = newData()
        SplinePositionEditor.removeControlPoint(data, index = 1)
        val arr = data.getAsJsonArray("controlPoints")
        assertEquals(2, arr.size())

        SplinePositionEditor.appendControlPoint(data, Vec3(20.0, 64.0, 0.0))
        SplinePositionEditor.removeControlPoint(data, index = 0)
        assertEquals(2, data.getAsJsonArray("controlPoints").size())
    }
}
