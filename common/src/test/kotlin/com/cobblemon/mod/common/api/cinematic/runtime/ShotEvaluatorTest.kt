/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SmoothingModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OffsetModifier
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ShotEvaluatorTest {

    @Test
    fun `overflowing modifiers are rejected before querying block collisions`() {
        val shot = Shot("orbit", OrbitPosition(Vec3(Double.MAX_VALUE, 0.0, 0.0), 10f, 30f),
            FixedRotation(CameraRotation(0f, 0f)),
            modifiers = listOf(OffsetModifier(positionOffset = Vec3(Double.MAX_VALUE, 0.0, 0.0))))
        val evaluator = ShotEvaluator(sceneWithShot(shot), CameraBlockCollision { error("Invalid position reached collision query") })
        assertNull(evaluator.evaluate("orbit", 0f))
    }

    @Test
    fun `invalid sample times leave previous smoothing state intact`() {
        val shot = Shot("orbit", OrbitPosition(Vec3.ZERO, 10f, 30f, avoidBlocks = false, rememberProgress = true),
            FixedRotation(CameraRotation(0f, 0f)), modifiers = listOf(SmoothingModifier(positionFactor = 0.5f)))
        val scene = sceneWithShot(shot)
        val evaluator = ShotEvaluator(scene)
        val uninterrupted = ShotEvaluator(scene)
        assertEquals(uninterrupted.evaluate("orbit", 0f), evaluator.evaluate("orbit", 0f))
        for (time in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(evaluator.evaluate("orbit", time))
            assertNull(evaluator.evaluate("orbit", 1f, orbitTime = time))
        }
        assertEquals(uninterrupted.evaluate("orbit", 1f), evaluator.evaluate("orbit", 1f))
    }

    @Test
    fun `modifier output outside the camera FOV range is dropped`() {
        val shot = Shot("s", FixedPosition(Vec3.ZERO), FixedRotation(CameraRotation(0f, 0f)),
            fov = FixedFov(170f), modifiers = listOf(OffsetModifier(fovOffset = 10f)))
        assertNull(ShotEvaluator(sceneWithShot(shot)).evaluate("s", 0f))
    }

    @Test
    fun `arena orbit smoothing crosses the eight second yaw seam without spinning`() {
        val center = Vec3(10.0, 64.0, 20.0)
        for (direction in listOf(-1f, 1f)) {
            for (avoidBlocks in listOf(false, true)) {
                val shot = Shot("arena_orbit",
                    OrbitPosition(center, 14f, direction * 22.5f, startAngleDeg = direction * -90f,
                        height = 6f, avoidBlocks = avoidBlocks),
                    LookAt(LookAtTarget.Position(center)),
                    modifiers = listOf(SmoothingModifier(positionFactor = 0.75f, rotationFactor = 0.75f)))
                val evaluator = ShotEvaluator(sceneWithShot(shot), CameraBlockCollision { emptyList() })
                var previous = evaluator.evaluate(shot.id, 0f)!!.rotation!!.yaw
                for (frame in 1..360) {
                    val time = frame / 20f
                    val yaw = evaluator.evaluate(shot.id, time)!!.rotation!!.yaw
                    val turn = Mth.wrapDegrees(yaw - previous)
                    assertTrue(abs(turn) < 2f,
                        "Sudden turn of $turn degrees at ${time}s (direction=$direction, avoidBlocks=$avoidBlocks)")
                    previous = yaw
                }
            }
        }
    }

    @Test
    fun `fixed rotation stays fixed throughout a smoothed orbit with block avoidance`() {
        val rotation = CameraRotation(-179f, 25f)
        val shot = Shot("arena_orbit", OrbitPosition(Vec3.ZERO, 14f, 22.5f, startAngleDeg = -90f,
            height = 6f, avoidanceSmoothing = 2f), FixedRotation(rotation),
            modifiers = listOf(SmoothingModifier(positionFactor = 0.75f, rotationFactor = 0.75f)))
        val wall = AABB(4.0, 0.0, -4.0, 5.0, 10.0, 4.0)
        val collision = CameraBlockCollision { query -> listOf(wall).filter { it.intersects(query) } }
        val evaluator = ShotEvaluator(sceneWithShot(shot), collision)
        val start = evaluator.evaluate(shot.id, 0f)!!.position
        for (frame in 1..340) {
            val result = evaluator.evaluate(shot.id, frame / 20f)!!
            assertEquals(rotation, result.rotation)
            assertTrue(collision.clear(result.position))
            if (frame == 160) assertTrue(start.distanceTo(result.position) > 1.0)
        }
    }

    private fun sceneWithShot(shot: Shot): Scene = Scene(
        id = "t",
        startBeatId = "main",
        beats = mapOf("main" to Beat(id = "main")),
        shots = mapOf(shot.id to shot)
    )

    @Test
    fun `FixedPosition emits the declared position`() {
        val shot = Shot(
            id = "s",
            position = FixedPosition(Vec3(1.0, 2.0, 3.0)),
            rotation = FixedRotation(CameraRotation(45f, -10f))
        )
        val evaluator = ShotEvaluator(sceneWithShot(shot))
        val result = evaluator.evaluate("s", 0f)!!
        assertEquals(Vec3(1.0, 2.0, 3.0), result.position)
        assertEquals(45f, result.rotation!!.yaw)
        assertEquals(-10f, result.rotation.pitch)
    }

    @Test
    fun `KeyframePosition interpolates linearly between keyframes`() {
        val shot = Shot(
            id = "s",
            position = KeyframePosition(listOf(
                KeyframePosition.Kf(0f, Vec3(0.0, 0.0, 0.0)),
                KeyframePosition.Kf(1f, Vec3(10.0, 0.0, 0.0))
            )),
            rotation = FixedRotation(CameraRotation(0f, 0f))
        )
        val evaluator = ShotEvaluator(sceneWithShot(shot))
        val mid = evaluator.evaluate("s", 0.5f)!!
        assertTrue(abs(mid.position.x - 5.0) < 0.01, "Expected ~5.0, got ${mid.position.x}")
    }

    @Test
    fun `LookAt with Position target produces rotation pointing at target`() {
        val shot = Shot(
            id = "s",
            position = FixedPosition(Vec3.ZERO),
            rotation = LookAt(target = LookAtTarget.Position(Vec3(0.0, 0.0, 10.0)))
        )
        val evaluator = ShotEvaluator(sceneWithShot(shot))
        val result = evaluator.evaluate("s", 0f, actorDirector = null)!!
        assertNotNull(result.rotation)
        assertTrue(abs(result.rotation!!.yaw) < 1f, "Expected yaw ~0°, got ${result.rotation.yaw}")
    }

    @Test
    fun `FixedFov passes through to transform`() {
        val shot = Shot(
            id = "s",
            position = FixedPosition(Vec3.ZERO),
            rotation = FixedRotation(CameraRotation(0f, 0f)),
            fov = FixedFov(70f)
        )
        val evaluator = ShotEvaluator(sceneWithShot(shot))
        assertEquals(70f, evaluator.evaluate("s", 0f)!!.fov)
    }

    @Test
    fun `SmoothingModifier drives output toward target over successive ticks`() {
        val keyframes = KeyframePosition(listOf(
            KeyframePosition.Kf(0f, Vec3(0.0, 0.0, 0.0)),
            KeyframePosition.Kf(0.05f, Vec3(100.0, 0.0, 0.0))
        ))
        val shot = Shot(
            id = "s",
            position = keyframes,
            rotation = FixedRotation(CameraRotation(0f, 0f)),
            modifiers = listOf(SmoothingModifier(positionFactor = 0.5f))
        )
        val evaluator = ShotEvaluator(sceneWithShot(shot))
        val t0 = evaluator.evaluate("s", 0f)!!
        assertTrue(abs(t0.position.x - 0.0) < 0.01)
        val t1 = evaluator.evaluate("s", 0.05f)!!
        assertTrue(
            abs(t1.position.x - 50.0) < 0.01,
            "Expected smoothed ~50, got ${t1.position.x}"
        )
    }

    @Test
    fun `smoothing cadence is stable and a backward seek starts fresh`() {
        fun shot(position: Vec3) = Shot("s", FixedPosition(position), FixedRotation(CameraRotation(0f, 0f)),
            modifiers = listOf(SmoothingModifier(positionFactor = 0.5f)))
        fun sample(step: Float): ShotEvaluator {
            val shots = mutableMapOf("s" to shot(Vec3.ZERO))
            val scene = Scene("t", shots = shots, startBeatId = "main", beats = mapOf("main" to Beat("main")))
            val evaluator = ShotEvaluator(scene)
            evaluator.evaluate("s", 0f)
            shots["s"] = shot(Vec3(100.0, 0.0, 0.0))
            var time = step
            while (time < 1f) {
                evaluator.evaluate("s", time)
                time += step
            }
            return evaluator
        }
        val twenty = sample(0.05f)
        val atTwenty = twenty.evaluate("s", 1f)!!
        val atForty = sample(0.025f).evaluate("s", 1f)!!
        assertEquals(atTwenty.position.x, atForty.position.x, 0.0001)
        assertEquals(Vec3(100.0, 0.0, 0.0), twenty.evaluate("s", 0f)!!.position)
    }
}
