/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

fun Vec3.asInterpolatable(): Vec3Interpolatable = Vec3Interpolatable(this)

data class Vec3Interpolatable(val vec: Vec3) : Interpolatable<Vec3Interpolatable> {
    override fun lerp(other: Vec3Interpolatable, t: Float): Vec3Interpolatable {
        return Vec3Interpolatable(Vec3(
            Mth.lerp(t.toDouble(), vec.x, other.vec.x),
            Mth.lerp(t.toDouble(), vec.y, other.vec.y),
            Mth.lerp(t.toDouble(), vec.z, other.vec.z)
        ))
    }

    override fun add(other: Vec3Interpolatable): Vec3Interpolatable {
        return Vec3Interpolatable(vec.add(other.vec))
    }

    override fun subtract(other: Vec3Interpolatable): Vec3Interpolatable {
        return Vec3Interpolatable(vec.subtract(other.vec))
    }

    override fun scale(scalar: Float): Vec3Interpolatable {
        return Vec3Interpolatable(vec.scale(scalar.toDouble()))
    }
}
