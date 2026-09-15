/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.google.gson.JsonElement

internal fun JsonElement?.requireUiString(path: String): String {
    require(this != null && isJsonPrimitive && asJsonPrimitive.isString) { "'$path' must be a string" }
    return asString
}

internal fun JsonElement?.requireUiBoolean(path: String): Boolean {
    require(this != null && isJsonPrimitive && asJsonPrimitive.isBoolean) { "'$path' must be a boolean" }
    return asBoolean
}

internal fun JsonElement?.requireUiNumber(path: String): Double {
    require(this != null && isJsonPrimitive && asJsonPrimitive.isNumber) { "'$path' must be a number" }
    return asDouble.also { require(it.isFinite()) { "'$path' must be finite" } }
}

internal fun JsonElement?.requireUiInt(path: String): Int {
    require(this != null && isJsonPrimitive && asJsonPrimitive.isNumber) { "'$path' must be an integer" }
    return try {
        asBigDecimal.intValueExact()
    } catch (error: ArithmeticException) {
        throw IllegalArgumentException("'$path' must be an integer in the 32-bit range", error)
    }
}
