/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyframeMovementEditorJsonTest {

    @Test
    fun `appendKeyframe writes JSON that KeyframeMovement fromJson accepts`() {
        val data = JsonObject().apply { add("keyframes", JsonArray()) }
        KeyframeMovementEditor.appendKeyframe(data, time = 0.5f, pos = Vec3(1.0, 2.0, 3.0))

        val parsed = KeyframeMovement.fromJson(data)
        assertEquals(1, parsed.keyframes.size)
        assertEquals(0.5f, parsed.keyframes[0].time)
        assertEquals(Vec3(1.0, 2.0, 3.0), parsed.keyframes[0].position)
    }

    @Test
    fun `removeKeyframe drops the target row`() {
        val data = JsonObject().apply { add("keyframes", JsonArray()) }
        KeyframeMovementEditor.appendKeyframe(data, 0f, Vec3(0.0, 0.0, 0.0))
        KeyframeMovementEditor.appendKeyframe(data, 1f, Vec3(5.0, 0.0, 0.0))
        KeyframeMovementEditor.appendKeyframe(data, 2f, Vec3(10.0, 0.0, 0.0))

        KeyframeMovementEditor.removeKeyframe(data, 1)

        val parsed = KeyframeMovement.fromJson(data)
        assertEquals(2, parsed.keyframes.size)
        assertEquals(0f, parsed.keyframes[0].time)
        assertEquals(2f, parsed.keyframes[1].time)
    }
}
