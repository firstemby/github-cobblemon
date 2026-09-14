/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import net.minecraft.world.phys.Vec3
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LookAtTargetEditorSwapTest {

    @Test
    fun `expression targets do not populate numeric position buffers`() {
        val target = LookAtTargetEditor.buildDefaultTarget(LookAtKind.EXPRESSION).apply {
            addProperty("x", "v.arena_center_x")
            addProperty("y", "v.arena_center_y + 1.5")
            addProperty("z", "v.arena_center_z")
        }
        val before = target.deepCopy()
        assertEquals(Vec3.ZERO, LookAtTargetEditor.positionForBuffers(target))
        assertEquals(before, target)
        assertTrue(LookAt.fromJson(wrap(target)).target is LookAtTarget.Expression)
        val position = LookAtTargetEditor.buildDefaultTarget(LookAtKind.POSITION)
        assertEquals(Vec3(0.0, 64.0, 0.0), LookAtTargetEditor.positionForBuffers(position))
    }

    @Test
    fun `buildDefaultTarget returns a JsonObject the engine can deserialize - position`() {
        val target = LookAtTargetEditor.buildDefaultTarget(LookAtKind.POSITION)
        val full = wrap(target)
        val parsed = LookAt.fromJson(full)
        assertTrue(parsed.target is LookAtTarget.Position)
    }

    @Test
    fun `buildDefaultTarget returns a JsonObject the engine can deserialize - actor_bone`() {
        val target = LookAtTargetEditor.buildDefaultTarget(LookAtKind.ACTOR_BONE)
        val full = wrap(target)
        val parsed = LookAt.fromJson(full)
        assertTrue(parsed.target is LookAtTarget.ActorBone)
    }

    @Test
    fun `buildDefaultTarget returns a JsonObject the engine can deserialize - actor`() {
        val parsed = LookAt.fromJson(wrap(LookAtTargetEditor.buildDefaultTarget(LookAtKind.ACTOR)))
        assertTrue(parsed.target is LookAtTarget.Actor)
    }

    @Test
    fun `hasUserData flags populated targets`() {
        val populated = JsonObject().apply {
            addProperty("type", "position")
            addProperty("x", 10.0); addProperty("y", 0.0); addProperty("z", 0.0)
        }
        assertTrue(LookAtTargetEditor.hasUserData(populated))

        for (kind in LookAtKind.entries) {
            val empty = LookAtTargetEditor.buildDefaultTarget(kind)
            assertEquals(false, LookAtTargetEditor.hasUserData(empty), kind.name)
        }
    }

    private fun wrap(targetObj: JsonObject): JsonObject = JsonObject().apply {
        addProperty("type", "look_at")
        add("target", targetObj)
    }
}
