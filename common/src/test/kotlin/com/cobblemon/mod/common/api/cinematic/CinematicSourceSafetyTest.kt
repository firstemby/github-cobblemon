package com.cobblemon.mod.common.api.cinematic

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionVec3Position
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.CranePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.DollyPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FollowActorPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAlongPath
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.TruckPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.VariableVec3Position
import com.cobblemon.mod.common.util.asExpressionLike
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CinematicSourceSafetyTest {
    @Test fun `look-at targets recover after nonfinite dynamic coordinates`() {
        val runtime = MoLangRuntime()
        val fallback = Vec3(1.0, 2.0, 3.0)
        val variable = LookAtTarget.Variable("arena", fallback)
        val expression = LookAtTarget.Expression("v.arena_x".asExpressionLike(), "v.arena_y".asExpressionLike(),
            "v.arena_z".asExpressionLike(), fallback)
        assertEquals(fallback, variable.evaluate(null))
        assertEquals(fallback, variable.evaluate(runtime))
        assertEquals(fallback, expression.evaluate(null))
        val variables = runtime.environment.variable.map
        variables["arena_y"] = DoubleValue(64.0)
        variables["arena_z"] = DoubleValue(20.0)
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            variables["arena_x"] = DoubleValue(value)
            assertEquals(fallback, variable.evaluate(runtime))
            assertEquals(fallback, expression.evaluate(runtime))
        }
        variables["arena_x"] = DoubleValue(10.0)
        assertEquals(Vec3(10.0, 64.0, 20.0), variable.evaluate(runtime))
        assertEquals(Vec3(10.0, 64.0, 20.0), expression.evaluate(runtime))
    }

    @Test fun `look-at fixed positions offsets and fallbacks reject nonfinite coordinates`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val vector = Vec3(value, 0.0, 0.0)
            assertThrows(IllegalArgumentException::class.java) { LookAtTarget.Position(vector) }
            assertThrows(IllegalArgumentException::class.java) { LookAtTarget.ActorBone("actor", "head", vector) }
            assertThrows(IllegalArgumentException::class.java) { LookAtTarget.Variable("arena", vector) }
            assertThrows(IllegalArgumentException::class.java) {
                LookAtTarget.Expression("0".asExpressionLike(), "0".asExpressionLike(), "0".asExpressionLike(), vector)
            }
        }
    }

    @Test fun `motion sources reject nonfinite parameters before playback`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val vector = Vec3(value.toDouble(), 0.0, 0.0)
            assertThrows(IllegalArgumentException::class.java) { DollyPosition(Vec3.ZERO, Vec3.ZERO, value) }
            assertThrows(IllegalArgumentException::class.java) { DollyPosition(vector, Vec3.ZERO, 1f) }
            assertThrows(IllegalArgumentException::class.java) { TruckPosition(Vec3.ZERO, Vec3.ZERO, value) }
            assertThrows(IllegalArgumentException::class.java) { TruckPosition(Vec3.ZERO, vector, 1f) }
            assertThrows(IllegalArgumentException::class.java) { CranePosition(Vec3.ZERO, value, 0f, 1f) }
            assertThrows(IllegalArgumentException::class.java) { CranePosition(Vec3.ZERO, 1f, 0f, value) }
            assertThrows(IllegalArgumentException::class.java) { FollowActorPosition("actor", vector) }
            assertThrows(IllegalArgumentException::class.java) { LookAlongPath(value) }
        }
    }

    @Test fun `finite motion speed multiplication does not overflow float precision`() {
        val crane = CranePosition(Vec3.ZERO, 10f, 0f, Float.MAX_VALUE)
        val position = crane.evaluate(2f)
        assertTrue(position.x.isFinite() && position.y.isFinite() && position.z.isFinite())
        val truck = TruckPosition(Vec3.ZERO, Vec3(1.0, 0.0, 0.0), Float.MAX_VALUE)
        assertEquals(Float.MAX_VALUE.toDouble() * 2, truck.evaluate(2f).x)
    }

    @Test fun `returning to a position uses the destination keyframes easing`() {
        val source = KeyframePosition(listOf(
            KeyframePosition.Kf(0f, Vec3.ZERO, "step"),
            KeyframePosition.Kf(1f, Vec3(10.0, 0.0, 0.0)),
            KeyframePosition.Kf(2f, Vec3.ZERO, "linear")
        ))
        assertEquals(Vec3(5.0, 0.0, 0.0), source.evaluate(1.5f))
    }

    @Test fun `fixed camera transforms and fallback vectors reject nonfinite coordinates`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { FixedPosition(Vec3(value, 0.0, 0.0)) }
            assertThrows(IllegalArgumentException::class.java) { FixedRotation(CameraRotation(value.toFloat(), 0f)) }
            assertThrows(IllegalArgumentException::class.java) { FixedRoll(value.toFloat()) }
            assertThrows(IllegalArgumentException::class.java) {
                ExpressionVec3Position("0".asExpressionLike(), "0".asExpressionLike(), "0".asExpressionLike(), Vec3(0.0, value, 0.0))
            }
            assertThrows(IllegalArgumentException::class.java) { VariableVec3Position("arena", offset = Vec3(0.0, 0.0, value)) }
            assertThrows(IllegalArgumentException::class.java) { VariableVec3Position("arena", fallback = Vec3(value, 0.0, 0.0)) }
        }
    }

    @Test fun `variable positions use the offset fallback until all coordinates are finite`() {
        val runtime = MoLangRuntime()
        val source = VariableVec3Position("arena", Vec3(1.0, 2.0, 3.0), Vec3(10.0, 64.0, 20.0))
        val fallback = Vec3(11.0, 66.0, 23.0)
        assertEquals(fallback, source.evaluate(null))
        assertEquals(fallback, source.evaluate(runtime))
        val variables = runtime.environment.variable.map
        variables["arena_x"] = DoubleValue(5.0)
        variables["arena_y"] = DoubleValue(70.0)
        assertEquals(fallback, source.evaluate(runtime))
        variables["arena_z"] = DoubleValue(8.0)
        assertEquals(Vec3(6.0, 72.0, 11.0), source.evaluate(runtime))
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            variables["arena_x"] = DoubleValue(value)
            assertEquals(fallback, source.evaluate(runtime))
        }
    }

    @Test fun `overflowing variable offsets fall back and impossible fallback sums are rejected`() {
        val runtime = MoLangRuntime()
        runtime.environment.variable.map.putAll(mapOf("arena_x" to DoubleValue(Double.MAX_VALUE),
            "arena_y" to DoubleValue(0.0), "arena_z" to DoubleValue(0.0)))
        val offset = Vec3(Double.MAX_VALUE, 0.0, 0.0)
        val source = VariableVec3Position("arena", offset)
        assertEquals(offset, source.evaluate(runtime))
        assertThrows(IllegalArgumentException::class.java) { VariableVec3Position("arena", offset, offset) }
    }
}
