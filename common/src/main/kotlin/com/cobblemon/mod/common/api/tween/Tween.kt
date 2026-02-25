/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

/**
 * Usage:
 * ```kotlin
 * val tween = Tween.of(FloatInterpolatable(0f))
 *     .to(FloatInterpolatable(1f), 2f, "ease_out_quad")
 *     .onUpdate { value -> entity.setOpacity(value.value) }
 *     .onComplete { println("done") }
 *     .build()
 *
 * TweenManager.client.add(tween)
 * ```
 */
class Tween<T : Interpolatable<T>> private constructor(
    private val start: T,
    private val end: T,
    private val duration: Float,
    private val easingName: String,
    private val onUpdate: ((T) -> Unit)?,
    private val onComplete: (() -> Unit)?
) : Tickable {

    private var elapsed = 0f
    private var cancelled = false
    var current: T = start
        private set

    override val isActive: Boolean
        get() = !cancelled && elapsed < duration

    override fun tick(dt: Float) {
        if (!isActive) return

        elapsed = (elapsed + dt).coerceAtMost(duration)
        val progress = if (duration > 0f) elapsed / duration else 1f
        val easedProgress = EasingRegistry.getEasingFunction(easingName)(progress)

        current = start.lerp(end, easedProgress)
        onUpdate?.invoke(current)

        if (elapsed >= duration) {
            onComplete?.invoke()
        }
    }

    override fun cancel() {
        cancelled = true
    }

    class Builder<T : Interpolatable<T>>(private val start: T) {
        private var end: T = start
        private var duration: Float = 1f
        private var easingName: String = "linear"
        private var onUpdate: ((T) -> Unit)? = null
        private var onComplete: (() -> Unit)? = null

        fun to(end: T, duration: Float, easing: String = "linear"): Builder<T> {
            this.end = end
            this.duration = duration
            this.easingName = easing
            return this
        }

        fun onUpdate(callback: (T) -> Unit): Builder<T> {
            this.onUpdate = callback
            return this
        }

        fun onComplete(callback: () -> Unit): Builder<T> {
            this.onComplete = callback
            return this
        }

        fun build(): Tween<T> = Tween(start, end, duration, easingName, onUpdate, onComplete)
    }

    companion object {
        fun <T : Interpolatable<T>> of(start: T): Builder<T> = Builder(start)
    }
}
