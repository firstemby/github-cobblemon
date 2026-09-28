/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.math

import kotlin.math.sqrt
import net.minecraft.world.phys.Vec3

/**
 * Velocity steering helpers, detailed in https://www.red3d.com/cwr/steer/gdc99/
 *
 * Behaviours give a desired velocity and [steerForce] turns it into a force limited by the max acceleration.
 */

/** Desired velocity toward [target]. With [arrive] it slows to stop on the target. */
fun seekVelocity(from: Vec3, target: Vec3, maxSpeed: Double, maxAccel: Double, arrive: Boolean): Vec3 {
    val toTarget = target.subtract(from)
    val speed = if (arrive) arriveSpeed(toTarget.length(), maxSpeed, maxAccel) else maxSpeed
    return toTarget.normalize().scale(speed)
}

/**
 * Fastest speed that can still brake to a stop within [distance], from v² = 2ad.
 * Capped at [distance] so the last tick lands on the target instead of stepping past it.
 */
fun arriveSpeed(distance: Double, maxSpeed: Double, maxAccel: Double): Double {
    return minOf(maxSpeed, sqrt(2.0 * maxAccel * distance), distance)
}

/** The change from [current] toward [desired] velocity, limited to [maxForce]. */
fun steerForce(desired: Vec3, current: Vec3, maxForce: Double): Vec3 {
    val steer = desired.subtract(current)
    return if (steer.lengthSqr() > maxForce * maxForce) steer.normalize().scale(maxForce) else steer
}
