/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.npc.NPCClass
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

object NPCClassReferenceAdapter : JsonSerializer<NPCClass>, JsonDeserializer<NPCClass> {
    override fun serialize(npcClass: NPCClass, p1: Type, ctx: JsonSerializationContext): JsonElement {
        return JsonPrimitive(npcClass.id.toString())
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): NPCClass {
        val resourceLocation = json.asString.asIdentifierDefaultingNamespace()
        return NPCClasses.getByIdentifier(resourceLocation)
            ?: throw IllegalArgumentException("No such NPC class: $resourceLocation")
    }
}