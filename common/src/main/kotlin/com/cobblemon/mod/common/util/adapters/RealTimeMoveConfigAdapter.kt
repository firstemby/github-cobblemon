/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.pokemon.ai.moves.RealTimeMove
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

object RealTimeMoveConfigAdapter : JsonDeserializer<RealTimeMove> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): RealTimeMove? {

        val obj = json.asJsonObject
        val type = obj.get("type")?.asString?.asIdentifierDefaultingNamespace()
            ?: throw IllegalArgumentException("Missing RealTimeMove config type. A RealTimeMove config element must have a 'type' value.")
        val clazz = RealTimeMove.types[type]
            ?: throw IllegalArgumentException("Unknown RealTimeMove config type: $type")
        return ctx.deserialize(obj, clazz)
    }
}