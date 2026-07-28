/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.math

import net.minecraft.util.Mth
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.abs
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

class QuatSpring(
    var quat: Quaternionf = Quaternionf(),
    var angVel: Vector3f = Vector3f()
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

fun simpleSpringDamperQuat(
    spring: QuatSpring,
    goalQuat: Quaternionf,
    halflife: Float,
    dt: Float
) {
    val y = halflifeToDamping(halflife) / 2.0f

    // Gather the rotation from the goal to the current
    val goalToCurrQuat = Quaternionf(spring.quat).mul(Quaternionf(goalQuat).conjugate())
    
    val j0 = Vector3f()
    goalToCurrQuat.toScaledAngleAxis(j0)

    val j1: Vector3f = Vector3f(spring.angVel).add(Vector3f(j0).mul(y))

    val eydt: Float = negExp(y * dt)

    val scaledAxis = Vector3f(j0).add(Vector3f(j1).mul(dt)).mul(eydt)

    spring.quat.setFromScaledAngleAxis(scaledAxis)
    spring.quat.mul(goalQuat)

    spring.angVel.sub(j1.mul(y * dt))
    spring.angVel.mul(eydt)
}


/**
* Below is essentially the same code as found here: https://github.com/orangeduck/Spring-It-On/blob/main/inertialeasing.c
 * https://theorangeduck.com/page/inertial-easing <- the blogpost
*/

// Class to hold the "time" along the curve/function and scale of the function currently
// Held in this class so both can be mutated and returned in one function call
class InertialVals(
    var t: Float = 0f,
    var s: Float = 0f,
    var offset: Float = 0f,
)

// Includes the negative portion
fun smoothstep(t: Float, s: Float): Float {
    return s * (if(t > 1.0f) 1.0f else 3*t*t - 2*t*t*t)
}

// Derivative of the above smoothstep
fun smoothstepDt(t: Float, s: Float): Float {
    return s * (if(t > 1.0f) 0.0f else 6*t - 6*t*t)
}

// Solve for smoothstep parameters `t` and `s` given new target `x` and velocity `v`
fun smoothstepSolve( //TODO change these names to make more sense
    vals: InertialVals,
    x: Float, // Target Value x
    v: Float,  // Goal velocity 
    overshoot: Float = 0.05f,
    eps: Float = 1e-8f
) {
    // If velocity is low enough then start from time zero
    if (abs(v) < 1e-8f) {
        vals.t = 0f
        vals.s = x
        return
    }

    // If x is negative then invert signs and re-Solve
    if (x < 0f) {
        smoothstepSolve(vals, -x, -v, overshoot, eps)
        vals.s *= -1f
        return
    }

    // Find the possible times that solve for the given `x` and `v`
    // Quadratic formula!
    val q: Float = sqrt(max((6*x - v)*(6*x - v) + 8*v*v, eps))
    val t0: Float = (v - 6*x + q) / (4*v)
    val t1: Float = (v - 6*x - q) / (4*v)

    // Overshoot if the alternative time is between -0.5f and -(0.5f + overshoot)
    vals.t = if(-0.5f > t1 && t1 > -(0.5f + overshoot)) t1 else t0;

    // Find the un-scaled velocity at the fitted time
    val vt: Float = smoothstepDt(vals.t, 1f);        

    // Find the scaling factor so that the velocity at the fitted time matches
    vals.s = if(abs(vt) < eps) 0f else v / vt;
}