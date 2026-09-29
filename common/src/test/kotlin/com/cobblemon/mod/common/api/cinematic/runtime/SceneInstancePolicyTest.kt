/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.api.cinematic.model.Scene
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SceneInstancePolicyTest {

    private fun scene(policy: DisconnectPolicy) = Scene(
        id = "policy.test",
        startBeatId = "b",
        beats = linkedMapOf("b" to Beat(id = "b")),
        disconnectPolicy = policy
    )

    @Test
    fun `HIDE_ACTOR keeps scene active after a bystander disconnect`() {
        val s = SceneInstance(
            id = UUID.randomUUID(),
            scene = scene(DisconnectPolicy.HIDE_ACTOR),
            origin = Vec3.ZERO,
            actorBindings = emptyMap()
        )
        s.start()
        s.onPlayerDisconnect(UUID.randomUUID())
        assertTrue(s.isActive())
    }

    @Test
    fun `END_SCENE with no bound takeover player is a safe no-op`() {
        val s = SceneInstance(
            id = UUID.randomUUID(),
            scene = scene(DisconnectPolicy.END_SCENE),
            origin = Vec3.ZERO,
            actorBindings = emptyMap()
        )
        s.start()
        assertTrue(s.isActive())
        s.onPlayerDisconnect(UUID.randomUUID())
        assertTrue(s.isActive())
    }
}
