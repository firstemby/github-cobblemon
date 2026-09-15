/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic

import com.cobblemon.mod.common.api.cinematic.runtime.ActorFraming
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActorFramingTest {
    @Test fun `arena orbit follows rectangular dimensions without expanding to the actor bounding sphere`() {
        val center = Vec3(100.0, 64.0, -200.0)
        val orbit = OrbitPosition(center, 4f, 90f, height = 6f, padding = 2f,
            fitArena = true, arenaWidth = 16f, arenaDepth = 8f)
        val oversizedActors = AABB(-20.0, 0.0, -20.0, 20.0, 20.0, 20.0)
        assertEquals(center.add(10.0, 6.0, 0.0), orbit.evaluate(0f, oversizedActors))
        val quarter = orbit.evaluate(1f, oversizedActors)
        assertEquals(center.x, quarter.x, 0.00001)
        assertEquals(center.z + 6.0, quarter.z, 0.00001)
        assertEquals(center, orbit.focus(oversizedActors))
        assertTrue(orbit.copy(fitArena = false).evaluate(0f, oversizedActors).y != center.y + 6.0)
        assertThrows(IllegalArgumentException::class.java) { orbit.copy(arenaWidth = Float.NaN) }
    }

    @Test fun `dynamic actor groups include future slots without including event aliases twice`() {
        val available = buildSet {
            add("user"); add("target")
            repeat(2) { side -> repeat(5) { slot ->
                add("trainer_${side + 1}_${slot + 1}")
                add("pokemon_${side + 1}${'a' + slot}")
            } }
        }
        val ids = ActorFraming.expand(
            listOf("@trainers", "@pokemon", "pokemon_1a"), available)
        assertEquals(20, ids.size)
        assertFalse("user" in ids)
        assertTrue("pokemon_2e" in ids)
    }
    @Test fun `actor bearing rotates the composition with the stadium`() {
        val bounds = AABB(-0.5, 0.0, -0.5, 0.5, 2.0, 0.5)
        val orbit = OrbitPosition(Vec3.ZERO, 9f, 0f, actorIds = listOf("trainer_1"), bearingActorId = "trainer_2")
        val east = orbit.evaluate(0f, bounds, bearingPosition = Vec3(16.0, 1.0, 0.0))
        val south = orbit.evaluate(0f, bounds, bearingPosition = Vec3(0.0, 1.0, 16.0))
        assertEquals(9.0, east.x, 0.00001)
        assertEquals(0.0, south.x, 0.00001)
        assertEquals(9.0, south.z, 0.00001)
        assertEquals(orbit.evaluate(0f, bounds), orbit.evaluate(0f, bounds, bearingPosition = bounds.center))
        val offset = Vec3(100.0, 20.0, -40.0)
        assertEquals(east.add(offset), orbit.evaluate(0f, bounds.move(offset), bearingPosition = Vec3(16.0, 1.0, 0.0).add(offset)))
    }

    @Test fun `fixed orbit retains its original behavior`() {
        val orbit = OrbitPosition(Vec3(10.0, 20.0, 30.0), 4f, 90f, height = 2f)
        assertEquals(Vec3(14.0, 22.0, 30.0), orbit.evaluate(0f))
        assertEquals(34.0, orbit.evaluate(1f).z, 0.00001)
    }

    @Test fun `larger actors and wider formations increase camera distance`() {
        val orbit = OrbitPosition(Vec3.ZERO, 2f, 0f, actorIds = listOf("a", "b"))
        val small = AABB(-0.5, 0.0, -0.5, 0.5, 1.0, 0.5)
        val big = AABB(-3.0, 0.0, -3.0, 3.0, 6.0, 3.0)
        assertTrue(orbit.evaluate(0f, big).x > orbit.evaluate(0f, small).x)
        assertTrue(orbit.evaluate(0f, small.minmax(small.move(12.0, 0.0, 0.0))).x > orbit.evaluate(0f, small).x)
        val shifted = small.move(100.0, 20.0, -40.0)
        assertEquals(orbit.evaluate(0f, small).add(100.0, 20.0, -40.0), orbit.evaluate(0f, shifted))
    }

    @Test fun `padding and narrower field of view pull the camera back`() {
        val bounds = AABB(-2.0, 0.0, -2.0, 2.0, 4.0, 2.0)
        val orbit = OrbitPosition(Vec3.ZERO, 1f, 0f)
        assertTrue(orbit.copy(padding = 8f).evaluate(0f, bounds).x > orbit.evaluate(0f, bounds).x)
        assertTrue(orbit.copy(framingFov = 30f).evaluate(0f, bounds).x > orbit.evaluate(0f, bounds).x)
        assertThrows(IllegalArgumentException::class.java) { orbit.copy(padding = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { orbit.copy(framingFov = 0f) }
    }
}
