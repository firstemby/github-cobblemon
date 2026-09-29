/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.tracks.builtin

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class CinematicSpline(controlPoints: List<Vec3>) {
    private val points = controlPoints.fold(mutableListOf<Vec3>()) { result, point ->
        require(point.x.isFinite() && point.y.isFinite() && point.z.isFinite()) { "Path points must be finite" }
        if (result.isEmpty() || result.last().distanceToSqr(point) > 1e-12) result.add(point)
        result
    }.toList()
    private val sampleCount = ((points.size - 1) * 64).coerceIn(1, 16384)
    private val distances = DoubleArray(sampleCount + 1)
    val length: Double get() = distances.last()

    init {
        require(points.isNotEmpty()) { "A path needs at least one point" }
        var previous = parameterPosition(0.0)
        for (i in 1..sampleCount) {
            val position = parameterPosition(i.toDouble() / sampleCount)
            distances[i] = distances[i - 1] + previous.distanceTo(position)
            previous = position
        }
    }

    fun position(progress: Double): Vec3 {
        require(progress.isFinite()) { "Path progress must be finite" }
        if (progress <= 0.0 || length <= 1e-9) return points.first()
        if (progress >= 1.0) return points.last()
        val distance = progress * length
        var low = 0
        var high = sampleCount
        while (high - low > 1) {
            val middle = (low + high) ushr 1
            if (distances[middle] < distance) low = middle else high = middle
        }
        val span = distances[high] - distances[low]
        val fraction = if (span > 1e-12) (distance - distances[low]) / span else 0.0
        return parameterPosition((low + fraction) / sampleCount)
    }

    fun tangent(progress: Double): Vec3 {
        val t = progress.coerceIn(0.0, 1.0)
        val step = if (length > 0.0) (0.01 / length).coerceIn(1e-5, 0.01) else 0.01
        val direction = position((t + step).coerceAtMost(1.0)).subtract(position((t - step).coerceAtLeast(0.0)))
        return if (direction.lengthSqr() > 1e-12) direction.normalize() else Vec3.ZERO
    }

    private fun parameterPosition(progress: Double): Vec3 {
        if (points.size == 1) return points.first()
        if (points.size == 2) return points[0].lerp(points[1], progress)
        val segment = progress.coerceIn(0.0, 1.0) * (points.size - 1)
        val index = segment.toInt().coerceAtMost(points.size - 2)
        val p1 = points[index]
        val p2 = points[index + 1]
        val p0 = points.getOrNull(index - 1) ?: p1.scale(2.0).subtract(p2)
        val p3 = points.getOrNull(index + 2) ?: p2.scale(2.0).subtract(p1)
        val t0 = 0.0
        val t1 = sqrt(p0.distanceTo(p1))
        val t2 = t1 + sqrt(p1.distanceTo(p2))
        val t3 = t2 + sqrt(p2.distanceTo(p3))
        val t = t1 + (segment - index) * (t2 - t1)
        fun blend(a: Vec3, b: Vec3, start: Double, end: Double) = a.lerp(b, (t - start) / (end - start))
        val a1 = blend(p0, p1, t0, t1)
        val a2 = blend(p1, p2, t1, t2)
        val a3 = blend(p2, p3, t2, t3)
        val b1 = blend(a1, a2, t0, t2)
        val b2 = blend(a2, a3, t1, t3)
        return blend(b1, b2, t1, t2)
    }
}
