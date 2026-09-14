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

data class AnimationTrack(
    val cues: List<Cue>
) : ActorTrack() {
    override val trackType: String = "animation"
    override val duration: Float = cues.maxOfOrNull { it.time } ?: 0f

    data class Cue(val time: Float, val animationName: String) {
        init {
            require(time.isFinite() && time >= 0f) { "Animation cue time must be finite and nonnegative" }
            require(animationName.isNotBlank()) { "Animation name must be nonblank" }
        }
    }

    companion object {
        const val TYPE = "animation"
        fun fromJson(obj: JsonObject): AnimationTrack {
            val cues = obj.getAsJsonArray("cues").map { el ->
                val cueObj = el.asJsonObject
                Cue(time = cueObj.get("time").asFloat, animationName = cueObj.get("name").asString)
            }
            return AnimationTrack(cues)
        }
    }
}
