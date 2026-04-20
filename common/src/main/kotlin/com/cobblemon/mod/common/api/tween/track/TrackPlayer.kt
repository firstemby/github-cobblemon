/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

import com.cobblemon.mod.common.api.tween.Tickable

class TrackPlayer<T>(
    val track: Track<T>,
    var loop: Boolean = false,
    var speed: Float = 1f,
    private val onUpdate: ((T) -> Unit)? = null,
    private val onComplete: (() -> Unit)? = null
) : Tickable {

    var currentTime: Float = 0f
        private set
    var paused: Boolean = false
    private var cancelled = false
    private val firedCallbacks = mutableSetOf<Int>()

    var currentValue: T? = null
        private set

    override val isActive: Boolean
        get() = !cancelled && (loop || currentTime < track.duration)

    override fun tick(dt: Float) {
        if (!isActive || paused) return

        val previousTime = currentTime
        currentTime += dt * speed

        if (currentTime >= track.duration) {
            if (loop) {
                currentTime %= track.duration
                firedCallbacks.clear()
            } else {
                currentTime = track.duration
            }
        }

        if (track is CallbackTrack) {
            fireCallbacks(previousTime, currentTime, track)
        }

        val value = track.evaluate(currentTime)
        currentValue = value
        if (value != null) {
            onUpdate?.invoke(value)
        }

        if (!loop && currentTime >= track.duration) {
            onComplete?.invoke()
        }
    }

    override fun cancel() {
        cancelled = true
    }

    fun reset() {
        currentTime = 0f
        firedCallbacks.clear()
    }

    private fun fireCallbacks(previousTime: Float, currentTime: Float, callbackTrack: CallbackTrack) {
        callbackTrack.callbacks.forEachIndexed { index, callback ->
            if (index in firedCallbacks) return@forEachIndexed

            val crossed = if (previousTime <= currentTime) {
                callback.time in previousTime..currentTime
            } else {
                // Wrapped around (loop)
                callback.time <= currentTime
            }

            if (crossed) {
                callback.action()
                firedCallbacks.add(index)
            }
        }
    }
}
