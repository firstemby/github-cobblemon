/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.tracks.builtin

import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack
import com.cobblemon.mod.common.api.cinematic.model.orderedKeyframes
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

/** Movement does not drive rotation; pair with a LookAtTrack to orient the actor. */
data class KeyframeMovement(
    val keyframes: List<Kf>
) : ActorTrack() {
    override val trackType: String = "keyframe_movement"
    override val duration: Float = keyframes.maxOfOrNull { it.time } ?: 0f

    init {
        require(keyframes.all { it.position.x.isFinite() && it.position.y.isFinite() && it.position.z.isFinite() }) {
            "Keyframe positions must be finite"
        }
    }

    @Transient private val playbackKeyframes = orderedKeyframes(keyframes) { it.time }

    data class Kf(val time: Float, val position: Vec3)

    fun evaluate(time: Float): Vec3 {
        val keyframes = playbackKeyframes
        if (keyframes.size == 1) return keyframes[0].position
        if (time <= keyframes.first().time) return keyframes.first().position
        if (time >= keyframes.last().time) return keyframes.last().position

        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (time >= a.time && time <= b.time) {
                val duration = b.time - a.time
                val t = if (duration > 0f) (time - a.time) / duration else 0f
                return Vec3(
                    a.position.x + (b.position.x - a.position.x) * t,
                    a.position.y + (b.position.y - a.position.y) * t,
                    a.position.z + (b.position.z - a.position.z) * t
                )
            }
        }
        return keyframes.last().position
    }

    companion object {
        const val TYPE = "keyframe_movement"
        fun fromJson(obj: JsonObject): KeyframeMovement {
            val kfs = obj.getAsJsonArray("keyframes").map { el ->
                val kfObj = el.asJsonObject
                val pos = kfObj.getAsJsonObject("position")
                Kf(
                    time = kfObj.get("time").asFloat,
                    position = Vec3(pos.get("x").asDouble, pos.get("y").asDouble, pos.get("z").asDouble)
                )
            }
            return KeyframeMovement(kfs)
        }
    }
}
