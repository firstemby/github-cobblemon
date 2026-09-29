/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.RollSource
import com.cobblemon.mod.common.api.cinematic.model.orderedKeyframes
import com.google.gson.JsonObject

data class KeyframeRoll(val keyframes: List<Kf>) : RollSource() {
    override val type: String = TYPE

    data class Kf(val time: Float, val roll: Float)

    init {
        require(keyframes.all { it.roll.isFinite() }) { "Keyframe roll must be finite" }
    }

    @Transient private val playbackKeyframes = orderedKeyframes(keyframes) { it.time }

    fun evaluate(time: Float): Float {
        val keyframes = playbackKeyframes
        if (keyframes.size == 1) return keyframes[0].roll
        if (time <= keyframes.first().time) return keyframes.first().roll
        if (time >= keyframes.last().time) return keyframes.last().roll
        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (time >= a.time && time <= b.time) {
                val d = b.time - a.time
                val t = if (d > 0f) (time - a.time) / d else 0f
                return (a.roll + (b.roll.toDouble() - a.roll) * t).toFloat()
            }
        }
        return keyframes.last().roll
    }

    companion object {
        const val TYPE = "keyframe"
        fun fromJson(obj: JsonObject): KeyframeRoll {
            val kfs = obj.getAsJsonArray("keyframes").map { el ->
                val k = el.asJsonObject
                Kf(time = k.get("time").asFloat, roll = k.get("roll").asFloat)
            }
            return KeyframeRoll(kfs)
        }
    }
}
