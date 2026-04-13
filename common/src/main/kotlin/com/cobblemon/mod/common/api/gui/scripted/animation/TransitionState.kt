/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.animation

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
