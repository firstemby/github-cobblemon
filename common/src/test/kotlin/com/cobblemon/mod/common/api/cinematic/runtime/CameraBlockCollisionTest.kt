package com.cobblemon.mod.common.api.cinematic.runtime

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CameraBlockCollisionTest {
    private fun collision(vararg blocks: AABB) = CameraBlockCollision { query -> blocks.filter { it.intersects(query) } }

    @Test
    fun `smooth avoidance has no jumps when rays pass a finite block edge`() {
        val world = collision(AABB(3.0, -2.0, 0.5, 4.0, 3.0, 1.5))
        val edge = atan2(1.75, 2.75)
        fun position(angle: Double) = world.orbitPosition(Vec3.ZERO,
            Vec3(8 * cos(angle), 1.0, 8 * sin(angle)), 2f)!!
        for (sample in -8..8) {
            val angle = edge + sample * 0.25 / 8
            val step = 0.000001
            val before = position(angle - step)
            val at = position(angle)
            val after = position(angle + step)
            assertTrue(before.distanceTo(after) < 0.0001, "Position jumped near angle $angle")
            val incoming = at.subtract(before).scale(1 / step)
            val outgoing = after.subtract(at).scale(1 / step)
            assertTrue(incoming.distanceTo(outgoing) < 0.01, "Tangent jumped near angle $angle")
        }
    }

    @Test
    fun `rounded orbit stays continuous and clear around staggered terrain and the angle seam`() {
        val blocks = (-5..5).map { step ->
            val x = 4.0 + (step % 3) * 0.5
            AABB(x, -2.0, step.toDouble(), x + 1.0, 3.0, step + 1.0)
        } + AABB(-5.0, -2.0, -1.0, -4.0, 3.0, 1.0)
        val world = collision(*blocks.toTypedArray())
        fun position(angle: Double) = world.orbitPosition(Vec3.ZERO,
            Vec3(8 * cos(angle), 1.0, 8 * sin(angle)), 2f)!!
        val path = (0..1440).map { position(it * Math.PI / 720) }
        path.forEach { assertTrue(world.clear(it)) }
        path.zipWithNext().forEach { (from, to) ->
            assertTrue(from.distanceTo(to) < 0.15)
            assertTrue(world.constrain(from, to).distanceTo(to) < 0.000001)
        }
        assertTrue(position(Math.PI - 0.000001).distanceTo(position(Math.PI + 0.000001)) < 0.0001)
        assertTrue(path.first().distanceTo(path.last()) < 0.000001)
    }

    @Test
    fun `open orbit retains its radius and height`() {
        val desired = Vec3(10.0, 4.0, 0.0)
        assertEquals(desired, collision().orbitPosition(Vec3.ZERO, desired))
        assertTrue(desired.distanceTo(collision().orbitPosition(Vec3.ZERO, desired, 2f)!!) < 0.00000001)
    }

    @Test
    fun `rounding bends before a wall corner and preserves clearance throughout the curve`() {
        val world = collision(AABB(6.0, -2.0, -20.0, 7.0, 5.0, 20.0))
        fun desired(degrees: Double): Vec3 {
            val angle = Math.toRadians(degrees)
            return Vec3(8 * cos(angle), 1.0, 8 * sin(angle))
        }
        val ahead = desired(50.0)
        assertEquals(ahead, world.orbitPosition(Vec3.ZERO, ahead))
        val rounded = world.orbitPosition(Vec3.ZERO, ahead, 2f)!!
        assertTrue(rounded.horizontalDistance() < ahead.horizontalDistance())

        fun path(smoothing: Float) = (0..120).map { world.orbitPosition(Vec3.ZERO, desired(30.0 + it * 0.25), smoothing)!! }
        fun sharpestTurn(points: List<Vec3>): Double = points.windowed(3).maxOf { (a, b, c) ->
            val incoming = b.subtract(a).normalize()
            val outgoing = c.subtract(b).normalize()
            acos(incoming.dot(outgoing).coerceIn(-1.0, 1.0))
        }
        val curved = path(2f)
        assertTrue(sharpestTurn(curved) < sharpestTurn(path(0f)) / 2)
        assertTrue(curved.all(world::clear))
        curved.zipWithNext().forEach { (from, to) ->
            assertTrue(world.constrain(from, to).distanceTo(to) < 0.000001)
        }
    }

    @Test
    fun `camera pulls in before a wall with room for the near plane`() {
        val world = collision(AABB(4.0, -2.0, -2.0, 5.0, 5.0, 2.0))
        val result = world.orbitPosition(Vec3.ZERO, Vec3(10.0, 1.0, 0.0))!!
        assertEquals(3.75, result.x, 0.001)
        assertTrue(world.clear(result))
    }

    @Test
    fun `sweep catches thin obstacles between clear endpoints in both directions`() {
        val world = collision(AABB(2.01, -1.0, -1.0, 2.02, 2.0, 1.0))
        val a = Vec3(0.0, 0.0, 0.0)
        val b = Vec3(10.0, 0.0, 0.0)
        assertTrue(world.clear(a) && world.clear(b))
        assertEquals(1.76, world.constrain(a, b).x, 0.001)
        assertEquals(2.27, world.constrain(b, a).x, 0.001)
    }

    @Test
    fun `camera clearance catches a corner missed by the center ray`() {
        val world = collision(AABB(3.0, -1.0, 0.1, 4.0, 1.0, 1.0))
        assertEquals(2.75, world.constrain(Vec3.ZERO, Vec3(8.0, 0.0, 0.0)).x, 0.001)
    }

    @Test
    fun `grounded focus does not pin an elevated orbit to the floor`() {
        val world = collision(AABB(-20.0, -5.0, -20.0, 20.0, 0.0, 20.0))
        val elevated = Vec3(8.0, 3.0, 0.0)
        assertEquals(elevated, world.orbitPosition(Vec3.ZERO, elevated))
        val low = world.orbitPosition(Vec3.ZERO, Vec3(8.0, 0.0, 0.0))!!
        assertEquals(8.0, low.x, 0.001)
        assertTrue(world.clear(low))
        assertTrue(low.y >= CameraBlockCollision.CLEARANCE)
    }

    @Test
    fun `fully obstructed focus yields no unsafe camera position`() {
        val world = collision(AABB(-10.0, -10.0, -10.0, 10.0, 10.0, 10.0))
        assertNull(world.orbitPosition(Vec3.ZERO, Vec3(3.0, 0.0, 0.0)))
    }
}
