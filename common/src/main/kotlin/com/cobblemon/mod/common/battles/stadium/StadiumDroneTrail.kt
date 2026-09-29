/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class StadiumDroneTrail {
    data class Segment(val points: List<Vec3>, val tick: Int) {
        fun opacity(time: Float) = (1f - (time - tick) / LIFETIME).coerceIn(0f, 1f)
    }

    private val history = ArrayDeque<Segment>()
    val segments: List<Segment> get() = history.toList()
    private var activeUntil = -1

    fun beginMove(from: Vec3, to: Vec3, steps: Int, tick: Int) {
        val distance = from.distanceToSqr(to)
        if (distance > MAX_DISTANCE * MAX_DISTANCE) {
            clear()
        } else if (distance >= 4.0) {
            activeUntil = tick + steps.coerceAtLeast(1)
        }
    }

    fun tick(from: Vec3, to: Vec3, tick: Int) {
        while (history.firstOrNull()?.let { tick - it.tick >= LIFETIME } == true) history.removeFirst()
        val distance = from.distanceTo(to)
        if (tick > activeUntil || distance < 0.01 || !distance.isFinite() || distance > MAX_DISTANCE) return
        val direction = to.subtract(from).normalize()
        val reference = if (abs(direction.y) > 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val side = direction.cross(reference).normalize()
        val up = direction.cross(side).normalize()
        val count = (distance * 2).toInt().coerceIn(2, 20)
        val random = Random(tick)
        val points = (0..count).map { index ->
            if (index == 0) from else if (index == count) to else {
                val t = index.toDouble() / count
                val spread = minOf(0.25, distance * 0.15) * sin(PI * t)
                from.lerp(to, t).add(side.scale(random.nextDouble(-spread, spread)))
                    .add(up.scale(random.nextDouble(-spread, spread)))
            }
        }
        history.addLast(Segment(points, tick))
        while (history.size > 24) history.removeFirst()
    }

    fun bounds(): AABB? = history.asSequence().flatMap { it.points.asSequence() }
        .map { AABB(it, it) }.reduceOrNull { bounds, point -> bounds.minmax(point) }?.inflate(0.3)

    fun clear() {
        history.clear()
        activeUntil = -1
    }

    companion object {
        private const val LIFETIME = 10
        private const val MAX_DISTANCE = 128.0
    }
}
