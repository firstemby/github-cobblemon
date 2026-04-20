/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.layout

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.bedrockk.molang.runtime.MoLangRuntime

sealed class SizeExpression {
    abstract fun resolve(parentSize: Float, runtime: MoLangRuntime): Float

    data class Pixels(val value: Float) : SizeExpression() {
        override fun resolve(parentSize: Float, runtime: MoLangRuntime) = value
    }

    data class Percent(val fraction: Float) : SizeExpression() {
        override fun resolve(parentSize: Float, runtime: MoLangRuntime) = parentSize * fraction
    }

    data class PercentWithOffset(val fraction: Float, val offset: Float) : SizeExpression() {
        override fun resolve(parentSize: Float, runtime: MoLangRuntime) = parentSize * fraction + offset
    }

    data object Auto : SizeExpression() {
        override fun resolve(parentSize: Float, runtime: MoLangRuntime) = 0f
    }

    data object Fill : SizeExpression() {
        override fun resolve(parentSize: Float, runtime: MoLangRuntime) = 0f
    }

    data class MolangSize(val expression: ExpressionLike, val isPercent: Boolean = false) : SizeExpression() {
        override fun resolve(parentSize: Float, runtime: MoLangRuntime): Float {
            val value = expression.resolveFloat(runtime)
            return if (isPercent) parentSize * value / 100f else value
        }
    }

    companion object {
        private val PERCENT_PATTERN = Regex("""^(\d+(?:\.\d+)?)%$""")
        private val PERCENT_OFFSET_PATTERN = Regex("""^(\d+(?:\.\d+)?)%\s*([+-])\s*(\d+(?:\.\d+)?)$""")

        fun parse(value: Any): SizeExpression {
            return when (value) {
                is Number -> Pixels(value.toFloat())
                is String -> parseString(value)
                else -> Pixels(0f)
            }
        }

        private fun parseString(value: String): SizeExpression {
            val trimmed = value.trim()
            return when {
                trimmed == "auto" -> Auto
                trimmed == "fill" -> Fill
                trimmed.toFloatOrNull() != null -> Pixels(trimmed.toFloat())
                PERCENT_PATTERN.matches(trimmed) -> {
                    val pct = PERCENT_PATTERN.matchEntire(trimmed)!!.groupValues[1].toFloat()
                    Percent(pct / 100f)
                }
                PERCENT_OFFSET_PATTERN.matches(trimmed) -> {
                    val match = PERCENT_OFFSET_PATTERN.matchEntire(trimmed)!!
                    val pct = match.groupValues[1].toFloat() / 100f
                    val sign = if (match.groupValues[2] == "+") 1f else -1f
                    val offset = match.groupValues[3].toFloat() * sign
                    PercentWithOffset(pct, offset)
                }
                else -> {
                    val isPercent = trimmed.endsWith("%")
                    val exprStr = if (isPercent) trimmed.dropLast(1).trim() else trimmed
                    try {
                        MolangSize(exprStr.asExpressionLike(), isPercent)
                    } catch (e: Exception) {
                        Pixels(0f)
                    }
                }
            }
        }
    }
}
