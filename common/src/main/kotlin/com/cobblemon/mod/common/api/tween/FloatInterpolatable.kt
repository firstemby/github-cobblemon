/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

import net.minecraft.util.Mth

fun Float.asInterpolatable(): FloatInterpolatable = FloatInterpolatable(this)

data class FloatInterpolatable(val value: Float) : Interpolatable<FloatInterpolatable> {
    override fun lerp(other: FloatInterpolatable, t: Float): FloatInterpolatable {
        return FloatInterpolatable(Mth.lerp(t, value, other.value))
    }

    override fun add(other: FloatInterpolatable): FloatInterpolatable {
        return FloatInterpolatable(value + other.value)
    }

    override fun subtract(other: FloatInterpolatable): FloatInterpolatable {
        return FloatInterpolatable(value - other.value)
    }

    override fun scale(scalar: Float): FloatInterpolatable {
        return FloatInterpolatable(value * scalar)
    }
}
