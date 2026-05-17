/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.CobblemonRealTimeMoves
import com.cobblemon.mod.common.pokemon.ai.RealTimeMove
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

object RealTimeMoveReferenceAdapter : JsonDeserializer<RealTimeMove> {
    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): RealTimeMove {
        val id = json.asString.asIdentifierDefaultingNamespace()
        return CobblemonRealTimeMoves.realTimeMoves[id]
            ?: throw IllegalArgumentException("No real time move found with id $id")
    }
}