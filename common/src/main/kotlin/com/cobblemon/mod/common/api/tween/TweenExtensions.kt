/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

import net.minecraft.world.phys.Vec3

fun tweenFloat(
    from: Float,
    to: Float,
    duration: Float,
    easing: String = "linear",
    onUpdate: (Float) -> Unit
): Tween<FloatInterpolatable> {
    return Tween.of(FloatInterpolatable(from))
        .to(FloatInterpolatable(to), duration, easing)
        .onUpdate { onUpdate(it.value) }
        .build()
}

fun tweenVec3(
    from: Vec3,
    to: Vec3,
    duration: Float,
    easing: String = "linear",
    onUpdate: (Vec3) -> Unit
): Tween<Vec3Interpolatable> {
    return Tween.of(Vec3Interpolatable(from))
        .to(Vec3Interpolatable(to), duration, easing)
        .onUpdate { onUpdate(it.vec) }
        .build()
}
