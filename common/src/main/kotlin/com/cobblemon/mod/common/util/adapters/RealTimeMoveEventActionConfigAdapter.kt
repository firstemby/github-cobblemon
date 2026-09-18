/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.pokemon.ai.moves.RealTimeMove
import com.cobblemon.mod.common.pokemon.ai.moves.events.MoveEventAction
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type


object RealTimeMoveEventActionConfigAdapter : JsonDeserializer<MoveEventAction> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): MoveEventAction? {

        val obj = json.asJsonObject
        val type = obj.get("type")?.asString?.asIdentifierDefaultingNamespace()
            ?: throw IllegalArgumentException("Missing behaviour config type. A behaviour config element must have a 'type' value.")
        val clazz = MoveEventAction.types[type]
            ?: throw IllegalArgumentException("Unknown behaviour config type: $type")
        return ctx.deserialize(obj, clazz)
    }
}