/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.math

import net.minecraft.util.Mth

/**
 * Eases one value towards a target with inertial easing, see SpringUtils and https://theorangeduck.com/page/inertial-easing
 * Keeps its velocity when the target changes and gets there within 1.5x [blendTime]
 *
 * With [isAngle] the value is in degrees and always eases the short way round.
 */
class InertialEase(
    private val blendTime: Float,
    private val isAngle: Boolean = false
) {
    companion object {
        const val SECONDS_PER_TICK = 0.05F
    }

    var value = 0F
        private set

    private val vals = InertialVals()
    // How far along the curve one tick is
    private val step = SECONDS_PER_TICK / blendTime
    private var target = 0F

    /** Moves one tick towards [newTarget] */
    fun update(newTarget: Float) {
        if (newTarget != target) {
            val difference = if (isAngle) Mth.wrapDegrees(newTarget - value) else newTarget - value
            // Velocity along the current curve, carried into the new one so there's no sudden change
            smoothstepSolve(vals, difference, smoothstepDt(vals.t, vals.s))
            vals.offset = value - smoothstep(vals.t, vals.s)
            target = newTarget
        }
        vals.t += step
        value = vals.offset + smoothstep(vals.t, vals.s)
        if (isAngle) {
            // Keep turning angles in range so they don't grow forever
            val turns = value - Mth.wrapDegrees(value)
            if (turns != 0F) {
                vals.offset -= turns
                value -= turns
            }
        }
    }

    /** True once the curve has reached its target */
    fun isSettled() = vals.t >= 1F

    /** How fast the value is changing in units per second */
    fun velocity() = smoothstepDt(vals.t, vals.s) / blendTime

    /** Value between last tick and this one read straight off the curve */
    fun valueAt(partialTick: Float): Float {
        val eased = vals.offset + smoothstep(vals.t - step * (1 - partialTick), vals.s)
        return if (isAngle) Mth.wrapDegrees(eased) else eased
    }

    /** Jumps straight to [to] with no easing */
    fun snap(to: Float) {
        vals.t = 1F
        vals.s = 0F
        vals.offset = to
        target = to
        value = to
    }
}
