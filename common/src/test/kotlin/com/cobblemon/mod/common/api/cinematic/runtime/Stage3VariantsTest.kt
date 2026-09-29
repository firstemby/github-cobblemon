/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ConstraintModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.DollyPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.util.asExpressionLike
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class Stage3VariantsTest {

    private fun scene(shot: Shot) = Scene(
        id = "t",
        startBeatId = "main",
        beats = mapOf("main" to Beat(id = "main")),
        shots = mapOf(shot.id to shot)
    )

    @Test
    fun `OrbitPosition traces a circle around center`() {
        val orbit = OrbitPosition(
            center = Vec3(0.0, 0.0, 0.0),
            radius = 10f,
            angularVelocityDegPerSec = 90f,
            startAngleDeg = 0f
        )
        val p0 = orbit.evaluate(0f)
        assertEquals(10.0, p0.x, 0.01)
        assertEquals(0.0, p0.z, 0.01)
        val p1 = orbit.evaluate(1f)
        assertEquals(0.0, p1.x, 0.01)
        assertEquals(10.0, p1.z, 0.01)
    }

    @Test
    fun `DollyPosition interpolates linearly`() {
        val dolly = DollyPosition(
            from = Vec3(0.0, 0.0, 0.0),
            to = Vec3(10.0, 0.0, 0.0),
            duration = 1f
        )
        assertEquals(0.0, dolly.evaluate(0f).x, 0.01)
        assertEquals(5.0, dolly.evaluate(0.5f).x, 0.01)
        assertEquals(10.0, dolly.evaluate(1f).x, 0.01)
    }

    @Test
    fun `ConstraintModifier clamps position to box`() {
        val shot = Shot(
            id = "s",
            position = FixedPosition(Vec3(100.0, 100.0, 100.0)),
            rotation = FixedRotation(CameraRotation(0f, 0f)),
            modifiers = listOf(ConstraintModifier(maxX = 10.0, maxY = 10.0, maxZ = 10.0))
        )
        val result = ShotEvaluator(scene(shot)).evaluate("s", 0f, null)!!
        assertEquals(10.0, result.position.x, 0.01)
        assertEquals(10.0, result.position.y, 0.01)
        assertEquals(10.0, result.position.z, 0.01)
    }

    @Test
    fun `ExpressionFov evaluates MoLang literal expression`() {
        val fovSource = ExpressionFov(expression = "90".asExpressionLike(), fallbackFov = 70f)
        val runtime = MoLangRuntime()
        assertEquals(90f, fovSource.evaluate(runtime), 0.01f)
    }

    @Test
    fun `ExpressionFov coerces result into valid FOV range`() {
        val fovSource = ExpressionFov(expression = "9999".asExpressionLike(), fallbackFov = 70f)
        val runtime = MoLangRuntime()
        assertEquals(179f, fovSource.evaluate(runtime), 0.01f)
    }

    @Test
    fun `ExpressionFov falls back when runtime is null in ShotEvaluator`() {
        val shot = Shot(
            id = "s",
            position = FixedPosition(Vec3.ZERO),
            rotation = FixedRotation(CameraRotation(0f, 0f)),
            fov = ExpressionFov(expression = "90".asExpressionLike(), fallbackFov = 55f)
        )
        val result = ShotEvaluator(scene(shot)).evaluate("s", 0f, null, null)!!
        val fov = result.fov
        assertNotNull(fov)
        assertEquals(55f, fov!!, 0.01f)
    }

    @Test
    fun `ExpressionFov uses expression result when runtime is provided in ShotEvaluator`() {
        val shot = Shot(
            id = "s",
            position = FixedPosition(Vec3.ZERO),
            rotation = FixedRotation(CameraRotation(0f, 0f)),
            fov = ExpressionFov(expression = "85".asExpressionLike(), fallbackFov = 70f)
        )
        val runtime = MoLangRuntime()
        val result = ShotEvaluator(scene(shot)).evaluate("s", 0f, null, runtime)!!
        val fov = result.fov
        assertNotNull(fov)
        assertEquals(85f, fov!!, 0.01f)
    }
}
