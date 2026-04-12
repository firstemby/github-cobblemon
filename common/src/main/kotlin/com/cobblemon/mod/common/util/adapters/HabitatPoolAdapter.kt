/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type

/**
 * Simple map adapter for which type of HabitatPool is being deserialized.
 *
 * @author Hiroku
 * @since February 13th, 2026
 */
object HabitatPoolAdapter : JsonDeserializer<HabitatPool<*>> {
    override fun deserialize(json: JsonElement, t: Type, ctx: JsonDeserializationContext): HabitatPool<*>? {
        json as JsonObject
        val type = json.get("type").asString.asIdentifierDefaultingNamespace()
        val clazz = HabitatPool.types[type] ?: throw IllegalArgumentException("Unknown habitat pool type: $type")
        return ctx.deserialize(json, clazz)
    }
}