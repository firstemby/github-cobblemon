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

/** Offset is world-space and ignores actor orientation. */
data class FollowActorPosition(
    val actorId: String,
    val offset: Vec3,
    val fallback: Vec3 = Vec3.ZERO
) : PositionSource() {
    override val type: String = TYPE

    init {
        require(listOf(offset.x, offset.y, offset.z, fallback.x, fallback.y, fallback.z).all { it.isFinite() }) {
            "FollowActorPosition offset and fallback must be finite"
        }
    }

    companion object {
        const val TYPE = "follow_actor"
        fun fromJson(obj: JsonObject): FollowActorPosition {
            val o = obj.getAsJsonObject("offset")
            val fObj = obj.getAsJsonObject("fallback")
            return FollowActorPosition(
                actorId = obj.get("actorId").asString,
                offset = Vec3(o.get("x").asDouble, o.get("y").asDouble, o.get("z").asDouble),
                fallback = if (fObj != null) Vec3(fObj.get("x").asDouble, fObj.get("y").asDouble, fObj.get("z").asDouble) else Vec3.ZERO
            )
        }
    }
}
