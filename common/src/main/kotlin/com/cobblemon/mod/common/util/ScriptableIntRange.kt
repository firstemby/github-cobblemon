/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike

/**
 * An IntRange where the minimum and maximum values are defined by MoLang expressions. When deserializing from
 * JSON the [com.cobblemon.mod.common.util.adapters.ScriptableIntRangeAdapter] will figure out if it's a single
 * value in which case it's not using any scripting stuff. Otherwise, if it's an object with "min" and "max" properties
 * it will respect them as expressions.
 *
 * @author Hiroku
 * @since December 15th, 2025
 */
class ScriptableIntRange(
    val min: ExpressionLike = "0".asExpressionLike(),
    val max: ExpressionLike = "0".asExpressionLike()
) {
    constructor(min: Int, max: Int): this(
        min = min.toString().asExpressionLike(),
        max = max.toString().asExpressionLike()
    )

    fun resolve(runtime: MoLangRuntime): IntRange {
        val minimum = min.resolveInt(runtime)
        val maximum = max.resolveInt(runtime)
        if (minimum > maximum) {
            Cobblemon.LOGGER.error("ScriptableIntRange(${min.getString()}, ${max.getString()}) resolved a minimum ($minimum) greater than maximum ($maximum). Returning empty range.")
            return IntRange.EMPTY
        }
        return minimum..maximum
    }
}