/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui.widget

import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.bedrockk.molang.runtime.MoLangRuntime

/**
 * A widget property that is either a static value or a Molang expression.
 * String values that look like Molang (contain operators, function calls, or variable refs)
 * are parsed as expressions. Plain literals are kept static.
 */
class WidgetProperty(val raw: Any) {
    val expression: ExpressionLike? = when (raw) {
        is String -> {
            if (isMolangExpression(raw)) raw.asExpressionLike() else null
        }
        else -> null
    }

    val isStatic: Boolean get() = expression == null

    fun resolveDouble(runtime: MoLangRuntime): Double {
        return expression?.resolveDouble(runtime) ?: when (raw) {
            is Number -> raw.toDouble()
            is String -> parseNumericString(raw)
            is Boolean -> if (raw) 1.0 else 0.0
            else -> 0.0
        }
    }

    fun resolveString(runtime: MoLangRuntime): String {
        return expression?.resolveString(runtime) ?: raw.toString()
    }

    fun resolveBoolean(runtime: MoLangRuntime): Boolean {
        return expression?.resolveBoolean(runtime) ?: when (raw) {
            is Boolean -> raw
            is Number -> raw.toDouble() != 0.0
            is String -> raw.toBooleanStrictOrNull() ?: (raw.isNotEmpty())
            else -> false
        }
    }

    fun resolveInt(runtime: MoLangRuntime): Int {
        if (expression == null && raw is String && raw.startsWith("0x", ignoreCase = true)) {
            return raw.substring(2).toLongOrNull(16)?.toInt() ?: 0
        }
        return resolveDouble(runtime).toInt()
    }

    fun resolveFloat(runtime: MoLangRuntime): Float = resolveDouble(runtime).toFloat()

    companion object {
        private val MOLANG_INDICATORS = listOf("q.", "v.", "t.", "c.", "math.", "temp.", "query.", "variable.", "+", "-", "*", "/", "?", "!", "==", "!=", "<", ">", "(", "&&", "||")

        fun isMolangExpression(value: String): Boolean {
            return MOLANG_INDICATORS.any { value.contains(it) }
        }

        fun parseNumericString(value: String): Double {
            value.toDoubleOrNull()?.let { return it }
            if (value.startsWith("0x", ignoreCase = true)) {
                value.substring(2).toLongOrNull(16)?.let { return it.toDouble() }
            }
            return 0.0
        }
    }
}
