/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

/** The axis is not normalized; callers must supply a unit vector for predictable speed. */
data class TruckPosition(
    val origin: Vec3,
    val axis: Vec3,
    val velocity: Float
) : PositionSource() {
    override val type: String = TYPE

    init {
        require(velocity.isFinite()) { "TruckPosition velocity must be finite" }
        require(listOf(origin.x, origin.y, origin.z, axis.x, axis.y, axis.z).all { it.isFinite() }) {
            "TruckPosition origin and axis must be finite"
        }
    }

    fun evaluate(time: Float): Vec3 {
        val dist = velocity.toDouble() * time
        return Vec3(
            origin.x + axis.x * dist,
            origin.y + axis.y * dist,
            origin.z + axis.z * dist
        )
    }

    companion object {
        const val TYPE = "truck"
        fun fromJson(obj: JsonObject): TruckPosition {
            val o = obj.getAsJsonObject("origin")
            val a = obj.getAsJsonObject("axis")
            return TruckPosition(
                origin = Vec3(o.get("x").asDouble, o.get("y").asDouble, o.get("z").asDouble),
                axis = Vec3(a.get("x").asDouble, a.get("y").asDouble, a.get("z").asDouble),
                velocity = obj.get("velocity").asFloat
            )
        }
    }
}
