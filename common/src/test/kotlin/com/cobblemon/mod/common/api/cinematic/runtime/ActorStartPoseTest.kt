package com.cobblemon.mod.common.api.cinematic.runtime

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorDirection
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.StartPose
import com.cobblemon.mod.common.api.cinematic.model.StartPoseMode
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpressionLike
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class ActorStartPoseTest {
    private fun position(x: Double) = PositionExpression(CoordinateValue.Static(x), CoordinateValue.Static(64.0), CoordinateValue.Static(20.0))
    private fun beat(position: PositionExpression, mode: StartPoseMode = StartPoseMode.BLEND, duration: Float = 2f) =
        Beat("pose", actorDirections = mapOf("actor" to ActorDirection(visible = true,
            startPose = StartPose(position, CameraRotation(0f, 0f), duration), startPoseMode = mode)))

    private fun director(beat: Beat, entity: Entity, preview: Boolean = false, runtime: () -> MoLangRuntime = { MoLangRuntime() }): ActorDirector {
        val scene = Scene("test", actors = mapOf("actor" to ActorDeclaration("actor", ActorKind.RealEntity)),
            startBeatId = beat.id, beats = mapOf(beat.id to beat))
        return ActorDirector(scene, mapOf("actor" to ActorBinding.Real(entity)), previewMode = preview, runtimeProvider = runtime)
    }

    @Test fun `zero duration blends are instantaneous in live playback and preview`() {
        for (preview in listOf(false, true)) {
            val entity = mockk<Entity>(relaxed = true)
            val beat = beat(position(10.0), duration = 0f)
            val director = director(beat, entity, preview)
            director.onBeatEnter(beat)
            director.evaluate(beat, 0f)
            verify(exactly = 1) { entity.setPos(10.0, 64.0, 20.0) }
            verify(exactly = 0) { entity.setPos(match { !it.isFinite() }, any(), any()) }
            assertNull(director.previewError)
        }
    }

    @Test fun `blend evaluation follows beat time and old blends end at a new beat`() {
        for (preview in listOf(false, true)) {
            val entity = mockk<Entity>(relaxed = true)
            every { entity.x } returns 0.0
            every { entity.y } returns 64.0
            every { entity.z } returns 20.0
            val beat = beat(position(10.0))
            val director = director(beat, entity, preview)
            director.onBeatEnter(beat)
            director.evaluate(beat, 1f)
            verify { entity.setPos(5.0, 64.0, 20.0) }
            clearMocks(entity, answers = false)
            val next = Beat("next")
            director.onBeatEnter(next)
            director.evaluate(next, 2f)
            verify(exactly = 0) { entity.setPos(any<Double>(), any<Double>(), any<Double>()) }
        }
    }

    @Test fun `start positions use the current runtime after a preview reset`() {
        var runtime = MoLangRuntime()
        runtime.environment.variable.map["start_x"] = DoubleValue(10.0)
        val position = PositionExpression(CoordinateValue.Dynamic("v.start_x".asExpressionLike()),
            CoordinateValue.Static(64.0), CoordinateValue.Static(20.0))
        val entity = mockk<Entity>(relaxed = true)
        val beat = beat(position, StartPoseMode.TELEPORT)
        val director = director(beat, entity, runtime = { runtime })
        director.onBeatEnter(beat)
        verify { entity.setPos(10.0, 64.0, 20.0) }
        runtime = MoLangRuntime()
        runtime.environment.variable.map["start_x"] = DoubleValue(30.0)
        director.onBeatEnter(beat)
        verify { entity.setPos(30.0, 64.0, 20.0) }
    }

    @Test fun `invalid start positions leave the actor in place and report a translated error`() {
        val runtime = MoLangRuntime()
        runtime.environment.variable.map["start_x"] = DoubleValue(Double.NaN)
        val invalidPosition = PositionExpression(CoordinateValue.Dynamic("v.start_x".asExpressionLike()),
            CoordinateValue.Static(64.0), CoordinateValue.Static(20.0))
        val entity = mockk<Entity>(relaxed = true)
        val beat = beat(invalidPosition, StartPoseMode.TELEPORT)
        val director = director(beat, entity, runtime = { runtime })
        director.onBeatEnter(beat)
        verify(exactly = 0) { entity.setPos(any<Double>(), any<Double>(), any<Double>()) }
        assertEquals(Component.translatable("cobblemon.cinematic_editor.preview.invalid_start_pose", "actor"), director.previewError)
        director.onBeatEnter(Beat("next"))
        assertNull(director.previewError)
    }

    @Test fun `ignored poses never evaluate position expressions`() {
        val entity = mockk<Entity>(relaxed = true)
        val beat = beat(position(10.0), StartPoseMode.IGNORE)
        val director = director(beat, entity, runtime = { error("Ignored pose evaluated its runtime") })
        director.onBeatEnter(beat)
        verify(exactly = 0) { entity.setPos(any<Double>(), any<Double>(), any<Double>()) }
        assertNull(director.previewError)
    }
}
