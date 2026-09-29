/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.orderedKeyframes
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.google.gson.JsonObject

data class KeyframeRotation(val keyframes: List<Kf>) : RotationSource() {
    override val type: String = TYPE

    data class Kf(val time: Float, val yaw: Float, val pitch: Float)

    init {
        require(keyframes.all { it.yaw.isFinite() && it.pitch.isFinite() }) { "Keyframe rotations must be finite" }
    }

    @Transient private val playbackKeyframes = orderedKeyframes(keyframes) { it.time }

    fun evaluate(time: Float): CameraRotation {
        val keyframes = playbackKeyframes
        if (keyframes.size == 1) return CameraRotation(keyframes[0].yaw, keyframes[0].pitch)
        if (time <= keyframes.first().time) return CameraRotation(keyframes.first().yaw, keyframes.first().pitch)
        if (time >= keyframes.last().time) return CameraRotation(keyframes.last().yaw, keyframes.last().pitch)
        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (time >= a.time && time <= b.time) {
                val d = b.time - a.time
                val t = if (d > 0f) (time - a.time) / d else 0f
                return CameraRotation(
                    (a.yaw + (b.yaw.toDouble() - a.yaw) * t).toFloat(),
                    (a.pitch + (b.pitch.toDouble() - a.pitch) * t).toFloat()
                )
            }
        }
        return CameraRotation(keyframes.last().yaw, keyframes.last().pitch)
    }

    companion object {
        const val TYPE = "keyframe"
        fun fromJson(obj: JsonObject): KeyframeRotation {
            val kfs = obj.getAsJsonArray("keyframes").map { el ->
                val k = el.asJsonObject
                Kf(time = k.get("time").asFloat, yaw = k.get("yaw").asFloat, pitch = k.get("pitch").asFloat)
            }
            return KeyframeRotation(kfs)
        }
    }
}
