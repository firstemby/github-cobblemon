/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

data class Keyframe<T>(
    val time: Float,
    val value: T
)

data class Segment<T>(
    val startValue: T,
    val endValue: T,
    val startTime: Float,
    val endTime: Float,
    val progress: Float,
    val easing: String
) {
    val duration: Float get() = endTime - startTime
}
