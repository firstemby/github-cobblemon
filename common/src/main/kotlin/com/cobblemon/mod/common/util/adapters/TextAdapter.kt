/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.text.text
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.mojang.serialization.JsonOps
import java.lang.reflect.Type
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization

object TextAdapter : JsonSerializer<Component>, JsonDeserializer<Component> {
    override fun serialize(text: Component, p1: Type, ctx: JsonSerializationContext): JsonElement {
        return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, text).orThrow
    }

    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): Component {
        return if (json.isJsonPrimitive) {
            json.asString.text()
        } else {
            ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first
        }
    }
}