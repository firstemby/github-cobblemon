/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

import com.cobblemon.mod.common.api.tween.*

class KeyframeTrack<T : Interpolatable<T>>(
    val keyframes: List<Keyframe<T>>,
    val method: InterpolationMethod = InterpolationMethod.LINEAR,
    val defaultEasing: String = "linear",
    val getEasing: ((T) -> String?)? = null
) : Track<T> {

    override val duration: Float
        get() = keyframes.maxOfOrNull { it.time } ?: 0f

    override fun evaluate(time: Float): T? {
        return Interpolator.interpolate(keyframes, time, method, defaultEasing, getEasing)
    }

    init {
        require(keyframes.isNotEmpty()) { "KeyframeTrack must have at least one keyframe" }
        require(keyframes.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "KeyframeTrack keyframes must be sorted by time"
        }
    }
}
