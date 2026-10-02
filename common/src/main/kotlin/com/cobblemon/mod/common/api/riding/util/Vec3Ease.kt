/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

import com.cobblemon.mod.common.util.math.InertialVals
import com.cobblemon.mod.common.util.math.smoothstep
import com.cobblemon.mod.common.util.math.smoothstepDt
import com.cobblemon.mod.common.util.math.smoothstepSolve
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

/**
 * Eases a Vec3 towards a target with inertial easing, see SpringUtils and https://theorangeduck.com/page/inertial-easing
 * When the target changes each component keeps its current velocity
 *
 * With [isAngle] the components are treated as degrees and always ease the short way round.
 */
class Vec3Ease(
    private val blendTime: Float,
    private val isAngle: Boolean = false
) {
    companion object {
        const val SECONDS_PER_TICK = 0.05F
    }

    var value: Vec3 = Vec3.ZERO
        private set

    private val axes = Array(3) { InertialVals() }
    // How far along the curve one tick is
    private val step = SECONDS_PER_TICK / blendTime
    private var target = Vec3.ZERO

    /** Moves one tick towards [newTarget] */
    fun update(newTarget: Vec3) {
        val current = doubleArrayOf(value.x, value.y, value.z)
        val oldGoal = doubleArrayOf(target.x, target.y, target.z)
        val goal = doubleArrayOf(newTarget.x, newTarget.y, newTarget.z)
        for (i in 0..2) {
            val vals = axes[i]
            if (goal[i] != oldGoal[i]) {
                val difference = if (isAngle) Mth.wrapDegrees(goal[i] - current[i]) else goal[i] - current[i]
                // Velocity along the current curve, carried into the new one so there's no sudden change
                smoothstepSolve(vals, difference.toFloat(), smoothstepDt(vals.t, vals.s))
                vals.offset = current[i].toFloat() - smoothstep(vals.t, vals.s)
            }
            vals.t += step
            current[i] = (vals.offset + smoothstep(vals.t, vals.s)).toDouble()
            if (isAngle) {
                // Keep turning angles in range so they don't grow forever
                val turns = current[i] - Mth.wrapDegrees(current[i])
                if (turns != 0.0) {
                    vals.offset -= turns.toFloat()
                    current[i] -= turns
                }
            }
        }
        target = newTarget
        value = Vec3(current[0], current[1], current[2])
    }

    /** Jumps straight to [to] with no easing */
    fun snap(to: Vec3) {
        val components = floatArrayOf(to.x.toFloat(), to.y.toFloat(), to.z.toFloat())
        for (i in 0..2) {
            axes[i].t = 1F
            axes[i].s = 0F
            axes[i].offset = components[i]
        }
        target = to
        value = to
    }

    /** Value between last tick and this one read straight off the curve. [lookBackTick] isn't used anymore */
    fun getInterpolated(partialTick: Double, @Suppress("UNUSED_PARAMETER") lookBackTick: Int = 0): Vec3 {
        val back = step * (1 - partialTick.toFloat())
        val x = axes[0].let { it.offset + smoothstep(it.t - back, it.s) }.toDouble()
        val y = axes[1].let { it.offset + smoothstep(it.t - back, it.s) }.toDouble()
        val z = axes[2].let { it.offset + smoothstep(it.t - back, it.s) }.toDouble()
        // Can land just past ±180 partway through a tick
        return if (isAngle) Vec3(Mth.wrapDegrees(x), Mth.wrapDegrees(y), Mth.wrapDegrees(z)) else Vec3(x, y, z)
    }
}
