/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

object ObjectiveConfigAdapter : JsonDeserializer<ObjectiveConfig> {
    override fun deserialize(json: JsonElement, t: Type, ctx: JsonDeserializationContext): ObjectiveConfig? {
        if (json.isJsonPrimitive) {
            val type = json.asString.asIdentifierDefaultingNamespace()
            val clazz = ObjectiveConfig.types[type]
                ?: throw IllegalArgumentException("Unknown objective config type: $type")
            return ctx.deserialize(JsonObject(), clazz)
        } else {
            val obj = json.asJsonObject
            val type = obj.get("type")?.asString?.asIdentifierDefaultingNamespace()
                ?: throw IllegalArgumentException("Missing objective config type. An objective config element must have a 'type' value.")
            val clazz = ObjectiveConfig.types[type]
                ?: throw IllegalArgumentException("Unknown objective config type: $type")
            return ctx.deserialize(obj, clazz)
        }
    }
}