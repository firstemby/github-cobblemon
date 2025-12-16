/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Defines how a move's score should be modified.
 *
 * @property type The type of modification: "multiply", "add", "set", or "script"
 * @property value The value or expression to use for the modification
 *
 * @author Amo Aster
 * @since December 2025
 */
data class ScoreModifier(
    val type: ModifierType,
    val value: ExpressionLike
) {
    /**
     * Applies this modifier to a base score.
     *
     * @param baseScore The current score before modification
     * @param evaluatedValue The evaluated Molang value
     * @return The modified score
     */
    fun apply(baseScore: Double, evaluatedValue: Double): Double {
        return when (type) {
            ModifierType.MULTIPLY -> baseScore * evaluatedValue
            ModifierType.ADD -> baseScore + evaluatedValue
            ModifierType.SET -> evaluatedValue
            ModifierType.SCRIPT -> evaluatedValue // Script returns final value directly
        }
    }

    companion object {
        val CODEC: Codec<ScoreModifier> = RecordCodecBuilder.create { instance ->
            instance.group(
                ModifierType.CODEC.fieldOf("type").forGetter { it.type },
                ExpressionLike.CODEC.fieldOf("value").forGetter { it.value }
            ).apply(instance, ::ScoreModifier)
        }

        /** Creates a multiply modifier with a constant factor */
        fun multiply(factor: Double): ScoreModifier = ScoreModifier(ModifierType.MULTIPLY, createConstantExpression(factor))

        /** Creates an add modifier with a constant value */
        fun add(value: Double): ScoreModifier = ScoreModifier(ModifierType.ADD, createConstantExpression(value))

        /** Creates a set modifier with a constant value */
        fun set(value: Double): ScoreModifier = ScoreModifier(ModifierType.SET, createConstantExpression(value))

        private fun createConstantExpression(value: Double): ExpressionLike {
            return object : ExpressionLike {
                override fun toString() = value.toString()
                override fun resolve(runtime: MoLangRuntime, context: Map<String, MoValue>) =
                    DoubleValue(value)
            }
        }
    }
}
