/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import kotlin.math.cos
import kotlin.math.sin
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class CranePosition(
    val pivot: Vec3,
    val armLength: Float,
    val startAngleDeg: Float,
    val angularVelocityDegPerSec: Float,
    val height: Float = 0f
) : PositionSource() {
    override val type: String = TYPE

    init {
        require(armLength.isFinite() && armLength > 0f) { "CranePosition armLength must be finite and positive" }
        require(startAngleDeg.isFinite() && angularVelocityDegPerSec.isFinite() && height.isFinite()) {
            "CranePosition angles, speed and height must be finite"
        }
        require(pivot.x.isFinite() && pivot.y.isFinite() && pivot.z.isFinite()) { "CranePosition pivot must be finite" }
    }

    fun evaluate(time: Float): Vec3 {
        val angleRad = Math.toRadians(startAngleDeg + angularVelocityDegPerSec.toDouble() * time)
        return Vec3(
            pivot.x + armLength * cos(angleRad),
            pivot.y + height,
            pivot.z + armLength * sin(angleRad)
        )
    }

    companion object {
        const val TYPE = "crane"
        fun fromJson(obj: JsonObject): CranePosition {
            val p = obj.getAsJsonObject("pivot")
            return CranePosition(
                pivot = Vec3(p.get("x").asDouble, p.get("y").asDouble, p.get("z").asDouble),
                armLength = obj.get("armLength").asFloat,
                startAngleDeg = obj.get("startAngle")?.asFloat ?: 0f,
                angularVelocityDegPerSec = obj.get("angularVelocity").asFloat,
                height = obj.get("height")?.asFloat ?: 0f
            )
        }
    }
}
