/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.evolution.adapters

import com.cobblemon.mod.common.util.adapters.CodecBackedAdapter
import com.cobblemon.mod.common.util.server
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.mojang.serialization.JsonOps
import net.minecraft.advancements.criterion.DataComponentMatchers
import net.minecraft.advancements.criterion.ItemPredicate
import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponentExactPredicate
import net.minecraft.core.component.DataComponents
import net.minecraft.core.component.predicates.CustomDataPredicate
import net.minecraft.core.component.predicates.DataComponentPredicate
import net.minecraft.core.component.predicates.DataComponentPredicates
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.TagParser
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.item.component.CustomData
import java.lang.reflect.Type

// This is an ugly adapter because breaking changes are scary and people would ;(
object LegacyItemConditionWrapperAdapter : JsonDeserializer<ItemPredicate>, JsonSerializer<ItemPredicate> {

    private const val TAG_PREFIX = "#"
    private const val LEGACY_ITEM = "item"
    private const val LEGACY_NBT = "nbt"

    private val codecAdapter = CodecBackedAdapter(ItemPredicate.CODEC)

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ItemPredicate {
        if (json.isJsonPrimitive) {
            return this.createBuilderForItem(json.asString).build()
        }
        else if (json.isJsonObject) {
            val jObject = json.asJsonObject
            if (jObject.size() == 2 && jObject.has(LEGACY_ITEM) && jObject.has(LEGACY_NBT)) {
                val builder = this.createBuilderForItem(jObject.get(LEGACY_ITEM).asString)
                val nbtPredicate = TagParser.LENIENT_CODEC.decode(JsonOps.INSTANCE, jObject.get(LEGACY_NBT)).result().get().first
                return builder // TODO I HAVE NO IDEA WHAT I DID HERE
                    .withComponents(
                        DataComponentMatchers.Builder.components()
                            .exact(DataComponentExactPredicate.expect(DataComponents.CUSTOM_DATA, CustomData.of(nbtPredicate)))
                            .build())
                    .build()
            }
        }
        return this.codecAdapter.deserialize(json, typeOfT, context)
    }

    override fun serialize(src: ItemPredicate, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return this.codecAdapter.serialize(src, typeOfSrc, context)
    }

    private fun createBuilderForItem(raw: String): ItemPredicate.Builder {
        val isTag = raw.startsWith(TAG_PREFIX)
        val builder = ItemPredicate.Builder.item()
        return Identifier.read(if (isTag) raw.substring(1) else raw)
            .mapOrElse(
                { id ->
                    if (isTag) {
                        val tag = TagKey.create(Registries.ITEM, id)
                        builder.of(BuiltInRegistries.ITEM, tag)
                    } else {
                        BuiltInRegistries.ITEM.getOptional(id)
                            .map { builder.of(BuiltInRegistries.ITEM, it) }
                            .orElseGet { builder }
                    }
                },
                { builder }
            )
    }

}
