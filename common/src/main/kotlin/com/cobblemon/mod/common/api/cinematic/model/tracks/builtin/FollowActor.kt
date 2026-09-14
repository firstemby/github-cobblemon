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

/** Zero duration keeps follow evaluation active throughout the beat. */
data class FollowActor(
    val targetActorId: String,
    val offset: Vec3,
    val lagSecs: Float = 0f
) : ActorTrack() {
    override val trackType: String = TYPE
    override val duration: Float = 0f

    init {
        require(offset.x.isFinite() && offset.y.isFinite() && offset.z.isFinite()) { "Follow offset must be finite" }
        require(lagSecs.isFinite() && lagSecs >= 0f) { "FollowActor lagSecs must be non-negative" }
    }

    companion object {
        const val TYPE = "follow_actor"
        fun fromJson(obj: JsonObject): FollowActor {
            val o = obj.getAsJsonObject("offset")
            return FollowActor(
                targetActorId = obj.get("targetActorId").asString,
                offset = Vec3(o.get("x").asDouble, o.get("y").asDouble, o.get("z").asDouble),
                lagSecs = obj.get("lag")?.asFloat ?: 0f
            )
        }
    }
}
