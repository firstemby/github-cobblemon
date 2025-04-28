/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.stats.RidingStatDefinition
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import net.minecraft.network.chat.Component

object RidingStatDefinitionAdapter : JsonDeserializer<RidingStatDefinition> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): RidingStatDefinition {
        if (json.isJsonPrimitive) {
            val range = IntRangeAdapter.deserialize(json, TypeToken.get(IntRange::class.java).type, context)
            return RidingStatDefinition().apply {
                this.ranges.putAll(RidingStyle.entries.associateWith { range })
            }
        }

        json as JsonObject
        val rangesJson = json.get("ranges")
        val displayName = json.get("displayName")?.let { TextAdapter.deserialize(it, TypeToken.get(Component::class.java).type, context) }
        val description = json.get("description")?.let { TextAdapter.deserialize(it, TypeToken.get(Component::class.java).type, context) }

        if (rangesJson.isJsonPrimitive) {
            val range = IntRangeAdapter.deserialize(rangesJson, TypeToken.get(IntRange::class.java).type, context)
            return RidingStatDefinition().apply {
                displayName?.let { this.displayName = it }
                description?.let { this.description = it }
                this.ranges.putAll(RidingStyle.entries.associateWith { range })
            }
        } else {
            val ranges = rangesJson.asJsonObject.entrySet().associate { (key, value) ->
                RidingStyle.valueOf(key) to IntRangeAdapter.deserialize(value, TypeToken.get(IntRange::class.java).type, context)
            }
            return RidingStatDefinition().apply {
                displayName?.let { this.displayName = it }
                description?.let { this.description = it }
                this.ranges.putAll(ranges)
            }
        }
    }
}