/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.math

import com.cobblemon.mod.common.util.math.geometry.toDegrees
import net.minecraft.util.Mth
import kotlin.math.cos
import kotlin.math.sin
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Much of the code here is directly taken or inspired off this great article:
 * https://theorangeduck.com/page/spring-roll-call#critical
 *
 * Very much worth the read.
 *
 * Util funcitons for using springs to smoothly/naturally approach one value towards a target
 */

/**
 * Need to pass in and mutate both a value and a velocity to a spring
 */
class FloatSpring(
    var value: Float = 0f,
    var velocity: Float = 0f
)

/**
 * Provides a fast negative exponent approximation for e^-x
 */
fun negExp(x: Float): Float {
    return 1.0f / (1.0f + x + 0.48f*x*x + 0.235f*x*x*x)
}

fun halflifeToDamping(halflife: Float, eps: Float = 1e-5f): Float {
    return (4.0f * 0.69314718056f) / (halflife + eps)
}

fun simpleSpringDamper(
    spring: FloatSpring,
    goalValue: Float,
    halflife: Float,
    dt: Float,
    isAngle: Boolean = false
) {
    val y = halflifeToDamping(halflife) / 2.0f
    val j0 = if (isAngle) Mth.wrapDegrees(spring.value - goalValue) else spring.value - goalValue
    val j1 = spring.velocity + j0*y;
    val eydt = negExp(y*dt);

    spring.value = eydt*(j0 + j1*dt) + goalValue;
    spring.velocity = eydt*(spring.velocity - j1*y*dt);
}
