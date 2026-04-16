/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.api.npc.configuration.interaction.CustomScriptNPCInteractionConfiguration
import com.cobblemon.mod.common.api.npc.configuration.interaction.ScriptNPCInteractionConfiguration
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import java.lang.reflect.Type
import net.minecraft.resources.Identifier

object NPCInteractConfigurationAdapter : JsonDeserializer<NPCInteractConfiguration> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): NPCInteractConfiguration {
        when (json) {
            is JsonPrimitive -> {
                val config = json.asString
                val identifier = Identifier.tryParse(config)
                return if (identifier != null) {
                    ScriptNPCInteractionConfiguration().apply { script = identifier }
                } else {
                    CustomScriptNPCInteractionConfiguration().apply { script = config.asExpressionLike() }
                }
            }
            is JsonObject -> {
                return  NPCInteractConfiguration.CODEC.decode(JsonOps.INSTANCE, json)
                    .getOrThrow { throw IllegalArgumentException("Invalid NPC interact configuration: $json") }
                    .first
            }
            else -> throw IllegalArgumentException("Invalid NPC interact configuration: $json")
        }
    }
}