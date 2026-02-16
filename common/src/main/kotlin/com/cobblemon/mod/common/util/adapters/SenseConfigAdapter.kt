/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

object SenseConfigAdapter : JsonDeserializer<SenseConfig> {
    override fun deserialize(json: JsonElement, t: Type, ctx: JsonDeserializationContext): SenseConfig? {
        if (json.isJsonPrimitive) {
            val type = json.asString.asIdentifierDefaultingNamespace()
            val clazz = SenseConfig.types[type]
                ?: throw IllegalArgumentException("Unknown sense config type: $type")
            return ctx.deserialize(JsonObject(), clazz)
        } else {
            val obj = json.asJsonObject
            val type = obj.get("type")?.asString?.asIdentifierDefaultingNamespace()
                ?: throw IllegalArgumentException("Missing sense config type. An sense config element must have a 'type' value.")
            val clazz = SenseConfig.types[type]
                ?: throw IllegalArgumentException("Unknown sense config type: $type")
            return ctx.deserialize(obj, clazz)
        }
    }
}