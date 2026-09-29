/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

data class LookAlongPath(
    val epsilon: Float = 1f / 60f
) : RotationSource() {
    override val type: String = "look_along_path"

    init {
        require(epsilon.isFinite() && epsilon > 0f) { "LookAlongPath epsilon must be finite and positive" }
    }

    fun computeRotation(current: Vec3, ahead: Vec3): CameraRotation {
        val dx = ahead.x - current.x
        val dy = ahead.y - current.y
        val dz = ahead.z - current.z
        if (dx * dx + dy * dy + dz * dz < 1e-8) {
            // Motion near zero — return forward-facing default to avoid NaN.
            return CameraRotation(0f, 0f)
        }
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horizontal)).toFloat()
        return CameraRotation(yaw = yaw, pitch = pitch)
    }

    companion object {
        const val TYPE = "look_along_path"
        fun fromJson(obj: JsonObject): LookAlongPath = LookAlongPath(
            epsilon = obj.get("epsilon")?.asFloat ?: (1f / 60f)
        )
    }
}
