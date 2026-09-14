/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class StarBoneTrackerTest {
    @Test
    fun `moving and rotating the whole entity does not qualify an unanimated bone`() {
        val locators = mutableMapOf<String, MatrixWrapper>()
        val state = state(locators)
        val tracker = StarBoneTracker(emptyMap())
        repeat(20) { frame ->
            val world = Vec3(frame.toDouble(), 0.0, 0.0)
            val root = Matrix4f().rotateY(frame * 0.15f)
            locators["root"] = MatrixWrapper().updateMatrix(root).updatePosition(world)
            locators["middle"] = MatrixWrapper().updateMatrix(Matrix4f(root).translate(0f, 1f, 0f)).updatePosition(world)
            locators["body"] = MatrixWrapper().updateMatrix(Matrix4f(root).translate(0.2f, 1f, 0f)).updatePosition(world)
            assertNull(tracker.sample(state))
        }
    }

    @Test
    fun `animated central bones qualify and stay selected for the current model`() {
        val locators = mutableMapOf("root" to MatrixWrapper(), "middle" to MatrixWrapper())
        val state = state(locators)
        val tracker = StarBoneTracker(mapOf("body" to 5))
        repeat(6) { frame ->
            locators["body"] = MatrixWrapper().updatePosition(Vec3(frame * 0.02, 0.0, 0.0))
            locators["tail"] = MatrixWrapper().updatePosition(Vec3(frame * 0.5, 0.0, 0.0))
            tracker.sample(state)
        }
        assertEquals("body", tracker.starBone)
        locators["body"] = MatrixWrapper().updatePosition(Vec3(100.0, 0.0, 0.0))
        assertEquals("body", tracker.sample(state))
    }

    @Test
    fun `separate cameras and models do not inherit another trackers selection`() {
        val first = StarBoneTracker(emptyMap())
        val second = StarBoneTracker(emptyMap())
        val firstLocators = mutableMapOf("root" to MatrixWrapper(), "middle" to MatrixWrapper())
        val secondLocators = mutableMapOf("root" to MatrixWrapper(), "middle" to MatrixWrapper())
        val firstState = state(firstLocators)
        val secondState = state(secondLocators)
        repeat(6) { frame ->
            firstLocators["body"] = MatrixWrapper().updatePosition(Vec3(frame * 0.02, 0.0, 0.0))
            secondLocators["head"] = MatrixWrapper().updatePosition(Vec3(frame * 0.02, 0.0, 0.0))
            first.sample(firstState)
            second.sample(secondState)
        }
        assertEquals("body", first.starBone)
        assertEquals("head", second.starBone)
    }

    private fun state(locators: MutableMap<String, MatrixWrapper>) = mockk<PosableState>().also {
        every { it.currentModel } returns null
        every { it.locatorStates } returns locators
    }
}
