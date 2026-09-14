/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.tracks.builtin

import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.cobblemon.mod.common.entity.PoseType
import com.google.gson.JsonObject

data class PoseLockTrack(
    val pose: PoseType
) : ActorTrack() {
    override val trackType: String = TYPE
    override val duration: Float = 0f

    companion object {
        const val TYPE = "pose_lock"
        fun fromJson(obj: JsonObject): PoseLockTrack {
            val name = obj.get("pose").asString
            val pose = PoseType.valueOf(name.uppercase())
            return PoseLockTrack(pose)
        }
    }
}
