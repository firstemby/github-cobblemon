/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

fun <T> orderedKeyframes(keyframes: List<T>, time: (T) -> Float): List<T> {
    require(keyframes.isNotEmpty()) { "At least one keyframe is required" }
    require(keyframes.all { time(it).isFinite() && time(it) >= 0f }) { "Keyframe times must be finite and nonnegative" }
    val ordered = ArrayList<T>(keyframes.size)
    for (keyframe in keyframes.sortedBy(time)) {
        if (ordered.isNotEmpty() && time(ordered.last()) == time(keyframe)) {
            ordered[ordered.lastIndex] = keyframe
        } else {
            ordered.add(keyframe)
        }
    }
    return ordered
}
