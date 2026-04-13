/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.animation

import com.cobblemon.mod.common.api.gui.scripted.widget.AnimationConfig

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
