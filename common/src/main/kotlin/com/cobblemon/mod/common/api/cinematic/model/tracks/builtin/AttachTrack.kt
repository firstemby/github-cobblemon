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

/** Position-only attachment; zero duration keeps evaluation active throughout the beat. */
data class AttachTrack(
    val parentActorId: String,
    val offset: Vec3 = Vec3.ZERO
) : ActorTrack() {
    override val trackType: String = TYPE
    override val duration: Float = 0f

    init {
        require(offset.x.isFinite() && offset.y.isFinite() && offset.z.isFinite()) { "Attachment offset must be finite" }
    }

    companion object {
        const val TYPE = "attach"
        fun fromJson(obj: JsonObject): AttachTrack {
            val o = obj.get("offset")?.asJsonObject
            val off = if (o != null) Vec3(o.get("x").asDouble, o.get("y").asDouble, o.get("z").asDouble) else Vec3.ZERO
            return AttachTrack(
                parentActorId = obj.get("parentActorId").asString,
                offset = off
            )
        }
    }
}
