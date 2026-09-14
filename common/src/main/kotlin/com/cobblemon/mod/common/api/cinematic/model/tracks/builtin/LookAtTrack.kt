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
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

data class LookAtTrack(
    val target: Vec3
) : ActorTrack() {
    override val trackType: String = "look_at"
    override val duration: Float = 0f

    init {
        require(target.x.isFinite() && target.y.isFinite() && target.z.isFinite()) { "Look-at target must be finite" }
    }

    fun computeYRot(fromPos: Vec3): Float {
        val dx = target.x - fromPos.x
        val dz = target.z - fromPos.z
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }

    fun computeXRot(fromPos: Vec3): Float {
        val dx = target.x - fromPos.x
        val dy = target.y - fromPos.y
        val dz = target.z - fromPos.z
        val horizontal = sqrt(dx * dx + dz * dz)
        return Math.toDegrees(-atan2(dy, horizontal)).toFloat()
    }

    companion object {
        const val TYPE = "look_at"
        fun fromJson(obj: JsonObject): LookAtTrack {
            val targetObj = obj.getAsJsonObject("target")
            val target = Vec3(targetObj.get("x").asDouble, targetObj.get("y").asDouble, targetObj.get("z").asDouble)
            return LookAtTrack(target)
        }
    }
}
