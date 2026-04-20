/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.animation

import com.cobblemon.mod.common.api.molangui.widget.AnimationConfig
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

object EnterExitAnimation {

    data class AnimationOutput(
        val opacity: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val scale: Float = 1f
    )

    fun evaluate(config: AnimationConfig, progress: Float, isExit: Boolean): AnimationOutput {
        val easedProgress = Easing.resolve(config.easing)(progress)
        val t = if (isExit) 1f - easedProgress else easedProgress
        return when (config.type) {
            "fade_in", "fade_out" -> AnimationOutput(opacity = t)
            "slide_up" -> AnimationOutput(offsetY = (1f - t) * 30f)
            "slide_down" -> AnimationOutput(offsetY = (1f - t) * -30f)
            "slide_left" -> AnimationOutput(offsetX = (1f - t) * 30f)
            "slide_right" -> AnimationOutput(offsetX = (1f - t) * -30f)
            "scale_in", "scale_out" -> AnimationOutput(scale = t)
            "pop" -> AnimationOutput(scale = if (t < 1f) t * 1.1f else 1f, opacity = t)
            else -> AnimationOutput()
        }
    }
}

class TransitionState(
    val duration: Float,
    val easingName: String = "ease_out"
) {
    private val easingFn = Easing.resolve(easingName)
    var startValue: Float = 0f
    var targetValue: Float = 0f
    var currentValue: Float = 0f
    var elapsed: Float = 0f
    var active: Boolean = false

    fun setTarget(newTarget: Float) {
        if (newTarget != targetValue) {
            startValue = currentValue
            targetValue = newTarget
            elapsed = 0f
            active = true
        }
    }

    fun update(delta: Float): Float {
        if (!active) return currentValue
        elapsed += delta
        val progress = (elapsed / duration).coerceIn(0f, 1f)
        currentValue = startValue + (targetValue - startValue) * easingFn(progress)
        if (progress >= 1f) {
            currentValue = targetValue
            active = false
        }
        return currentValue
    }

    fun snapTo(value: Float) {
        startValue = value
        targetValue = value
        currentValue = value
        active = false
    }
}
