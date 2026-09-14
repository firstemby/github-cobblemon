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
import net.minecraft.world.phys.AABB
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.atan2

data class OrbitPosition(
    val center: Vec3,
    val radius: Float,
    val angularVelocityDegPerSec: Float,
    val startAngleDeg: Float = 0f,
    val height: Float = 0f,
    val actorIds: List<String> = emptyList(),
    val padding: Float = 2f,
    val framingFov: Float = 60f,
    val bearingActorId: String? = null,
    val avoidBlocks: Boolean = true,
    val avoidanceSmoothing: Float = 0f,
    val fitArena: Boolean = false,
    val arenaWidth: Float? = null,
    val arenaDepth: Float? = null,
    val rememberProgress: Boolean = false
) : PositionSource() {
    override val type: String = TYPE

    init {
        require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite()) { "Orbit center must be finite" }
        require(radius > 0f) { "OrbitPosition radius must be positive" }
        require(listOf(radius, angularVelocityDegPerSec, startAngleDeg, height, padding, framingFov).all { it.isFinite() })
        require(padding >= 0f && framingFov in 10f..120f && actorIds.size <= 16)
        require(avoidanceSmoothing.isFinite() && avoidanceSmoothing in 0f..8f)
        require(listOfNotNull(arenaWidth, arenaDepth).all { it.isFinite() && it in 2f..128f })
    }

    val hasArena get() = fitArena && arenaWidth != null && arenaDepth != null
    fun focus(bounds: AABB?) = if (hasArena) center else bounds?.center ?: center

    fun evaluate(time: Float, bounds: AABB? = null, lensFov: Float = framingFov, bearingPosition: Vec3? = null): Vec3 {
        require(time.isFinite()) { "Orbit time must be finite" }
        val focus = focus(bounds)
        val bearing = bearingPosition?.subtract(focus)?.takeIf { it.horizontalDistanceSqr() > 0.000001 }
            ?.let { atan2(it.z, it.x) } ?: 0.0
        val angleDeg = (startAngleDeg.toDouble() + angularVelocityDegPerSec.toDouble() * time.toDouble()) % 360.0
        val angleRad = bearing + Math.toRadians(angleDeg)
        if (hasArena) return Vec3(
            focus.x + max(radius, arenaWidth!! / 2 + padding) * cos(angleRad),
            focus.y + height,
            focus.z + max(radius, arenaDepth!! / 2 + padding) * sin(angleRad))
        val fitFov = lensFov.takeIf { it.isFinite() }?.coerceIn(1f, 179f) ?: framingFov
        val fitRadius = bounds?.let {
            sqrt(it.xsize * it.xsize + it.ysize * it.ysize + it.zsize * it.zsize) / 2 / sin(Math.toRadians(fitFov / 2.0)) + padding
        } ?: radius.toDouble()
        val distance = max(radius.toDouble(), fitRadius)
        return Vec3(
            focus.x + distance * cos(angleRad),
            focus.y + height,
            focus.z + distance * sin(angleRad)
        )
    }

    companion object {
        const val TYPE = "orbit"
        fun fromJson(obj: JsonObject): OrbitPosition {
            val c = obj.getAsJsonObject("center")
            return OrbitPosition(
                center = Vec3(c.get("x").asDouble, c.get("y").asDouble, c.get("z").asDouble),
                radius = obj.get("radius").asFloat,
                angularVelocityDegPerSec = obj.get("angularVelocity").asFloat,
                startAngleDeg = obj.get("startAngle")?.asFloat ?: 0f,
                height = obj.get("height")?.asFloat ?: 0f,
                actorIds = obj.getAsJsonArray("actorIds")?.map { it.asString } ?: emptyList(),
                padding = obj.get("padding")?.asFloat ?: 2f,
                framingFov = obj.get("framingFov")?.asFloat ?: 60f,
                bearingActorId = obj.get("bearingActorId")?.asString,
                avoidBlocks = obj.get("avoidBlocks")?.asBoolean ?: true,
                avoidanceSmoothing = obj.get("avoidanceSmoothing")?.asFloat ?: 0f,
                fitArena = obj.get("fitArena")?.asBoolean ?: false,
                arenaWidth = obj.get("arenaWidth")?.asFloat,
                arenaDepth = obj.get("arenaDepth")?.asFloat,
                rememberProgress = obj.get("rememberProgress")?.asBoolean ?: false
            )
        }
    }
}
