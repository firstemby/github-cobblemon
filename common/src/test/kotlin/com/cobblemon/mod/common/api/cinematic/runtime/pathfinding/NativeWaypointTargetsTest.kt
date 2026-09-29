/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeWaypointTargetsTest {
    @Test fun `negative coordinates keep floor semantics`() {
        val target = Vec3(-142.32, 68.0, 403.38)
        val goals = NativeWaypointTargets.candidates(target, 0.7f) { _, _ -> true }
        assertEquals(BlockPos(-143, 68, 403), goals.first())
    }

    @Test fun `edge supported destinations can use an adjacent approach node`() {
        val target = Vec3(-142.32, 68.0, 403.38)
        val goals = NativeWaypointTargets.candidates(target, 0.7f) { start, end ->
            assertEquals(target, end)
            start.x == -141.5
        }
        assertTrue(goals.isNotEmpty())
        assertTrue(goals.all { it.x == -142 })
        assertFalse(BlockPos.containing(target) in goals)
    }

    @Test fun `blocked terminal approaches cannot make a waypoint reachable`() {
        assertTrue(NativeWaypointTargets.candidates(Vec3.ZERO, 0.7f) { _, _ -> false }.isEmpty())
    }

    @Test fun `large actors use native corner anchors`() {
        val goals = NativeWaypointTargets.candidates(Vec3(10.5, 64.0, 10.5), 2f) { _, _ -> true }
        assertEquals(BlockPos(9, 64, 9), goals.first())
    }

    @Test fun `fractional floor height offers both native height encodings`() {
        val goals = NativeWaypointTargets.candidates(Vec3(0.5, 63.5, 0.5), 0.7f) { _, _ -> true }
        assertEquals(setOf(63, 64), goals.map { it.y }.toSet())
        assertTrue(goals.size <= 18)
    }

    @Test fun `detour retries are increasing unique and bounded`() {
        assertEquals(listOf(24, 48, 80), NativeWaypointTargets.searchRanges(4.0))
        assertEquals(listOf(76, 80), NativeWaypointTargets.searchRanges(64.0))
        assertEquals(listOf(80), NativeWaypointTargets.searchRanges(100.0))
    }
}
