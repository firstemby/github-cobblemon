/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Type
import net.minecraft.resources.ResourceLocation

/**
 * A reusable JSON deserialization adapter which expects the object being deserialized has a property that matches a
 * value in the [types] class property which describes the class to use for deserialization.
 *
 * Half of our deserializers could probably be replaced with this tbh.
 *
 * @author Hiroku
 * @since March 28th, 2026
 */
class TypeMapAdapter<T>(
    val typeName: String,
    val types: MutableMap<ResourceLocation, Class<out T>>,
    val typeKey: String = "type",
    val defaultType: ResourceLocation? = null,
    val defaultNamespace: String = Cobblemon.MODID
) : JsonDeserializer<T> {
    override fun deserialize(element: JsonElement, t: Type, ctx: JsonDeserializationContext): T {
        element as JsonObject
        val type = element.get(typeKey)?.asString?.asIdentifierDefaultingNamespace(namespace = defaultNamespace)
            ?: defaultType
            ?: throw IllegalArgumentException("No $typeName type specified")
        val clazz = types[type] ?: throw IllegalArgumentException("Unrecognized $typeName type: $type")
        return ctx.deserialize(element, clazz)
    }
}