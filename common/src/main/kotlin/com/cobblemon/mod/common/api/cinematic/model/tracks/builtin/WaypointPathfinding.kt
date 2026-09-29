/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.tracks.builtin

import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.google.gson.JsonObject
import java.util.Locale
import kotlin.math.abs
import net.minecraft.world.phys.Vec3

data class WaypointPathfinding(
    val controlPoints: List<Vec3>,
    val speedMultiplier: Float = 1f,
    val durationSecs: Float = 30f,
    val travelMode: TravelMode = TravelMode.AUTO
) : ActorTrack() {
    override val trackType = TYPE
    override val duration = durationSecs

    init {
        require(controlPoints.size in 2..64) { "Pathfinding needs 2 to 64 waypoints" }
        require(controlPoints.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() &&
            abs(it.x) <= 30_000_000 && abs(it.z) <= 30_000_000 }) { "Waypoints must be finite world positions" }
        require(controlPoints.zipWithNext().all { (a, b) -> a.distanceTo(b) <= 64 }) { "Consecutive waypoints must be within 64 blocks" }
        require(speedMultiplier.isFinite() && speedMultiplier in 0.1f..4f) { "Native movement speed multiplier must be between 0.1 and 4" }
        require(durationSecs.isFinite() && durationSecs in 0.05f..600f) { "Pathfinding time budget must be between 0.05 and 600 seconds" }
    }

    enum class TravelMode { AUTO, GROUND, FLIGHT }

    companion object {
        const val TYPE = "pathfinding"
        fun fromJson(data: JsonObject) = WaypointPathfinding(
            controlPoints = data.getAsJsonArray("controlPoints").map {
                val point = it.asJsonObject
                Vec3(point.get("x").asDouble, point.get("y").asDouble, point.get("z").asDouble)
            },
            speedMultiplier = data.get("speedMultiplier")?.asFloat ?: 1f,
            durationSecs = data.get("duration")?.asFloat ?: 30f,
            travelMode = data.get("travelMode")?.asString?.let { TravelMode.valueOf(it.uppercase(Locale.ROOT)) } ?: TravelMode.AUTO
        )
    }
}
