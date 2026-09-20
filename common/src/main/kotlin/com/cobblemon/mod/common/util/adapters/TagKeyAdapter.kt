/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey

/**
 * An adapter for [TagKey]s.
 * [TagKey]s are just [ResourceLocation]s attached to a certain registry.
 *
 * @param T The type of the [Registry] this [TagKey] belongs to.
 * @property key The [ResourceKey] used to create new [TagKey]s.
 *
 * @author Licious
 * @since July 2nd, 2022
 */
class TagKeyAdapter<T>(private val key: ResourceKey<Registry<T>>) : JsonDeserializer<TagKey<T>>, JsonSerializer<TagKey<T>> {

    override fun deserialize(element: JsonElement, type: Type, ctx: JsonDeserializationContext): TagKey<T> {
        val identifier = ResourceLocation.parse(element.asString.replace("#", ""))
        return TagKey.create(this.key, identifier)
    }

    override fun serialize(tagKey: TagKey<T>, type: Type, ctx: JsonSerializationContext): JsonElement {
        return JsonPrimitive(tagKey.location.toString())
    }

}