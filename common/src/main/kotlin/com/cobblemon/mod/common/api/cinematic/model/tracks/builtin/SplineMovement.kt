/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.tracks.builtin

import kotlin.math.atan2
import kotlin.math.sqrt
import java.util.Locale
import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class SplineMovement(
    val controlPoints: List<Vec3>,
    val durationSecs: Float,
    val facing: PathFacing = PathFacing.GROUND,
    val headingOffset: Float = 0f
) : ActorTrack() {
    override val trackType: String = TYPE
    override val duration: Float = durationSecs

    init {
        require(controlPoints.size >= 2) { "SplineMovement needs at least 2 control points" }
        require(durationSecs.isFinite() && durationSecs > 0f) { "SplineMovement duration must be positive" }
        require(headingOffset.isFinite()) { "SplineMovement heading offset must be finite" }
    }

    @Transient private val path = CinematicSpline(controlPoints)
    val pathLength: Double get() = path.length

    fun evaluate(time: Float): Vec3 = path.position((time / durationSecs).toDouble().coerceIn(0.0, 1.0))

    fun facingAt(time: Float): Pair<Float, Float>? {
        if (facing == PathFacing.FIXED) return null
        val direction = path.tangent((time / durationSecs).toDouble())
        if (direction.lengthSqr() < 1e-12) return null
        val yaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat() + headingOffset
        val pitch = if (facing == PathFacing.FLIGHT) -Math.toDegrees(atan2(direction.y,
            sqrt(direction.x * direction.x + direction.z * direction.z))).toFloat() else 0f
        return yaw to pitch
    }

    companion object {
        const val TYPE = "spline_movement"
        fun fromJson(obj: JsonObject): SplineMovement {
            val points = obj.getAsJsonArray("controlPoints").map { el ->
                val p = el.asJsonObject
                Vec3(p.get("x").asDouble, p.get("y").asDouble, p.get("z").asDouble)
            }
            val d = obj.get("duration").asFloat
            val facing = obj.get("facing")?.asString?.let { PathFacing.valueOf(it.uppercase(Locale.ROOT)) } ?: PathFacing.GROUND
            return SplineMovement(points, d, facing, obj.get("headingOffset")?.asFloat ?: 0f)
        }
    }
}

enum class PathFacing { FIXED, GROUND, FLIGHT }
