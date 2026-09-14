/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class TrackedBoneCacheTest {
    @Test
    fun `rendering clears released bones and matrices from a replaced or changed model`() {
        val tracked = mutableMapOf<String, MatrixWrapper>()
        val locators = mutableMapOf<String, MatrixWrapper>()
        var owner: PosableModel? = null
        val state = mockk<PosableState>()
        every { state.trackedBoneStates } returns tracked
        every { state.trackedBoneModel } answers { owner }
        every { state.trackedBoneModel = any() } answers { owner = firstArg() }
        every { state.locatorStates } returns locators
        val entity = mockk<Entity>(relaxed = true)
        every { entity.id } returns 42
        every { entity.position() } returns Vec3.ZERO
        val leaf = mockk<Bone>()
        every { leaf.children } returns emptyMap()
        every { leaf.transform(any()) } just Runs
        val children = mutableMapOf("body" to leaf)
        val first = model(children)
        val second = model(emptyMap())
        BoneTracker.clear()
        try {
            BoneTracker.hold(42, "body")
            first.updateLocators(entity, state)
            assertTrue("body" in tracked)
            assertSame(first, owner)
            BoneTracker.release(42, "body")
            first.updateLocators(entity, state)
            assertTrue(tracked.isEmpty())

            BoneTracker.hold(42, "body")
            first.updateLocators(entity, state)
            second.updateLocators(entity, state)
            assertTrue(tracked.isEmpty())
            assertSame(second, owner)

            first.updateLocators(entity, state)
            assertTrue("body" in tracked)
            children.clear()
            first.updateLocators(entity, state)
            assertTrue(tracked.isEmpty())
        } finally {
            BoneTracker.clear()
        }
    }

    private fun model(children: Map<String, Bone>): PosableModel {
        val root = mockk<Bone>()
        every { root.children } returns children
        every { root.transform(any()) } just Runs
        return PosableModel(root).apply {
            locatorAccess = LocatorAccess(root)
        }
    }
}
