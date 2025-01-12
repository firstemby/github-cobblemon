/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.mojang.datafixers.util.Either
import java.lang.reflect.Type

/**
 * Deserializes what is either an Expression or an object describing a MoLang variable that will be put on an entity.
 *
 * @author Hiroku
 * @since December 28th, 2024
 */
object ExpressionOrEntityVariableAdapter : JsonDeserializer<Either<Expression, MoLangConfigVariable>> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Either<Expression, MoLangConfigVariable> {
        return if (json.isJsonObject) {
            Either.right(context.deserialize(json, MoLangConfigVariable::class.java))
        } else {
            Either.left(context.deserialize(json, Expression::class.java))
        }
    }
}