/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

import com.cobblemon.mod.common.api.tween.*

/**
 * DSL for building keyframe tracks.
 *
 * ```kotlin
 * val track = keyframeTrack<Vec3Interpolatable>(InterpolationMethod.BEZIER) {
 *     at(0f, Vec3Interpolatable(pos1))
 *     at(2f, Vec3Interpolatable(pos2), "ease_out_quad")
 *     at(4f, Vec3Interpolatable(pos3), "ease_out_cubic")
 * }
 * ```
 */
class KeyframeTrackBuilder<T : Interpolatable<T>>(
    private val method: InterpolationMethod = InterpolationMethod.LINEAR,
    private val defaultEasing: String = "linear"
) {
    private val keyframes = mutableListOf<Keyframe<T>>()
    private val easingOverrides = mutableMapOf<Int, String>()

    fun at(time: Float, value: T, easing: String? = null) {
        val index = keyframes.size
        keyframes.add(Keyframe(time, value))
        if (easing != null) {
            easingOverrides[index] = easing
        }
    }

    fun build(): KeyframeTrack<T> {
        val easingMap = easingOverrides.toMap()
        return KeyframeTrack(
            keyframes = keyframes.toList(),
            method = method,
            defaultEasing = defaultEasing,
            getEasing = if (easingMap.isNotEmpty()) { value ->
                val idx = keyframes.indexOfFirst { it.value == value }
                easingMap[idx]
            } else null
        )
    }
}

inline fun <T : Interpolatable<T>> keyframeTrack(
    method: InterpolationMethod = InterpolationMethod.LINEAR,
    defaultEasing: String = "linear",
    block: KeyframeTrackBuilder<T>.() -> Unit
): KeyframeTrack<T> {
    return KeyframeTrackBuilder<T>(method, defaultEasing).apply(block).build()
}

/**
 * DSL for building callback tracks.
 *
 * ```kotlin
 * val track = callbackTrack {
 *     at(0.5f) { println("halfway!") }
 *     at(2f) { playSound("ding") }
 * }
 * ```
 */
class CallbackTrackBuilder {
    private val callbacks = mutableListOf<TimedCallback>()

    fun at(time: Float, action: () -> Unit) {
        callbacks.add(TimedCallback(time, action))
    }

    fun build(): CallbackTrack = CallbackTrack(callbacks.toList())
}

inline fun callbackTrack(block: CallbackTrackBuilder.() -> Unit): CallbackTrack {
    return CallbackTrackBuilder().apply(block).build()
}
