/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p

class CameraBlockCollision(private val obstacles: (AABB) -> Iterable<AABB>) {
    fun clear(position: Vec3): Boolean {
        val body = AABB(position, position).inflate(CLEARANCE)
        return obstacles(body).none { it.intersects(body) }
    }

    fun orbitPosition(focus: Vec3, desired: Vec3, smoothing: Float = 0f): Vec3? {
        val center = Vec3(focus.x, desired.y, focus.z)
        val anchor = (0..16).asSequence().map { center.add(0.0, it * 0.25, 0.0) }.firstOrNull(::clear) ?: return null
        val target = desired.add(0.0, anchor.y - center.y, 0.0)
        val radial = target.subtract(anchor)
        val radius = radial.horizontalDistance()
        if (smoothing <= 0f || radius < 0.00001 || radius > 4096) return constrain(anchor, target)
        val spread = (smoothing / radius).coerceAtMost(Math.PI / 2)
        val angle = atan2(radial.z, radial.x)
        val directions = mutableListOf(angle - spread, angle, angle + spread)
        for (quadrant in 0..3) {
            val cardinal = quadrant * Math.PI / 2
            if (abs(wrapAngle(cardinal - angle)) <= spread) directions.add(cardinal)
        }
        val bounds = directions.fold(AABB(anchor, anchor)) { box, bearing ->
            val point = anchor.add(radius * cos(bearing), 0.0, radius * sin(bearing))
            box.minmax(AABB(point, point))
        }
        var penalty = 0.0
        for (obstacle in obstacles(bounds.inflate(CLEARANCE))) {
            val limit = roundedRadius(obstacle.inflate(CLEARANCE), anchor, radius, angle, spread)
            if (limit >= radius) continue
            if (limit <= 0.00000001) return anchor
            val next = 16 * ln(radius / limit)
            val largest = max(penalty, next)
            // This smooth minimum stays inside every obstacle's safe radius without a sample grid.
            penalty = largest + ln1p(exp(-abs(penalty - next)) - exp(-largest))
        }
        return anchor.add(radial.scale(exp(-penalty / 16)))
    }

    private fun roundedRadius(box: AABB, anchor: Vec3, radius: Double, angle: Double, spread: Double): Double {
        if (anchor.y <= box.minY || anchor.y >= box.maxY) return radius
        val minX = box.minX - anchor.x
        val maxX = box.maxX - anchor.x
        val minZ = box.minZ - anchor.z
        val maxZ = box.maxZ - anchor.z
        val closestX = 0.0.coerceIn(minX, maxX)
        val closestZ = 0.0.coerceIn(minZ, maxZ)
        val distance = sqrt(closestX * closestX + closestZ * closestZ)
        if (distance >= radius) return radius
        val bearing = atan2((minZ + maxZ) / 2, (minX + maxX) / 2)
        var first = Double.POSITIVE_INFINITY
        var last = Double.NEGATIVE_INFINITY
        fun include(x: Double, z: Double) {
            if (x * x + z * z < 0.0000000001) return
            val offset = wrapAngle(atan2(z, x) - bearing)
            first = min(first, offset)
            last = max(last, offset)
        }
        val radiusSquared = radius * radius
        for (x in listOf(minX, maxX)) {
            for (z in listOf(minZ, maxZ)) {
                if (x * x + z * z <= radiusSquared) include(x, z)
            }
            if (abs(x) <= radius) {
                val z = sqrt((radiusSquared - x * x).coerceAtLeast(0.0))
                if (z in minZ..maxZ) include(x, z)
                if (-z in minZ..maxZ) include(x, -z)
            }
        }
        for (z in listOf(minZ, maxZ)) {
            if (abs(z) <= radius) {
                val x = sqrt((radiusSquared - z * z).coerceAtLeast(0.0))
                if (x in minX..maxX) include(x, z)
                if (-x in minX..maxX) include(-x, z)
            }
        }
        if (!first.isFinite()) return radius
        val relative = wrapAngle(angle - bearing)
        val gap = (-1..1).minOf { turn ->
            val a = relative + turn * Math.PI * 2
            max(0.0, max(first - a, a - last))
        }
        if (gap >= spread) return radius
        val t = (gap / spread).coerceIn(0.0, 1.0)
        val falloff = 1 - t * t * t * (10 + t * (-15 + 6 * t))
        return radius - (radius - (distance - 0.0001).coerceAtLeast(0.0)) * falloff
    }

    private fun wrapAngle(angle: Double) = atan2(sin(angle), cos(angle))

    fun constrain(from: Vec3, to: Vec3): Vec3 {
        val distance = from.distanceTo(to)
        if (distance < 0.00001) return from
        val steps = ceil(distance).toInt().coerceIn(1, 4096)
        val end = if (distance > 4096) from.add(to.subtract(from).scale(4096 / distance)) else to
        var previous = from
        for (step in 1..steps) {
            val next = from.lerp(end, step.toDouble() / steps)
            val query = AABB(previous, next).inflate(CLEARANCE)
            val fraction = obstacles(query).minOfOrNull { entryFraction(it.inflate(CLEARANCE), previous, next) } ?: 1.0
            if (fraction < 1.0) return previous.lerp(next, (fraction - 0.0001).coerceAtLeast(0.0))
            previous = next
        }
        return end
    }

    fun slide(from: Vec3, to: Vec3): Vec3 {
        var result = constrain(from, to)
        if (result.distanceToSqr(to) < 0.00000001) return result
        for (axis in 0..2) {
            val target = when (axis) {
                0 -> Vec3(to.x, result.y, result.z)
                1 -> Vec3(result.x, to.y, result.z)
                else -> Vec3(result.x, result.y, to.z)
            }
            val candidate = constrain(result, target)
            // Feed interpolation travels straight between ticks, even when the collision response slides.
            if (constrain(from, candidate).distanceToSqr(candidate) < 0.00000001) result = candidate
        }
        return result
    }

    private fun entryFraction(box: AABB, from: Vec3, to: Vec3): Double {
        var enter = 0.0
        var leave = 1.0
        fun axis(start: Double, end: Double, low: Double, high: Double): Boolean {
            val delta = end - start
            if (abs(delta) < 0.0000001) return start > low && start < high
            val a = (low - start) / delta
            val b = (high - start) / delta
            enter = max(enter, min(a, b))
            leave = min(leave, max(a, b))
            return enter < leave
        }
        return if (axis(from.x, to.x, box.minX, box.maxX) &&
            axis(from.y, to.y, box.minY, box.maxY) &&
            axis(from.z, to.z, box.minZ, box.maxZ)) enter else 1.0
    }

    companion object {
        const val CLEARANCE = 0.25

        fun forLevel(level: Level) = CameraBlockCollision { bounds ->
            val loaded = ((floor(bounds.minX).toInt() shr 4)..(floor(bounds.maxX).toInt() shr 4)).all { x ->
                ((floor(bounds.minZ).toInt() shr 4)..(floor(bounds.maxZ).toInt() shr 4)).all { z ->
                    level.hasChunkAt(BlockPos(x shl 4, floor(bounds.minY).toInt(), z shl 4))
                }
            }
            if (!loaded) listOf(bounds) else level.getBlockCollisions(null, bounds).flatMap { it.toAabbs() }
        }
    }
}
