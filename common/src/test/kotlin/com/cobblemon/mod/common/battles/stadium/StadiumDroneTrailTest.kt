package com.cobblemon.mod.common.battles.stadium

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumDroneTrailTest {
    @Test fun `normal orbit movement does not create a trail`() {
        val trail = StadiumDroneTrail()
        repeat(20) { tick ->
            val from = Vec3(tick * 0.2, 4.0, 0.0)
            val to = from.add(0.2, 0.0, 0.0)
            trail.beginMove(from, to, 3, tick)
            trail.tick(from, to, tick)
        }
        assertTrue(trail.segments.isEmpty())
        assertNull(trail.bounds())
    }

    @Test fun `fast transition leaves stable jagged segments attached to movement endpoints`() {
        val trail = StadiumDroneTrail()
        val target = Vec3(12.0, 0.0, 0.0)
        trail.beginMove(Vec3.ZERO, target, 3, 0)
        trail.tick(Vec3.ZERO, Vec3(4.0, 0.0, 0.0), 1)
        trail.tick(Vec3(4.0, 0.0, 0.0), Vec3(8.0, 0.0, 0.0), 2)
        trail.tick(Vec3(8.0, 0.0, 0.0), target, 3)
        assertEquals(3, trail.segments.size)
        assertEquals(Vec3.ZERO, trail.segments.first().points.first())
        assertEquals(target, trail.segments.last().points.last())
        assertTrue(trail.segments.first().points.any { it.y != 0.0 || it.z != 0.0 })
        assertTrue(trail.bounds()!!.contains(Vec3.ZERO))
        assertTrue(trail.bounds()!!.contains(target))
        val snapshot = trail.segments
        assertEquals(snapshot, trail.segments)
    }

    @Test fun `trail fades and expires while the drone is stationary`() {
        val trail = StadiumDroneTrail()
        val target = Vec3(5.0, 0.0, 0.0)
        trail.beginMove(Vec3.ZERO, target, 0, 0)
        trail.tick(Vec3.ZERO, target, 0)
        val segment = trail.segments.single()
        assertEquals(1f, segment.opacity(0f))
        assertEquals(0.5f, segment.opacity(5f))
        assertEquals(0f, segment.opacity(10f))
        trail.tick(target, target, 10)
        assertTrue(trail.segments.isEmpty())
    }

    @Test fun `large teleports clear the trail without drawing across the world`() {
        val trail = StadiumDroneTrail()
        val nearby = Vec3(5.0, 0.0, 0.0)
        trail.beginMove(Vec3.ZERO, nearby, 3, 0)
        trail.tick(Vec3.ZERO, nearby, 1)
        val farAway = Vec3(10000.0, 0.0, 0.0)
        trail.beginMove(nearby, farAway, 3, 2)
        trail.tick(nearby, farAway, 3)
        assertTrue(trail.segments.isEmpty())
    }

    @Test fun `vertical jumps remain finite and geometry stays bounded`() {
        val trail = StadiumDroneTrail()
        repeat(100) {
            trail.beginMove(Vec3.ZERO, Vec3(0.0, 120.0, 0.0), 3, 0)
            trail.tick(Vec3.ZERO, Vec3(0.0, 120.0, 0.0), 1)
        }
        assertTrue(trail.segments.size <= 24)
        assertTrue(trail.segments.all { it.points.size <= 21 })
        assertTrue(trail.segments.flatMap { it.points }.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
    }
}
