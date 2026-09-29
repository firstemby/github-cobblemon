/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.sources.FovSource
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveDouble
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

data class ExpressionFov(
    val expression: ExpressionLike,
    val fallbackFov: Float = 70f
) : FovSource() {
    override val type: String = "expression"
    @Transient private val warningReported = AtomicBoolean()

    init {
        require(fallbackFov in 1f..179f) { "ExpressionFov fallbackFov must be in [1, 179]" }
    }

    fun evaluate(runtime: MoLangRuntime): Float {
        return try {
            // coerceIn does not filter NaN — its comparisons are false either way — so check first.
            val raw = runtime.resolveDouble(expression).toFloat()
            if (!raw.isFinite()) {
                if (warningReported.compareAndSet(false, true)) {
                    Cobblemon.LOGGER.warn("[ExpressionFov] Non-finite result $raw; using fallback $fallbackFov")
                }
                fallbackFov
            } else {
                raw.coerceIn(1f, 179f)
            }
        } catch (e: Exception) {
            if (warningReported.compareAndSet(false, true)) {
                Cobblemon.LOGGER.warn("[ExpressionFov] Evaluation failed: ${e.message}; using fallback $fallbackFov", e)
            }
            fallbackFov
        }
    }

    companion object {
        const val TYPE = "expression"
        fun fromJson(obj: JsonObject): ExpressionFov {
            val exprStr = obj.get("expression")?.asString
                ?: throw IllegalArgumentException("ExpressionFov missing 'expression' field")
            val fallback = obj.get("fallback")?.asFloat ?: 70f
            return ExpressionFov(expression = exprStr.asExpressionLike(), fallbackFov = fallback)
        }
    }
}
