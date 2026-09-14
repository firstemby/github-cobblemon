/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.SplinePathEditor
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.SplineMovement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplineMovementEditorJsonTest {

    private fun newData(): JsonObject = JsonObject().apply {
        addProperty("duration", 5f)
        add("controlPoints", JsonArray().apply {
            add(JsonObject().apply { addProperty("x", 0.0); addProperty("y", 64.0); addProperty("z", 0.0) })
            add(JsonObject().apply { addProperty("x", 10.0); addProperty("y", 64.0); addProperty("z", 0.0) })
        })
    }

    @Test
    fun `appendControlPoint lets engine fromJson accept the shape`() {
        val data = newData()
        SplinePathEditor.appendControlPoint(data, Vec3(5.0, 66.0, 3.0))
        val parsed = SplineMovement.fromJson(data)
        assertEquals(3, parsed.controlPoints.size)
    }

    @Test
    fun `removeControlPoint refuses to drop below 2 points`() {
        val data = newData()
        SplinePathEditor.removeControlPoint(data, 0)
        assertEquals(2, data.getAsJsonArray("controlPoints").size())
    }
}
