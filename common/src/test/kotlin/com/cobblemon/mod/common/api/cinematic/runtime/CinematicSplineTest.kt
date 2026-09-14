/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.google.gson.Gson
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SplinePosition
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.CinematicSpline
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.PathFacing
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.SplineMovement
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CinematicSplineTest {
    @Test
    fun `path settings round trip without persisting the distance cache`() {
        val movement = SplineMovement(listOf(Vec3.ZERO, Vec3(10.0, 2.0, 0.0)), 4f, PathFacing.FLIGHT, 15f)
        val json = Gson().toJsonTree(movement).asJsonObject
        assertFalse(json.has("path"))
        assertEquals(movement, SplineMovement.fromJson(json))
        assertEquals(movement.evaluate(2f), SplineMovement.fromJson(json).evaluate(2f))
        assertThrows(IllegalArgumentException::class.java) { movement.copy(headingOffset = Float.NaN) }
    }

    @Test
    fun `uneven collinear points travel equal distances in equal time`() {
        val movement = SplineMovement(listOf(Vec3.ZERO, Vec3(1.0, 0.0, 0.0), Vec3(20.0, 0.0, 0.0)), 10f)
        for (second in 0..10) assertEquals(second * 2.0, movement.evaluate(second.toFloat()).x, 0.015)
        assertEquals(Vec3.ZERO, movement.evaluate(-1f))
        assertEquals(Vec3(20.0, 0.0, 0.0), movement.evaluate(11f))
    }

    @Test
    fun `curved path has bounded speed variation and matches camera samples`() {
        val points = listOf(Vec3.ZERO, Vec3(1.0, 0.0, 1.0), Vec3(10.0, 3.0, 4.0), Vec3(12.0, 2.0, 10.0))
        val actor = SplineMovement(points, 5f)
        val camera = SplinePosition(points, 5f)
        val expected = actor.pathLength / 500
        for (i in 1..500) {
            val time = i / 100f
            assertEquals(camera.evaluate(time), actor.evaluate(time))
            val distance = actor.evaluate(time).distanceTo(actor.evaluate((i - 1) / 100f))
            assertEquals(expected, distance, expected * 0.06)
        }
    }

    @Test
    fun `duplicate and stationary points have deterministic finite results`() {
        val p = Vec3(2.0, 3.0, 4.0)
        val stationary = SplineMovement(listOf(p, p, p), 1f)
        assertEquals(p, stationary.evaluate(0.5f))
        assertNull(stationary.facingAt(1f))
        val path = CinematicSpline(listOf(Vec3.ZERO, Vec3.ZERO, p, p))
        assertEquals(0.0, p.scale(0.5).distanceTo(path.position(0.5)), 1e-9)
        assertEquals(0.0, p.scale(0.2).distanceTo(path.position(0.2)), 1e-9)
        assertThrows(IllegalArgumentException::class.java) { CinematicSpline(listOf(Vec3(Double.NaN, 0.0, 0.0))) }
    }

    @Test
    fun `facing is stable at both endpoints and supports flight and fixed heading`() {
        val points = listOf(Vec3.ZERO, Vec3(5.0, 5.0, 0.0))
        val ground = SplineMovement(points, 2f)
        assertEquals(-90f, ground.facingAt(0f)!!.first, 0.001f)
        assertEquals(ground.facingAt(0f), ground.facingAt(2f))
        assertEquals(0f, ground.facingAt(1f)!!.second)
        assertEquals(-45f, ground.copy(facing = PathFacing.FLIGHT).facingAt(1f)!!.second, 0.001f)
        assertNull(ground.copy(facing = PathFacing.FIXED).facingAt(1f))
    }
}
