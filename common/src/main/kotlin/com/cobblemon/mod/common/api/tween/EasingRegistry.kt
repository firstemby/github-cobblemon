/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

import com.cobblemon.mod.common.Cobblemon
import kotlin.math.*

/**
 * Registry for all easing functions. Replaces the former CameraEasing, CameraPluginRegistry,
 * and IEasingFunction — providing a single, common-side easing registry usable by the camera
 * system, tween engine, and any other animation system.
 *
 * Third-party mods can register custom easing functions via [registerEasingFunction].
 */
object EasingRegistry {

    private val easingFunctions = mutableMapOf<String, EasingFunction>()

    fun registerBuiltinEasings() {
        registerEasingFunction("linear", EasingFunction { linear(it) })
        registerEasingFunction("step", EasingFunction { step(it) })
        registerEasingFunction("ease_in_sine", EasingFunction { easeInSine(it) })
        registerEasingFunction("ease_out_sine", EasingFunction { easeOutSine(it) })
        registerEasingFunction("ease_in_out_sine", EasingFunction { easeInOutSine(it) })
        registerEasingFunction("ease_in_quad", EasingFunction { easeInQuad(it) })
        registerEasingFunction("ease_out_quad", EasingFunction { easeOutQuad(it) })
        registerEasingFunction("ease_in_out_quad", EasingFunction { easeInOutQuad(it) })
        registerEasingFunction("ease_in_cubic", EasingFunction { easeInCubic(it) })
        registerEasingFunction("ease_out_cubic", EasingFunction { easeOutCubic(it) })
        registerEasingFunction("ease_in_out_cubic", EasingFunction { easeInOutCubic(it) })
        registerEasingFunction("ease_in_quart", EasingFunction { easeInQuart(it) })
        registerEasingFunction("ease_out_quart", EasingFunction { easeOutQuart(it) })
        registerEasingFunction("ease_in_out_quart", EasingFunction { easeInOutQuart(it) })
        registerEasingFunction("ease_in_quint", EasingFunction { easeInQuint(it) })
        registerEasingFunction("ease_out_quint", EasingFunction { easeOutQuint(it) })
        registerEasingFunction("ease_in_out_quint", EasingFunction { easeInOutQuint(it) })
        registerEasingFunction("ease_in_expo", EasingFunction { easeInExpo(it) })
        registerEasingFunction("ease_out_expo", EasingFunction { easeOutExpo(it) })
        registerEasingFunction("ease_in_out_expo", EasingFunction { easeInOutExpo(it) })
        registerEasingFunction("ease_in_circ", EasingFunction { easeInCirc(it) })
        registerEasingFunction("ease_out_circ", EasingFunction { easeOutCirc(it) })
        registerEasingFunction("ease_in_out_circ", EasingFunction { easeInOutCirc(it) })
        registerEasingFunction("ease_in_back", EasingFunction { easeInBack(it) })
        registerEasingFunction("ease_out_back", EasingFunction { easeOutBack(it) })
        registerEasingFunction("ease_in_out_back", EasingFunction { easeInOutBack(it) })
        registerEasingFunction("ease_in_elastic", EasingFunction { easeInElastic(it) })
        registerEasingFunction("ease_out_elastic", EasingFunction { easeOutElastic(it) })
        registerEasingFunction("ease_in_out_elastic", EasingFunction { easeInOutElastic(it) })
        registerEasingFunction("ease_in_bounce", EasingFunction { easeInBounce(it) })
        registerEasingFunction("ease_out_bounce", EasingFunction { easeOutBounce(it) })
        registerEasingFunction("ease_in_out_bounce", EasingFunction { easeInOutBounce(it) })
        Cobblemon.LOGGER.info("[EasingRegistry] Registered built-in easing functions")
    }

    fun registerEasingFunction(name: String, function: EasingFunction) {
        val normalizedName = name.lowercase()
        if (easingFunctions.containsKey(normalizedName)) {
            Cobblemon.LOGGER.warn("[EasingRegistry] Overwriting easing function '$name'")
        }
        easingFunctions[normalizedName] = function
    }

    /**
     * Returns an easing function by name. Falls back to linear with a warning for unknown names.
     * Supports both snake_case ("ease_out_quad") and camelCase ("easeOutQuad") lookups.
     */
    fun getEasingFunction(name: String): (Float) -> Float {
        val normalized = name.lowercase()
        val registered = easingFunctions[normalized]
        if (registered != null) {
            return { t -> registered.ease(t) }
        }

        // Legacy camelCase fallback
        return when (normalized) {
            "linear" -> ::linear
            "step" -> ::step
            "easeinsine" -> ::easeInSine
            "easeoutsine" -> ::easeOutSine
            "easeinoutsine" -> ::easeInOutSine
            "easeinquad" -> ::easeInQuad
            "easeoutquad" -> ::easeOutQuad
            "easeinoutquad" -> ::easeInOutQuad
            "easeincubic" -> ::easeInCubic
            "easeoutcubic" -> ::easeOutCubic
            "easeinoutcubic" -> ::easeInOutCubic
            "easeinquart" -> ::easeInQuart
            "easeoutquart" -> ::easeOutQuart
            "easeinoutquart" -> ::easeInOutQuart
            "easeinquint" -> ::easeInQuint
            "easeoutquint" -> ::easeOutQuint
            "easeinoutquint" -> ::easeInOutQuint
            "easeinexpo" -> ::easeInExpo
            "easeoutexpo" -> ::easeOutExpo
            "easeinoutexpo" -> ::easeInOutExpo
            "easeincirc" -> ::easeInCirc
            "easeoutcirc" -> ::easeOutCirc
            "easeinoutcirc" -> ::easeInOutCirc
            "easeinback" -> ::easeInBack
            "easeoutback" -> ::easeOutBack
            "easeinoutback" -> ::easeInOutBack
            "easeinelastic" -> ::easeInElastic
            "easeoutelastic" -> ::easeOutElastic
            "easeinoutelastic" -> ::easeInOutElastic
            "easeinbounce" -> ::easeInBounce
            "easeoutbounce" -> ::easeOutBounce
            "easeinoutbounce" -> ::easeInOutBounce
            else -> {
                Cobblemon.LOGGER.warn("[EasingRegistry] Unknown easing function '$name', using linear")
                ::linear
            }
        }
    }

    fun linear(t: Float): Float = t
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
