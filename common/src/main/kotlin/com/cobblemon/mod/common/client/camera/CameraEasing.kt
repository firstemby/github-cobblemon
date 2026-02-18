/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraPluginRegistry
import com.cobblemon.mod.common.api.camera.IEasingFunction
import kotlin.math.*

object CameraEasing {

    fun registerBuiltinEasings() {
        CameraPluginRegistry.registerEasingFunction("linear", IEasingFunction { linear(it) })
        CameraPluginRegistry.registerEasingFunction("step", IEasingFunction { step(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_sine", IEasingFunction { easeInOutSine(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_quad", IEasingFunction { easeOutQuad(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_quad", IEasingFunction { easeInQuad(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_quad", IEasingFunction { easeInOutQuad(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_cubic", IEasingFunction { easeInCubic(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_cubic", IEasingFunction { easeOutCubic(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_cubic", IEasingFunction { easeInOutCubic(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_quart", IEasingFunction { easeInQuart(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_quart", IEasingFunction { easeOutQuart(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_quart", IEasingFunction { easeInOutQuart(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_quint", IEasingFunction { easeInQuint(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_quint", IEasingFunction { easeOutQuint(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_quint", IEasingFunction { easeInOutQuint(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_expo", IEasingFunction { easeInExpo(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_expo", IEasingFunction { easeOutExpo(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_expo", IEasingFunction { easeInOutExpo(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_circ", IEasingFunction { easeInCirc(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_circ", IEasingFunction { easeOutCirc(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_circ", IEasingFunction { easeInOutCirc(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_back", IEasingFunction { easeInBack(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_back", IEasingFunction { easeOutBack(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_back", IEasingFunction { easeInOutBack(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_elastic", IEasingFunction { easeInElastic(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_elastic", IEasingFunction { easeOutElastic(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_elastic", IEasingFunction { easeInOutElastic(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_bounce", IEasingFunction { easeInBounce(it) })
        CameraPluginRegistry.registerEasingFunction("ease_out_bounce", IEasingFunction { easeOutBounce(it) })
        CameraPluginRegistry.registerEasingFunction("ease_in_out_bounce", IEasingFunction { easeInOutBounce(it) })
        Cobblemon.LOGGER.info("[CameraAPI] Registered built-in camera easing functions")
    }

    fun getEasingFunction(name: String): (Float) -> Float {
        val customEasing = CameraPluginRegistry.getEasingFunction(name)
        if (customEasing != null) {
            return { t -> customEasing.ease(t) }
        }

        return when (name.lowercase()) {
            "linear" -> ::linear
            "step" -> ::step
            "easeinoutsine", "ease_in_out_sine" -> ::easeInOutSine
            "easeoutquad", "ease_out_quad" -> ::easeOutQuad
            "easeinquad", "ease_in_quad" -> ::easeInQuad
            "easeinoutquad", "ease_in_out_quad" -> ::easeInOutQuad
            "easeincubic", "ease_in_cubic" -> ::easeInCubic
            "easeoutcubic", "ease_out_cubic" -> ::easeOutCubic
            "easeinoutcubic", "ease_in_out_cubic" -> ::easeInOutCubic
            "easeinquart", "ease_in_quart" -> ::easeInQuart
            "easeoutquart", "ease_out_quart" -> ::easeOutQuart
            "easeinoutquart", "ease_in_out_quart" -> ::easeInOutQuart
            "easeinquint", "ease_in_quint" -> ::easeInQuint
            "easeoutquint", "ease_out_quint" -> ::easeOutQuint
            "easeinoutquint", "ease_in_out_quint" -> ::easeInOutQuint
            "easeinexpo", "ease_in_expo" -> ::easeInExpo
            "easeoutexpo", "ease_out_expo" -> ::easeOutExpo
            "easeinoutexpo", "ease_in_out_expo" -> ::easeInOutExpo
            "easeincirc", "ease_in_circ" -> ::easeInCirc
            "easeoutcirc", "ease_out_circ" -> ::easeOutCirc
            "easeinoutcirc", "ease_in_out_circ" -> ::easeInOutCirc
            "easeinback", "ease_in_back" -> ::easeInBack
            "easeoutback", "ease_out_back" -> ::easeOutBack
            "easeinoutback", "ease_in_out_back" -> ::easeInOutBack
            "easeinelastic", "ease_in_elastic" -> ::easeInElastic
            "easeoutelastic", "ease_out_elastic" -> ::easeOutElastic
            "easeinoutelastic", "ease_in_out_elastic" -> ::easeInOutElastic
            "easeinbounce", "ease_in_bounce" -> ::easeInBounce
            "easeoutbounce", "ease_out_bounce" -> ::easeOutBounce
            "easeinoutbounce", "ease_in_out_bounce" -> ::easeInOutBounce
            else -> {
                Cobblemon.LOGGER.warn("[CobblemonCamera] Unknown easing function '$name', using linear")
                ::linear
            }
        }
    }

    fun linear(t: Float): Float = t
    /** Step function - instant jump to end value (no interpolation) */
    fun step(t: Float): Float = if (t >= 1f) 1f else 0f
    fun easeInSine(t: Float): Float = 1 - cos((t * PI) / 2).toFloat()
    fun easeOutSine(t: Float): Float = sin((t * PI) / 2).toFloat()
    fun easeInOutSine(t: Float): Float = -(cos(PI * t) - 1).toFloat() / 2

    fun easeInQuad(t: Float): Float = t * t
    fun easeOutQuad(t: Float): Float = 1 - (1 - t) * (1 - t)
    fun easeInOutQuad(t: Float): Float {
        return if (t < 0.5f) 2 * t * t
        else 1 - (-2 * t + 2).pow(2) / 2
    }

    fun easeInCubic(t: Float): Float = t * t * t
    fun easeOutCubic(t: Float): Float = 1 - (1 - t).pow(3)
    fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4 * t * t * t
        else 1 - (-2 * t + 2).pow(3) / 2
    }

    fun easeInQuart(t: Float): Float = t * t * t * t
    fun easeOutQuart(t: Float): Float = 1 - (1 - t).pow(4)
    fun easeInOutQuart(t: Float): Float {
        return if (t < 0.5f) 8 * t * t * t * t
        else 1 - (-2 * t + 2).pow(4) / 2
    }

    fun easeInQuint(t: Float): Float = t * t * t * t * t
    fun easeOutQuint(t: Float): Float = 1 - (1 - t).pow(5)
    fun easeInOutQuint(t: Float): Float {
        return if (t < 0.5f) 16 * t * t * t * t * t
        else 1 - (-2 * t + 2).pow(5) / 2
    }

    fun easeInExpo(t: Float): Float {
        return if (t == 0f) 0f else 2f.pow(10 * t - 10)
    }
    fun easeOutExpo(t: Float): Float {
        return if (t == 1f) 1f else 1 - 2f.pow(-10 * t)
    }
    fun easeInOutExpo(t: Float): Float {
        return when {
            t == 0f -> 0f
            t == 1f -> 1f
            t < 0.5f -> 2f.pow(20 * t - 10) / 2
            else -> (2 - 2f.pow(-20 * t + 10)) / 2
        }
    }

    fun easeInCirc(t: Float): Float = 1 - sqrt(1 - t.pow(2))
    fun easeOutCirc(t: Float): Float = sqrt(1 - (t - 1).pow(2))
    fun easeInOutCirc(t: Float): Float {
        return if (t < 0.5f) (1 - sqrt(1 - (2 * t).pow(2))) / 2
        else (sqrt(1 - (-2 * t + 2).pow(2)) + 1) / 2
    }

    private const val c1 = 1.70158f
    private const val c2 = c1 * 1.525f
    private const val c3 = c1 + 1

    fun easeInBack(t: Float): Float = c3 * t * t * t - c1 * t * t
    fun easeOutBack(t: Float): Float = 1 + c3 * (t - 1).pow(3) + c1 * (t - 1).pow(2)
    fun easeInOutBack(t: Float): Float {
        return if (t < 0.5f) ((2 * t).pow(2) * ((c2 + 1) * 2 * t - c2)) / 2
        else ((2 * t - 2).pow(2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2
    }

    private const val c4 = (2 * PI) / 3
    private const val c5 = (2 * PI) / 4.5

    fun easeInElastic(t: Float): Float {
        return when {
            t == 0f -> 0f
            t == 1f -> 1f
            else -> -2f.pow(10 * t - 10) * sin((t * 10 - 10.75) * c4).toFloat()
        }
    }
    fun easeOutElastic(t: Float): Float {
        return when {
            t == 0f -> 0f
            t == 1f -> 1f
            else -> 2f.pow(-10 * t) * sin((t * 10 - 0.75) * c4).toFloat() + 1
        }
    }
    fun easeInOutElastic(t: Float): Float {
        return when {
            t == 0f -> 0f
            t == 1f -> 1f
            t < 0.5f -> -(2f.pow(20 * t - 10) * sin((20 * t - 11.125) * c5).toFloat()) / 2
            else -> (2f.pow(-20 * t + 10) * sin((20 * t - 11.125) * c5).toFloat()) / 2 + 1
        }
    }

    private const val n1 = 7.5625f
    private const val d1 = 2.75f

    fun easeOutBounce(t: Float): Float {
        return when {
            t < 1 / d1 -> n1 * t * t
            t < 2 / d1 -> n1 * (t - 1.5f / d1) * (t - 1.5f / d1) + 0.75f
            t < 2.5 / d1 -> n1 * (t - 2.25f / d1) * (t - 2.25f / d1) + 0.9375f
            else -> n1 * (t - 2.625f / d1) * (t - 2.625f / d1) + 0.984375f
        }
    }
    fun easeInBounce(t: Float): Float = 1 - easeOutBounce(1 - t)
    fun easeInOutBounce(t: Float): Float {
        return if (t < 0.5f) (1 - easeOutBounce(1 - 2 * t)) / 2
        else (1 + easeOutBounce(2 * t - 1)) / 2
    }
}

