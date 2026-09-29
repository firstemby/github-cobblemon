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

data class DollyPosition(
    val from: Vec3,
    val to: Vec3,
    val duration: Float
) : PositionSource() {
    override val type: String = TYPE

    init {
        require(duration.isFinite() && duration > 0f) { "DollyPosition duration must be finite and positive" }
        require(listOf(from.x, from.y, from.z, to.x, to.y, to.z).all { it.isFinite() }) {
            "DollyPosition endpoints must be finite"
        }
    }

    fun evaluate(time: Float): Vec3 {
        val t = (time / duration).coerceIn(0f, 1f)
        return Vec3(
            from.x + (to.x - from.x) * t,
            from.y + (to.y - from.y) * t,
            from.z + (to.z - from.z) * t
        )
    }

    companion object {
        const val TYPE = "dolly"
        fun fromJson(obj: JsonObject): DollyPosition {
            val f = obj.getAsJsonObject("from")
            val t = obj.getAsJsonObject("to")
            return DollyPosition(
                from = Vec3(f.get("x").asDouble, f.get("y").asDouble, f.get("z").asDouble),
                to = Vec3(t.get("x").asDouble, t.get("y").asDouble, t.get("z").asDouble),
                duration = obj.get("duration").asFloat
            )
        }
    }
}
