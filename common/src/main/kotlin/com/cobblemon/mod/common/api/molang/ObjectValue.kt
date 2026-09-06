/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue

/**
 * A [MoValue] that just wraps around some other object value. It's for good and valid reasons.
 *
 * @author Hiroku
 * @since October 2nd, 2023
 */
class ObjectValue<T>(
    var obj: T?,
    val stringify: (T) -> String = { it.toString() },
    val doublify: (T) -> Double = { DoubleValue.ONE.value }
) : QueryStruct(hashMapOf()) {
    override fun value() = this
    override fun asDouble() = obj?.let(doublify) ?: 0.0
    override fun asString() = obj?.let(stringify) ?: ""

    /**
     * Clears this ObjectValue's internals so that any escaped references to it
     * no longer retain the wrapped object or its associated lambda closures.
     */
    fun invalidate() {
        functions.clear()
        obj = null
    }
}
