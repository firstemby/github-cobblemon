/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.animation

import kotlin.math.*

object Easing {
    fun resolve(name: String): (Float) -> Float = when (name) {
        "linear" -> ::linear
        "ease_in" -> ::easeIn
        "ease_out" -> ::easeOut
        "ease_in_out" -> ::easeInOut
        "bounce" -> ::bounce
        "elastic" -> ::elastic
        else -> ::easeOut
    }

    fun linear(t: Float): Float = t
    fun easeIn(t: Float): Float = t * t
    fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
    fun easeInOut(t: Float): Float = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

    fun bounce(t: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        var x = t
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { x -= 1.5f / d1; n1 * x * x + 0.75f }
            x < 2.5f / d1 -> { x -= 2.25f / d1; n1 * x * x + 0.9375f }
            else -> { x -= 2.625f / d1; n1 * x * x + 0.984375f }
        }
    }

    fun elastic(t: Float): Float {
        if (t == 0f || t == 1f) return t
        val c4 = (2f * PI / 3f).toFloat()
        return 2f.pow(-10f * t) * sin((t * 10f - 0.75f) * c4) + 1f
    }
}
