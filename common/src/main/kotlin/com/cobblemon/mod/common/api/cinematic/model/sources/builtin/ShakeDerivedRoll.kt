/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import kotlin.math.sin
import com.cobblemon.mod.common.api.cinematic.model.sources.RollSource
import com.google.gson.JsonObject

data class ShakeDerivedRoll(
    val amplitudeDeg: Float,
    val frequencyHz: Float = 8f,
    val decaySecs: Float = 0f
) : RollSource() {
    override val type: String = "shake"

    init {
        require(amplitudeDeg.isFinite() && amplitudeDeg >= 0f)
        require(frequencyHz in 0f..60f)
        require(decaySecs.isFinite() && decaySecs >= 0f)
    }

    fun evaluate(time: Float): Float {
        val decay = if (decaySecs <= 0f) 1f else (1f - (time / decaySecs)).coerceAtLeast(0f)
        if (decay <= 0f) return 0f
        val t = time.toDouble() * frequencyHz
        val n = (sin(1.0 * t) + sin(2.3 * t + 1.3) + sin(3.7 * t + 2.7)) / 3.0
        return (n.toFloat() * amplitudeDeg * decay)
    }

    companion object {
        const val TYPE = "shake"
        fun fromJson(obj: JsonObject): ShakeDerivedRoll = ShakeDerivedRoll(
            amplitudeDeg = obj.get("amplitude").asFloat,
            frequencyHz = obj.get("frequency")?.asFloat ?: 8f,
            decaySecs = obj.get("decay")?.asFloat ?: 0f
        )
    }
}
