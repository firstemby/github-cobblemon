/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.math

/**
 * Fastest speed that the speed ease can still stop from within [distance]. The ease always takes [blendTicks]
 * to stop and averages half the speed on the way, so it covers speed × blendTicks / 2.
 * Capped at [distance] so the last tick lands on the target instead of stepping past it.
 */
fun arriveSpeed(distance: Double, maxSpeed: Double, blendTicks: Double): Double {
    return minOf(maxSpeed, 2 * distance / blendTicks, distance)
}

/**
 * Fastest speed at [distance] from somewhere that has to be passed at [slowSpeed].
 */
fun slowDownSpeed(distance: Double, slowSpeed: Double, blendTicks: Double): Double {
    return maxOf(slowSpeed, 2 * distance / blendTicks - slowSpeed)
}
