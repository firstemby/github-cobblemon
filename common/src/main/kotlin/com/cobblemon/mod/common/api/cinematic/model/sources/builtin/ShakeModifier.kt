/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import kotlin.math.sin
import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier
import com.google.gson.JsonObject

data class ShakeModifier(
    val positionAmplitude: Float = 0f,
    val rotationAmplitudeDeg: Float = 0f,
    val frequencyHz: Float = 8f,
    val decaySecs: Float = 0f
) : ShotModifier() {
    override val type: String = TYPE

    init {
        require(positionAmplitude.isFinite() && positionAmplitude >= 0f) { "positionAmplitude must be >= 0" }
        require(rotationAmplitudeDeg.isFinite() && rotationAmplitudeDeg >= 0f) { "rotationAmplitudeDeg must be >= 0" }
        require(frequencyHz in 0f..60f) { "frequency must be in [0, 60] Hz" }
        require(decaySecs.isFinite() && decaySecs >= 0f) { "decaySecs must be >= 0" }
    }

    fun noise3(time: Float): Triple<Float, Float, Float> {
        val t = time.toDouble() * frequencyHz
        fun s(a: Double, b: Double, c: Double) =
            (sin(a * t) + sin(b * t + 1.3) + sin(c * t + 2.7)) / 3.0
        return Triple(
            s(1.0, 2.3, 3.7).toFloat(),
            s(1.1, 2.7, 3.3).toFloat(),
            s(0.9, 2.1, 4.1).toFloat()
        )
    }

    fun decayFactor(time: Float): Float =
        if (decaySecs <= 0f) 1f else (1f - (time / decaySecs)).coerceAtLeast(0f)

    companion object {
        const val TYPE = "shake"
        fun fromJson(obj: JsonObject): ShakeModifier = ShakeModifier(
            positionAmplitude = obj.get("positionAmplitude")?.asFloat ?: 0f,
            rotationAmplitudeDeg = obj.get("rotationAmplitude")?.asFloat ?: 0f,
            frequencyHz = obj.get("frequency")?.asFloat ?: 8f,
            decaySecs = obj.get("decay")?.asFloat ?: 0f
        )
    }
}
