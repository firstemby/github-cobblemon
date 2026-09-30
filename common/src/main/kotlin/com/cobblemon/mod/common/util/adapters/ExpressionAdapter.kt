/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.bedrockk.molang.Expression
import com.bedrockk.molang.MoLang
import com.cobblemon.mod.common.util.getString
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object ExpressionAdapter : JsonDeserializer<Expression>, JsonSerializer<Expression> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): Expression {
        return MoLang.createParser(json.asString).parseExpression()
    }

    override fun serialize(src: Expression, typeOfSrc: Type, ctx: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.getString())
    }
}