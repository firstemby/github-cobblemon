/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class OffsetModifier(
    val positionOffset: Vec3 = Vec3.ZERO,
    val yawOffset: Float = 0f,
    val pitchOffset: Float = 0f,
    val fovOffset: Float = 0f,
    val rollOffset: Float = 0f
) : ShotModifier() {
    override val type: String = TYPE

    init {
        require(positionOffset.x.isFinite() && positionOffset.y.isFinite() && positionOffset.z.isFinite()) { "Position offset must be finite" }
        require(listOf(yawOffset, pitchOffset, fovOffset, rollOffset).all { it.isFinite() }) { "Camera offsets must be finite" }
    }

    companion object {
        const val TYPE = "offset"
        fun fromJson(obj: JsonObject): OffsetModifier {
            val p = obj.getAsJsonObject("position")
            val posOff = if (p != null) Vec3(p.get("x").asDouble, p.get("y").asDouble, p.get("z").asDouble) else Vec3.ZERO
            return OffsetModifier(
                positionOffset = posOff,
                yawOffset = obj.get("yaw")?.asFloat ?: 0f,
                pitchOffset = obj.get("pitch")?.asFloat ?: 0f,
                fovOffset = obj.get("fov")?.asFloat ?: 0f,
                rollOffset = obj.get("roll")?.asFloat ?: 0f
            )
        }
    }
}
