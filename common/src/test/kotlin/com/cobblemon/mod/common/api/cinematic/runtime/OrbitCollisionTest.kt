package com.cobblemon.mod.common.api.cinematic.runtime

import com.google.gson.JsonParser
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.*
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OrbitCollisionTest {
    @Test
    fun `orbit JSON enables collision by default and preserves opt out`() {
        val json = JsonParser.parseString("""{"center":{"x":0,"y":0,"z":0},"radius":8,"angularVelocity":20}""").asJsonObject
        assertTrue(OrbitPosition.fromJson(json).avoidBlocks)
        assertEquals(0f, OrbitPosition.fromJson(json).avoidanceSmoothing)
        json.addProperty("avoidanceSmoothing", 2.5f)
        assertEquals(2.5f, OrbitPosition.fromJson(json).avoidanceSmoothing)
        json.addProperty("avoidBlocks", false)
        assertFalse(OrbitPosition.fromJson(json).avoidBlocks)
        listOf(-1f, 9f, Float.NaN, Float.POSITIVE_INFINITY).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { OrbitPosition.fromJson(json).copy(avoidanceSmoothing = invalid) }
        }
    }

    private fun evaluator(shot: Shot, collision: CameraBlockCollision) = ShotEvaluator(
        Scene(id = "test", startBeatId = "main", beats = mapOf("main" to Beat("main")), shots = mapOf(shot.id to shot)), collision)

    @Test
    fun `collision runs after offsets and keeps the camera aimed at its target`() {
        val wall = AABB(4.0, -10.0, -10.0, 5.0, 10.0, 10.0)
        val collision = CameraBlockCollision { listOf(wall) }
        val look = LookAt(LookAtTarget.Position(Vec3.ZERO))
        val shot = Shot("orbit", OrbitPosition(Vec3.ZERO, 2f, 0f, height = 2f), look,
            modifiers = listOf(OffsetModifier(positionOffset = Vec3(6.0, 0.0, 0.0), yawOffset = 10f)))
        val result = evaluator(shot, collision).evaluate("orbit", 0f)!!
        assertEquals(3.75, result.position.x, 0.001)
        assertTrue(collision.clear(result.position))
        val expected = look.computeRotation(result.position, Vec3.ZERO)
        assertEquals(expected.yaw + 10f, result.rotation!!.yaw, 0.001f)
        assertEquals(expected.pitch, result.rotation.pitch, 0.001f)
    }

    @Test
    fun `successive orbit frames cannot cross a wall between safe endpoints`() {
        val collision = CameraBlockCollision { listOf(AABB(4.0, -1.0, 0.25, 6.0, 1.0, 0.3)) }
        val shot = Shot("orbit", OrbitPosition(Vec3.ZERO, 5f, 90f), LookAt(LookAtTarget.Position(Vec3.ZERO)))
        val evaluator = evaluator(shot, collision)
        val first = evaluator.evaluate("orbit", 0f)!!.position
        val second = evaluator.evaluate("orbit", 0.1f)!!.position
        assertTrue(second.x < first.x)
        assertEquals(second, collision.constrain(first, second))
        assertTrue(collision.clear(second))
        evaluator.resetSmoothing()
        assertTrue(evaluator.evaluate("orbit", 0.1f)!!.position.z > second.z)
    }

    @Test
    fun `avoid blocks can be disabled for an authored orbit`() {
        val orbit = OrbitPosition(Vec3.ZERO, 8f, 20f, avoidBlocks = false, avoidanceSmoothing = 2f)
        val shot = Shot("orbit", orbit, LookAt(LookAtTarget.Position(Vec3.ZERO)))
        val collision = CameraBlockCollision { listOf(AABB(-20.0, -20.0, -20.0, 20.0, 20.0, 20.0)) }
        assertEquals(orbit.evaluate(1f), evaluator(shot, collision).evaluate("orbit", 1f)!!.position)
    }

    @Test
    fun `smoothed avoidance matches direct seeks and stays clear during playback`() {
        val collision = CameraBlockCollision { listOf(AABB(6.0, -2.0, -20.0, 7.0, 5.0, 20.0)) }
        val orbit = OrbitPosition(Vec3.ZERO, 8f, 30f, startAngleDeg = 30f, height = 1f, avoidanceSmoothing = 2f)
        val shot = Shot("orbit", orbit, LookAt(LookAtTarget.Position(Vec3.ZERO)))
        val evaluator = evaluator(shot, collision)
        var previous: Vec3? = null
        for (frame in 0..20) {
            val time = frame / 20f
            val position = evaluator.evaluate("orbit", time)!!.position
            val expected = collision.orbitPosition(Vec3.ZERO, orbit.evaluate(time), 2f)!!
            assertTrue(position.distanceTo(expected) < 0.000001)
            assertTrue(collision.clear(position))
            previous?.let { assertTrue(collision.constrain(it, position).distanceTo(position) < 0.000001) }
            previous = position
        }
        evaluator.resetSmoothing()
        assertEquals(previous, evaluator.evaluate("orbit", 1f)!!.position)
    }
}
