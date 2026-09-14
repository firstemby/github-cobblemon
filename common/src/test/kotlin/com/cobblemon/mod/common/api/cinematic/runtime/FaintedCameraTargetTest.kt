package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FollowActorPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAtTarget
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class FaintedCameraTargetTest {
    private fun entity(position: Vec3) = mockk<Entity>(relaxed = true).also {
        every { it.uuid } returns UUID.randomUUID()
        every { it.isAlive } returns true
        every { it.isRemoved } returns false
        every { it.position() } returns position
        every { it.boundingBox } returns AABB(position.add(-0.5, 0.0, -0.5), position.add(0.5, 2.0, 0.5))
    }

    @Test fun `attack orbit holds framing after target faints and follows replacement`() {
        val target = entity(Vec3(120.0, 64.0, -80.0))
        val shot = Shot("target_focus", OrbitPosition(Vec3.ZERO, 4f, 0f, actorIds = listOf("target"), avoidBlocks = false),
            LookAt(LookAtTarget.Actors(listOf("target"))))
        val beat = Beat("attack")
        val scene = Scene("battle", shots = mapOf(shot.id to shot), startBeatId = beat.id, beats = mapOf(beat.id to beat))
        val director = ActorDirector(scene, mapOf("target" to ActorBinding.Real(target)))
        val evaluator = ShotEvaluator(scene)
        val before = evaluator.evaluate(shot.id, 0f, director)!!
        every { target.isAlive } returns false
        every { target.isRemoved } returns true
        every { target.position() } returns Vec3.ZERO
        every { target.boundingBox } returns AABB(Vec3.ZERO, Vec3.ZERO)
        assertEquals(before, evaluator.evaluate(shot.id, 1f, director))
        val replacement = entity(Vec3(135.0, 64.0, -80.0))
        director.updateBinding("target", ActorBinding.Real(replacement))
        assertTrue(before.position.add(15.0, 0.0, 0.0).distanceTo(evaluator.evaluate(shot.id, 2f, director)!!.position) < 0.000001)
    }

    @Test fun `live actor snapshots protect a follow shot first selected after fainting`() {
        val position = Vec3(120.0, 64.0, -80.0)
        val target = entity(position)
        val offset = Vec3(3.0, 2.0, 4.0)
        val shot = Shot("target_focus", FollowActorPosition("target", offset), LookAt(LookAtTarget.Actor("target")))
        val beat = Beat("attack")
        val scene = Scene("battle", shots = mapOf(shot.id to shot), startBeatId = beat.id, beats = mapOf(beat.id to beat))
        val director = ActorDirector(scene, mapOf("target" to ActorBinding.Real(target)))
        director.evaluate(beat, 0f)
        every { target.isAlive } returns false
        every { target.isRemoved } returns true
        every { target.position() } returns Vec3.ZERO
        val result = ShotEvaluator(scene).evaluate(shot.id, 1f, director)!!
        assertEquals(position.add(offset), result.position)
        assertEquals(LookAt(LookAtTarget.Position(position)).computeRotation(result.position, position), result.rotation)
    }
}
