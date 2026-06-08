/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.tms.ObtainMethod
import com.cobblemon.mod.common.tms.obtain.DefaultObtainMethod
import com.cobblemon.mod.common.tms.obtain.PlayerHasAdvancementObtainMethod
import com.cobblemon.mod.common.tms.obtain.PlayerYObtainMethod
import com.cobblemon.mod.common.tms.obtain.UnlockableObtainMethod
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import kotlin.reflect.KClass
import net.minecraft.resources.ResourceLocation

object TMObtainMethodAdapter : JsonSerializer<ObtainMethod>, JsonDeserializer<ObtainMethod> {
    private const val VARIANT = "variant"
    private val types = hashMapOf<String, KClass<out ObtainMethod>>()

    init {
        this.register(UnlockableObtainMethod::class, UnlockableObtainMethod.ID)
        this.register(PlayerHasAdvancementObtainMethod::class, PlayerHasAdvancementObtainMethod.ID)
        this.register(DefaultObtainMethod::class, DefaultObtainMethod.ID)
        this.register(PlayerYObtainMethod::class, PlayerYObtainMethod.ID)
    }

    fun register(type: KClass<out ObtainMethod>, identifier: ResourceLocation) {
        val existing = this.types.put(identifier.toString(), type)
        if (existing != null) {
            Cobblemon.LOGGER.debug(
                    "Replaced {} under ID {} with {} in the {}",
                    existing::class.qualifiedName,
                    identifier.toString(),
                    type.qualifiedName,
                    this::class.qualifiedName
            )
        }
    }

    override fun deserialize(jElement: JsonElement, type: Type, context: JsonDeserializationContext): ObtainMethod {
        val json = jElement.asJsonObject
        val variant = json.get(VARIANT).asString.lowercase()
        val registeredType = this.types[variant]
            ?: throw IllegalArgumentException("Cannot resolve type for variant $variant")
        return context.deserialize(json, registeredType.java)
    }

    override fun serialize(method: ObtainMethod, type: Type, context: JsonSerializationContext): JsonElement {
        val json = context.serialize(method).asJsonObject
        val variant = this.types.entries.find { it.value == method::class }?.key
            ?: throw IllegalArgumentException("Cannot resolve variant for type ${method::class.qualifiedName}")
        json.addProperty(VARIANT, variant)
        return json
    }
}
