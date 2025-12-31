/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.util.getBooleanOrNull
import com.cobblemon.mod.common.util.getDoubleOrNull

/**
 * Used to maintain some settings around how the entity should wander this tick. Anything left null
 * will fall back to defaults specified by the logic of the WanderAround objective.
 *
 * @author Hiroku
 * @since July 18, 2025
 */
class CobblemonWanderControl(
    var center: WanderCenter? = null,
    var maxAttempts: Int? = null, // How many times to try to find a suitable point before giving up on that tick.
    var allowLand: Boolean? = null,
    var allowWater: Boolean? = null,
    var allowAir: Boolean? = null,
    var wanderSpeed: Float? = null,
) {
    class WanderCenter(val x: Number, val y: Number, val z: Number, val minRange: Float, val maxRange: Float)

    @Transient
    val struct: QueryStruct = QueryStruct(hashMapOf())
        .addFunction("center_x") { DoubleValue(center?.x ?: 0.0) }
        .addFunction("center_y") { DoubleValue(center?.y ?: 0.0) }
        .addFunction("center_z") { DoubleValue(center?.z ?: 0.0) }
        .addFunction("max_attempts") { DoubleValue(maxAttempts?.toDouble() ?: 0.0) }
        .addFunction("min_range") { DoubleValue(center?.minRange ?: 0.0) }
        .addFunction("max_range") { DoubleValue(center?.maxRange ?: 0.0) }
        .addFunction("allow_land") { DoubleValue(if (allowLand == true) 1.0 else 0.0) }
        .addFunction("allow_water") { DoubleValue(if (allowWater == true) 1.0 else 0.0) }
        .addFunction("allow_air") { DoubleValue(if (allowAir == true) 1.0 else 0.0) }
        .addFunction("wander_speed") { DoubleValue(wanderSpeed ?: 0.0) }
        .addFunction("set_center") { params ->
            if (params.params.size < 3) {
                throw IllegalArgumentException("set_center requires at least 3 parameters: x, y, z")
            }
            val x = params.getDouble(0)
            val y = params.getDouble(1)
            val z = params.getDouble(2)
            val minRange = params.getDoubleOrNull(3) ?: 0.0
            val maxRange = params.getDoubleOrNull(4) ?: Float.MAX_VALUE.toDouble()
            center = WanderCenter(
                x = x,
                y = y,
                z = z,
                minRange = minRange.toFloat(),
                maxRange = maxRange.toFloat(),
            )
        }
        .addFunction("set_max_attempts") { params -> maxAttempts = params.getDoubleOrNull(0)?.toInt() ?: 4 }
        .addFunction("set_allow_land") { params -> allowLand = params.getBooleanOrNull(0) ?: true }
        .addFunction("set_allow_water") { params -> allowWater = params.getBooleanOrNull(0) ?: false }
        .addFunction("set_allow_air") { params -> allowAir = params.getBooleanOrNull(0) ?: false }
        .addFunction("set_wander_speed") { params -> wanderSpeed = params.getDoubleOrNull(0)?.toFloat() ?: 0.35F }
}

