/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.entity.stadium.StadiumCameraDroneEntity
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransform
import com.cobblemon.mod.common.net.messages.client.cinematic.FeedTransformPacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class StadiumCameraDronesTest {
    private val level = mockk<ServerLevel>(relaxed = true)
    private val created = mutableListOf<StadiumCameraDroneEntity>()
    private val drones = StadiumCameraDrones(level) {
        mockk<StadiumCameraDroneEntity>(relaxed = true).also { created.add(it) }
    }

    init { every { level.addFreshEntity(any()) } returns true }

    private fun packet(vararg feeds: FeedTransform) = FeedTransformPacket(UUID.randomUUID(), feeds.toList())

    @Test fun `feeds reuse drones while following camera position and rotation`() {
        drones.update(packet(FeedTransform("primary", Vec3.ZERO, CameraRotation(0f, 0f))))
        drones.update(packet(FeedTransform("primary", Vec3(4.0, 8.0, 12.0), CameraRotation(90f, 30f))))
        assertEquals(1, created.size)
        verify(exactly = 1) { level.addFreshEntity(created.single()) }
        verify { created.single().moveTo(4.0, 8.0, 12.0, 90f, 30f) }
    }

    @Test fun `intro drones disappear when battle changes active feeds and stop removes the remainder`() {
        drones.update(packet(FeedTransform("intro_a", Vec3.ZERO), FeedTransform("intro_b", Vec3.ZERO)))
        val intro = created.toList()
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        intro.forEach { drone -> verify(exactly = 1) { drone.discard() } }
        drones.close()
        drones.close()
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        assertEquals(3, created.size)
        created.forEach { drone -> verify(exactly = 1) { drone.discard() } }
    }

    @Test fun `one removal failure does not prevent remaining drones from closing`() {
        drones.update(packet(FeedTransform("intro_a", Vec3.ZERO), FeedTransform("intro_b", Vec3.ZERO)))
        every { created.first().discard() } throws IllegalStateException("Removal listener failed")
        drones.close()
        drones.close()
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        assertEquals(2, created.size)
        created.forEach { drone -> verify(exactly = 1) { drone.discard() } }
    }

    @Test fun `failed removal of an inactive feed does not prevent the new feed from appearing`() {
        drones.update(packet(FeedTransform("intro_a", Vec3.ZERO), FeedTransform("intro_b", Vec3.ZERO)))
        every { created.first().discard() } throws IllegalStateException("Removal listener failed")
        drones.update(packet(FeedTransform("primary", Vec3(4.0, 8.0, 12.0))))
        assertEquals(3, created.size)
        verify { created.last().moveTo(4.0, 8.0, 12.0, any(), any()) }
        drones.close()
        created.forEach { drone -> verify(exactly = 1) { drone.discard() } }
    }

    @Test fun `closing from a removal callback cannot spawn replacement feeds`() {
        drones.update(packet(FeedTransform("intro_a", Vec3.ZERO), FeedTransform("intro_b", Vec3.ZERO)))
        every { created.first().discard() } answers { drones.close() }
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        assertEquals(2, created.size)
        created.forEach { drone -> verify(exactly = 1) { drone.discard() } }
    }

    @Test fun `closing while the world accepts a drone removes that drone immediately`() {
        every { level.addFreshEntity(any()) } answers { drones.close(); true }
        drones.update(packet(FeedTransform("intro_a", Vec3.ZERO), FeedTransform("intro_b", Vec3.ZERO)))
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        assertEquals(1, created.size)
        verify(exactly = 1) { created.single().discard() }
    }

    @Test fun `removed drones are replaced and empty feed list removes all drones`() {
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        every { created.first().isRemoved } returns true
        drones.update(packet(FeedTransform("primary", Vec3.ZERO)))
        assertEquals(2, created.size)
        drones.update(packet())
        verify { created.last().discard() }
    }
}
