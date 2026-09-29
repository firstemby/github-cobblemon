/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.FovSource
import com.cobblemon.mod.common.api.cinematic.model.orderedKeyframes
import com.google.gson.JsonObject

data class KeyframeFov(val keyframes: List<Kf>) : FovSource() {
    override val type: String = TYPE

    data class Kf(val time: Float, val fov: Float)

    init {
        keyframes.forEach { require(it.fov in 1f..179f) { "Fov must be in [1, 179], got ${it.fov}" } }
    }

    @Transient private val playbackKeyframes = orderedKeyframes(keyframes) { it.time }

    fun evaluate(time: Float): Float {
        val keyframes = playbackKeyframes
        if (keyframes.size == 1) return keyframes[0].fov
        if (time <= keyframes.first().time) return keyframes.first().fov
        if (time >= keyframes.last().time) return keyframes.last().fov
        for (i in 0 until keyframes.size - 1) {
            val a = keyframes[i]
            val b = keyframes[i + 1]
            if (time >= a.time && time <= b.time) {
                val d = b.time - a.time
                val t = if (d > 0f) (time - a.time) / d else 0f
                return a.fov + (b.fov - a.fov) * t
            }
        }
        return keyframes.last().fov
    }

    companion object {
        const val TYPE = "keyframe"
        fun fromJson(obj: JsonObject): KeyframeFov {
            val kfs = obj.getAsJsonArray("keyframes").map { el ->
                val k = el.asJsonObject
                Kf(time = k.get("time").asFloat, fov = k.get("fov").asFloat)
            }
            return KeyframeFov(kfs)
        }
    }
}
