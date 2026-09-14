/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.CinematicSpline
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class SplinePosition(
    val controlPoints: List<Vec3>,
    val duration: Float
) : PositionSource() {
    override val type: String = TYPE

    init {
        require(controlPoints.size >= 2) { "SplinePosition needs at least 2 control points" }
        require(duration.isFinite() && duration > 0f) { "SplinePosition duration must be positive" }
    }

    @Transient private val path = CinematicSpline(controlPoints)
    val pathLength: Double get() = path.length

    fun evaluate(time: Float): Vec3 = path.position((time / duration).toDouble().coerceIn(0.0, 1.0))

    companion object {
        const val TYPE = "spline"
        fun fromJson(obj: JsonObject): SplinePosition {
            val points = obj.getAsJsonArray("controlPoints").map { el ->
                val p = el.asJsonObject
                Vec3(p.get("x").asDouble, p.get("y").asDouble, p.get("z").asDouble)
            }
            val duration = obj.get("duration").asFloat
            return SplinePosition(points, duration)
        }
    }
}
