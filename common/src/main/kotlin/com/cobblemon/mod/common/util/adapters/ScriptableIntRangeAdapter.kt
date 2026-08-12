/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.util.ScriptableIntRange
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.lang.reflect.Type

/**
 * Parses a [ScriptableIntRange] by two paths. One is if it's a primitive in which
 * case they're just doing a normal [IntRange] and it will parse it using the same
 * method as IntRange normally gets and then returns a ScriptableIntRange of literals.
 *
 * The other path is if they provide an object with "min" and "max" in which case
 * each property is deserialized as an [ExpressionLike].
 *
 * @author Hiroku
 * @since December 15th, 2025
 */
object ScriptableIntRangeAdapter : JsonDeserializer<ScriptableIntRange> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ScriptableIntRange {
        if (json is JsonPrimitive) {
            IntRangeAdapter.deserialize(json, typeOfT, context).let {
                return ScriptableIntRange(
                    it.first.toString().asExpressionLike(),
                    it.last.toString().asExpressionLike()
                )
            }
        } else {
            val obj = json.asJsonObject
            val min = obj.get("min").asString
            val max = obj.get("max").asString
            return ScriptableIntRange(
                min.toString().asExpressionLike(),
                max.toString().asExpressionLike()
            )
        }
    }
}