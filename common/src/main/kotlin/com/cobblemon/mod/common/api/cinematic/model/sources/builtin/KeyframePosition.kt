/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.cinematic.model.orderedKeyframes
import com.cobblemon.mod.common.api.tween.EasingRegistry
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class KeyframePosition(
    val keyframes: List<Kf>,
    val defaultEasing: String = "linear"
) : PositionSource() {
    override val type: String = "keyframe"

    init {
        require(keyframes.all { it.position.x.isFinite() && it.position.y.isFinite() && it.position.z.isFinite() }) {
            "Keyframe positions must be finite"
        }
    }

    // Keep authoring indices intact; coincident keyframes use the last authored value during playback.
    @Transient private val playbackKeyframes = orderedKeyframes(keyframes) { it.time }

    data class Kf(val time: Float, val position: Vec3, val easing: String? = null)

    fun evaluate(time: Float): Vec3 {
        if (time <= playbackKeyframes.first().time) return playbackKeyframes.first().position
        for (index in 1 until playbackKeyframes.size) {
            val start = playbackKeyframes[index - 1]
            val end = playbackKeyframes[index]
            if (time <= end.time) {
                val progress = ((time - start.time) / (end.time - start.time)).coerceIn(0f, 1f)
                val eased = EasingRegistry.getEasingFunction(end.easing ?: defaultEasing)(progress)
                return start.position.lerp(end.position, eased.toDouble())
            }
        }
        return playbackKeyframes.last().position
    }

    companion object {
        const val TYPE = "keyframe"
        fun fromJson(obj: JsonObject): KeyframePosition {
            val kfArray = obj.getAsJsonArray("keyframes") ?: throw IllegalArgumentException("KeyframePosition missing 'keyframes'")
            val kfs = kfArray.map { el ->
                val kfObj = el.asJsonObject
                val pos = kfObj.getAsJsonObject("position")
                Kf(
                    time = kfObj.get("time").asFloat,
                    position = Vec3(pos.get("x").asDouble, pos.get("y").asDouble, pos.get("z").asDouble),
                    easing = kfObj.get("easing")?.asString
                )
            }
            val easing = obj.get("easing")?.asString ?: "linear"
            return KeyframePosition(kfs, easing)
        }
    }
}
