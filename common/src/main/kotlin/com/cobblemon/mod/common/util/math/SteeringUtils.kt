/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.math

import kotlin.math.sqrt

/**
 * Fastest speed that can still brake to a stop within [distance], from v² = 2ad.
 * Capped at [distance] so the last tick lands on the target instead of stepping past it.
 */
fun arriveSpeed(distance: Double, maxSpeed: Double, maxAccel: Double): Double {
    return minOf(maxSpeed, sqrt(2.0 * maxAccel * distance), distance)
}
