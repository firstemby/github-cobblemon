/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SmoothingModifier
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class ShotBoneHintTest {
    @Test
    fun `smoothing retains bone hints after the bound target is removed`() {
        val offset = Vec3(0.0, 0.5, 0.0)
        val shot = Shot("focus", FixedPosition(Vec3.ZERO), LookAt(LookAtTarget.ActorBone("target", "head", offset)),
            modifiers = listOf(SmoothingModifier(positionFactor = 0.5f, rotationFactor = 0.5f)))
        val scene = Scene("test", shots = mapOf(shot.id to shot), startBeatId = "main", beats = mapOf("main" to Beat("main")))
        val director = mockk<ActorDirector>()
        val entity = mockk<Entity>()
        var alive = true
        every { entity.id } returns 42
        every { entity.isAlive } answers { alive }
        every { entity.isRemoved } answers { !alive }
        every { director.resolveEntityPublic("target") } returns entity
        every { director.cameraBounds("target") } returns AABB(0.0, 0.0, 10.0, 1.0, 2.0, 11.0)
        val evaluator = ShotEvaluator(scene)
        val expected = BoneHint(42, "head", offset, true)
        assertEquals(expected, evaluator.evaluate(shot.id, 0f, director)?.boneHint)
        assertEquals(expected, evaluator.evaluate(shot.id, 0.05f, director)?.boneHint)
        alive = false
        assertEquals(expected, evaluator.evaluate(shot.id, 0.1f, director)?.boneHint)
    }
}
