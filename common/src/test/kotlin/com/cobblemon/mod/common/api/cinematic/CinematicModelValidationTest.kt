package com.cobblemon.mod.common.api.cinematic

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiAction
import com.cobblemon.mod.common.api.cinematic.model.SceneGuiActionType
import com.cobblemon.mod.common.api.cinematic.model.StartPose
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ConstraintModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OffsetModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeDerivedRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ShakeModifier
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AnimationTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AttachTrack
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.FollowActor
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.LookAtTrack
import com.cobblemon.mod.common.api.molang.ExpressionLike
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CinematicModelValidationTest {
    private val origin = PositionExpression(CoordinateValue.Static(0.0), CoordinateValue.Static(0.0), CoordinateValue.Static(0.0))

    @Test fun `timing lens and actor scale reject nonfinite values`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Beat("beat", duration = value) }
            assertThrows(IllegalArgumentException::class.java) { Beat("beat", speed = value) }
            assertThrows(IllegalArgumentException::class.java) { CutEntry(value, "shot") }
            assertThrows(IllegalArgumentException::class.java) { CutTransition.Blend(value) }
            assertThrows(IllegalArgumentException::class.java) { SceneGuiAction(value, SceneGuiActionType.OPEN, "gui") }
            assertThrows(IllegalArgumentException::class.java) { Lens(aspectCrop = value) }
            assertThrows(IllegalArgumentException::class.java) {
                ActorBinding.SynthBedrock(ResourceLocation.fromNamespaceAndPath("cobblemon", "actor"), scale = value)
            }
            assertThrows(IllegalArgumentException::class.java) { StartPose(origin, CameraRotation(0f, 0f), value) }
            assertThrows(IllegalArgumentException::class.java) { StartPose(origin, CameraRotation(value, 0f)) }
            assertThrows(IllegalArgumentException::class.java) { StartPose(origin, CameraRotation(0f, value)) }
            assertThrows(IllegalArgumentException::class.java) { FollowActor("target", Vec3.ZERO, value) }
            assertThrows(IllegalArgumentException::class.java) { AnimationTrack.Cue(value, "idle") }
        }
        assertThrows(IllegalArgumentException::class.java) { AnimationTrack.Cue(-1f, "idle") }
        assertThrows(IllegalArgumentException::class.java) { AnimationTrack.Cue(0f, " ") }
        assertThrows(IllegalArgumentException::class.java) { StartPose(origin, CameraRotation(0f, 0f), -1f) }
    }

    @Test fun `actor targets attachments and camera offsets reject nonfinite coordinates`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (vector in listOf(Vec3(value, 0.0, 0.0), Vec3(0.0, value, 0.0), Vec3(0.0, 0.0, value))) {
                assertThrows(IllegalArgumentException::class.java) { AttachTrack("parent", vector) }
                assertThrows(IllegalArgumentException::class.java) { FollowActor("target", vector) }
                assertThrows(IllegalArgumentException::class.java) { LookAtTrack(vector) }
                assertThrows(IllegalArgumentException::class.java) { OffsetModifier(positionOffset = vector) }
            }
            assertThrows(IllegalArgumentException::class.java) { OffsetModifier(yawOffset = value.toFloat()) }
            assertThrows(IllegalArgumentException::class.java) { OffsetModifier(pitchOffset = value.toFloat()) }
            assertThrows(IllegalArgumentException::class.java) { OffsetModifier(fovOffset = value.toFloat()) }
            assertThrows(IllegalArgumentException::class.java) { OffsetModifier(rollOffset = value.toFloat()) }
        }
    }

    @Test fun `constraints reject invalid bounds and preserve one-sided and fixed-axis bounds`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(minX = value) }
            assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(maxX = value) }
            assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(minY = value) }
            assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(maxY = value) }
            assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(minZ = value) }
            assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(maxZ = value) }
        }
        assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(minX = 2.0, maxX = 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(minY = 2.0, maxY = 1.0) }
        assertThrows(IllegalArgumentException::class.java) { ConstraintModifier(minZ = 2.0, maxZ = 1.0) }
        val modifier = ConstraintModifier(minX = 1.0, minY = 2.0, maxY = 2.0, maxZ = 3.0)
        assertEquals(Vec3(1.0, 2.0, 3.0), modifier.apply(Vec3(0.0, -10.0, 4.0)))
        assertEquals(Vec3(4.0, 2.0, -1.0), modifier.apply(Vec3(4.0, 10.0, -1.0)))
    }

    @Test fun `shake parameters reject nonfinite values and large finite time keeps noise finite`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { ShakeModifier(positionAmplitude = value) }
            assertThrows(IllegalArgumentException::class.java) { ShakeModifier(rotationAmplitudeDeg = value) }
            assertThrows(IllegalArgumentException::class.java) { ShakeModifier(decaySecs = value) }
            assertThrows(IllegalArgumentException::class.java) { ShakeDerivedRoll(value) }
            assertThrows(IllegalArgumentException::class.java) { ShakeDerivedRoll(1f, decaySecs = value) }
        }
        val noise = ShakeModifier(frequencyHz = 60f).noise3(Float.MAX_VALUE)
        assertTrue(noise.toList().all { it.isFinite() && it in -1f..1f })
        assertTrue(ShakeDerivedRoll(1f, frequencyHz = 60f).evaluate(Float.MAX_VALUE).isFinite())
    }

    @Test fun `zero time remains valid for instantaneous actions and holds`() {
        assertEquals(0f, Beat("beat", duration = 0f).duration)
        assertEquals(0f, CutEntry(0f, "shot").time)
        assertEquals(0f, SceneGuiAction(0f, SceneGuiActionType.OPEN, "gui").time)
        assertEquals(0f, AnimationTrack.Cue(0f, "idle").time)
        assertEquals(0f, StartPose(origin, CameraRotation(0f, 0f), 0f).blendDurationSeconds)
        assertEquals(0f, FollowActor("target", Vec3.ZERO).lagSecs)
    }

    @Test fun `look-at identifiers and static coordinates must be usable`() {
        assertThrows(IllegalArgumentException::class.java) { LookAtTarget.Actor(" ") }
        assertThrows(IllegalArgumentException::class.java) { LookAtTarget.Actors(listOf("actor", "")) }
        assertThrows(IllegalArgumentException::class.java) { LookAtTarget.ActorBone("", "head") }
        assertThrows(IllegalArgumentException::class.java) { LookAtTarget.ActorBone("actor", " ") }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { CoordinateValue.Static(value) }
        }
        assertEquals("runtime_alias", LookAtTarget.Actor("runtime_alias").actorId)
    }

    @Test fun `dynamic coordinates propagate evaluation failures and reject nonfinite results`() {
        fun coordinate(resolve: () -> Double) = CoordinateValue.Dynamic(object : ExpressionLike {
            override fun toString() = "test"
            override fun resolve(runtime: MoLangRuntime, context: Map<String, MoValue>): MoValue = DoubleValue(resolve())
        })
        val runtime = MoLangRuntime()
        assertEquals(12.0, coordinate { 12.0 }.evaluate(emptyMap(), runtime))
        assertThrows(IllegalArgumentException::class.java) { coordinate { Double.NaN }.evaluate(emptyMap(), runtime) }
        assertThrows(IllegalStateException::class.java) {
            coordinate { throw IllegalStateException("failed") }.evaluate(emptyMap(), runtime)
        }
    }
}
