/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.entity.generic.GenericBedrockEntity
import com.cobblemon.mod.common.entity.stadium.StadiumCameraDroneEntity
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.api.net.serializers.IdentifierDataSerializer
import com.cobblemon.mod.common.api.net.serializers.PoseTypeDataSerializer
import com.cobblemon.mod.common.api.net.serializers.StringSetDataSerializer
import io.mockk.mockk
import io.mockk.every
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.network.syncher.EntityDataSerializers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class StadiumCameraDroneEntityTest {
    @Test fun `stadium drone has its own type and leaves generic entity behavior unchanged`() {
        listOf(IdentifierDataSerializer, PoseTypeDataSerializer, StringSetDataSerializer).forEach {
            if (EntityDataSerializers.getSerializedId(it) == -1) EntityDataSerializers.registerSerializer(it)
        }
        val level = mockk<ServerLevel>(relaxed = true)
        val droneType = mockk<EntityType<StadiumCameraDroneEntity>>(relaxed = true)
        val genericType = mockk<EntityType<GenericBedrockEntity>>(relaxed = true)
        every { droneType.dimensions } returns EntityDimensions.scalable(1f, 1f)
        every { genericType.dimensions } returns EntityDimensions.scalable(1f, 1f)
        val drone = StadiumCameraDroneEntity(droneType, level)
        val generic = GenericBedrockEntity(level, genericType)
        assertSame(droneType, drone.type)
        assertSame(genericType, generic.type)
        assertFalse(drone.isPickable)
        assertFalse(drone.canBeCollidedWith())
        assertTrue(drone.isNoGravity)
        assertFalse(drone.shouldBeSaved())
        assertTrue(generic.isPickable)
        assertTrue(generic.canBeCollidedWith())
        assertEquals("cobblemon:generic", generic.category.toString())
    }
}
