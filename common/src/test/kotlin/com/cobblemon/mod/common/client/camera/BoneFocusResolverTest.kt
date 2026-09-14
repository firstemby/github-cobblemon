/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.client.render.models.blockbench.BoneTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class BoneFocusResolverTest {
    private val level = mockk<ClientLevel>()
    private val pokemon = mockk<PokemonEntity>()
    private val state = mockk<PokemonClientDelegate>()
    private var model: PosableModel? = model()
    private var trackedModel = model
    private val bones = mutableMapOf("body" to MatrixWrapper())
    private val locators = mutableMapOf("middle" to MatrixWrapper().updatePosition(Vec3(0.0, 1.0, 10.0)))
    private val resolvers = mutableListOf<BoneFocusResolver>()

    @BeforeEach
    fun setup() {
        BoneTracker.clear()
        val client = mockk<Minecraft>()
        client.level = level
        mockkStatic(Minecraft::class)
        every { Minecraft.getInstance() } returns client
        every { level.getEntity(42) } returns pokemon
        every { pokemon.isAlive } returns true
        every { pokemon.isRemoved } returns false
        every { pokemon.delegate } returns state
        every { state.currentModel } answers { model }
        every { state.trackedBoneModel } answers { trackedModel }
        every { state.trackedBoneStates } returns bones
        every { state.locatorStates } returns locators
    }

    @AfterEach
    fun cleanup() {
        resolvers.forEach { it.close() }
        BoneTracker.clear()
        unmockkStatic(Minecraft::class)
    }

    @Test
    fun `repeated frames hold once and stopping one camera preserves another holder`() {
        val first = resolver()
        val second = resolver()
        repeat(30) {
            first.resolve(Vec3.ZERO, false, null, 0.016f)
            second.resolve(Vec3.ZERO, false, null, 0.016f)
        }
        first.close()
        first.close()
        assertTrue(BoneTracker.isTracked(42, "body"))
        second.close()
        assertFalse(BoneTracker.isTracked(42, "body"))
        first.resolve(Vec3.ZERO, false, null, 0.016f)
        assertFalse(BoneTracker.isTracked(42, "body"))
    }

    @Test
    fun `missing bones do not accumulate holds while a locator is used as fallback`() {
        val resolver = resolver("missing")
        repeat(30) {
            assertEquals(Vec3(0.0, 1.0, 10.0), resolver.resolve(Vec3.ZERO, false, null, 0.016f))
        }
        resolver.close()
        assertTrue(BoneTracker.getTrackedBones(42).isEmpty())
    }

    @Test
    fun `cameras sharing a bone retain independent offsets and smoothing`() {
        val first = resolver()
        val second = resolver()
        val offset = Vec3(100.0, 0.0, 0.0)
        first.resolve(Vec3.ZERO, true, null, 0f)
        second.resolve(offset, true, null, 0f)
        bones.getValue("body").updatePosition(Vec3(10.0, 0.0, 0.0))
        val left = requireNotNull(first.resolve(Vec3.ZERO, true, null, 0.1f))
        val right = requireNotNull(second.resolve(offset, true, null, 0.1f))
        assertEquals(100.0, right.x - left.x, 0.00001)
        assertTrue(left.x in 0.0..10.0)
    }

    @Test
    fun `smoothing a stationary target is independent of frame subdivision`() {
        val results = listOf(1, 15, 30, 72).map { frames ->
            bones.getValue("body").updatePosition(Vec3.ZERO)
            val resolver = resolver()
            resolver.resolve(Vec3.ZERO, true, null, 0f)
            bones.getValue("body").updatePosition(Vec3(10.0, 0.0, 0.0))
            var result = Vec3.ZERO
            repeat(frames) { result = requireNotNull(resolver.resolve(Vec3.ZERO, true, null, 0.5f / frames)) }
            resolver.close()
            result.x
        }
        results.forEach { assertEquals(results.first(), it, 0.00001) }
    }

    @Test
    fun `removed or missing entities retain their last target and release bone tracking`() {
        val resolver = resolver()
        bones.getValue("body").updatePosition(Vec3(3.0, 4.0, 5.0))
        val last = resolver.resolve(Vec3.ZERO, false, null, 0.1f)
        every { pokemon.isAlive } returns false
        assertEquals(last, resolver.resolve(Vec3.ZERO, false, Vec3(1000.0, 0.0, 0.0), 0.1f))
        assertFalse(BoneTracker.isTracked(42, "body"))
        every { level.getEntity(42) } returns null
        assertEquals(last, resolver.resolve(Vec3.ZERO, true, null, 0.1f))
    }

    @Test
    fun `matrices from the previous model are ignored until the new model renders`() {
        val resolver = resolver()
        resolver.resolve(Vec3.ZERO, false, null, 0.1f)
        bones.getValue("body").updatePosition(Vec3(1000.0, 0.0, 0.0))
        model = model()
        assertEquals(Vec3(0.0, 1.0, 10.0), resolver.resolve(Vec3.ZERO, false, null, 0.1f))
        trackedModel = model
        bones.getValue("body").updatePosition(Vec3(2.0, 3.0, 4.0))
        assertEquals(Vec3(2.0, 3.0, 4.0), resolver.resolve(Vec3.ZERO, false, null, 0.1f))
    }

    @Test
    fun `star discovery retries when a model becomes available and releases all owned bones`() {
        model = null
        val first = resolver("*")
        first.resolve(Vec3.ZERO, false, null, 0.1f)
        assertTrue(BoneTracker.getTrackedBones(42).isEmpty())
        model = model()
        trackedModel = model
        val second = resolver("*")
        first.resolve(Vec3.ZERO, false, null, 0.1f)
        second.resolve(Vec3.ZERO, false, null, 0.1f)
        assertEquals(setOf("body", "head"), BoneTracker.getTrackedBones(42))
        first.close()
        assertEquals(setOf("body", "head"), BoneTracker.getTrackedBones(42))
        second.close()
        assertTrue(BoneTracker.getTrackedBones(42).isEmpty())
    }

    private fun resolver(name: String = "body") = BoneFocusResolver(42, name).also(resolvers::add)

    private fun model(): PosableModel {
        val root = mockk<Bone>()
        val leaf = mockk<Bone>()
        every { leaf.children } returns emptyMap()
        every { root.children } returns mapOf("body" to leaf, "head" to leaf)
        return mockk<PosableModel>().also { every { it.rootPart } returns root }
    }
}
