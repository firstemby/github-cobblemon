/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumSetupTest {
    @Test fun `corner selection includes both clicked blocks and keeps the first floor height`() {
        val a = Vec3(-4.5, 64.5, -8.5)
        val b = Vec3(3.5, 68.0, 2.5)
        val area = StadiumSetup.area(a, b)
        assertEquals(9.0, area.width)
        assertEquals(12.0, area.depth)
        assertEquals(-5.0, area.minX)
        assertEquals(3.0, area.maxZ)
        assertEquals(64.5, area.center.y)
        assertEquals(area.copy(center = Vec3(area.center.x, b.y, area.center.z)), StadiumSetup.area(b, a))
    }

    @Test fun `invalid selection leaves existing arena unchanged`() {
        val data = StadiumBlockData.createDefault().apply {
            arena = StadiumArena(Vec3.ZERO, 16.0, 12.0)
            doublesLayout = StadiumBlockLayout.createDefault().apply { side2.actors.add(StadiumBlockPlacement(Vec3(6.0, 0.0, 0.0))) }
        }
        val original = data.toNbt()
        assertNotNull(StadiumSetup.areaError(data, StadiumSetup.area(Vec3.ZERO, Vec3.ZERO), Vec3.ZERO))
        assertNotNull(StadiumSetup.areaError(data, StadiumArena(Vec3.ZERO, 4.0, 4.0), Vec3.ZERO))
        assertNotNull(StadiumSetup.areaError(data, StadiumArena(Vec3(95.0, 0.0, 0.0), 16.0, 16.0), Vec3.ZERO))
        assertEquals(original, data.toNbt())
    }

    @Test fun `guided custom setup resumes the first missing slot through both sides`() {
        val data = StadiumBlockData.createDefault().apply {
            arena = StadiumArena(Vec3.ZERO, 32.0, 32.0)
            setLayout("custom", StadiumBlockLayout.createDefault().apply { trainerSlots = 3; pokemonSlots = 5 })
        }
        val slots = StadiumSetup.slots(data, "custom")
        assertEquals(16, slots.size)
        assertEquals(StadiumSetup.Slot(1, "actors", 0), slots.first())
        assertEquals(StadiumSetup.Slot(2, "pokemon", 4), slots.last())
        slots.forEachIndexed { index, slot ->
            assertEquals(slot, StadiumSetup.firstMissing(StadiumBlockData.fromNbt(data.toNbt()), "custom"))
            val point = Vec3(index - 8.0, 0.0, 0.0)
            assertNull(StadiumSetup.placementError(data, "custom", slot, point, Vec3.ZERO))
            slot.placements(data, "custom")!!.add(StadiumBlockPlacement(point))
        }
        assertNull(StadiumSetup.firstMissing(data, "custom"))
    }

    @Test fun `placement preview rejects outside overlap stale slots and gaps without mutation`() {
        val data = StadiumBlockData.createDefault().apply {
            arena = StadiumArena(Vec3.ZERO, 12.0, 12.0)
            doublesLayout = StadiumBlockLayout.createDefault().apply { side1.actors.add(StadiumBlockPlacement(Vec3.ZERO)) }
        }
        val slot = StadiumSetup.Slot(1, "pokemon", 0)
        val before = data.toNbt()
        assertNotNull(StadiumSetup.placementError(data, "doubles", slot, Vec3(7.0, 0.0, 0.0), Vec3.ZERO))
        assertNotNull(StadiumSetup.placementError(data, "doubles", slot, Vec3(0.1, 0.0, 0.0), Vec3.ZERO))
        assertNotNull(StadiumSetup.placementError(data, "doubles", slot.copy(index = 1), Vec3(1.0, 0.0, 0.0), Vec3.ZERO))
        assertNotNull(StadiumSetup.placementError(data, "doubles", slot.copy(side = 3), Vec3(1.0, 0.0, 0.0), Vec3.ZERO))
        assertNull(StadiumSetup.placementError(data, "doubles", slot, Vec3(0.5, 0.0, 0.0), Vec3.ZERO))
        assertEquals(before, data.toNbt())
    }
}
