/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.bag

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.BagCategoriesSyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.TagKeyAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

object BagCategories: JsonDataRegistry<BagCategory> {

    override val id: ResourceLocation = cobblemonResource("bag_categories")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<BagCategories>()

    override val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(TagKey::class.java, Item::class.java).type, TagKeyAdapter(Registries.ITEM))
        .create()

    override val typeToken: TypeToken<BagCategory> = TypeToken.get(BagCategory::class.java)
    override val resourcePath = "bag_categories"

    private val bagCategories = hashMapOf<ResourceLocation, BagCategory>()

    override fun reload(data: Map<ResourceLocation, BagCategory>) {
        bagCategories.clear()
        data.forEach { identifier, category ->
            try {
                category.identifier = identifier
                bagCategories[identifier] = category
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("Skipped loading the {} category", identifier, e)
            }
        }
        Cobblemon.LOGGER.info("Loaded {} bag categories", bagCategories.size)
        this.observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        BagCategoriesSyncPacket(bagCategories.values.toList())
    }

    fun getOrderedCategoriesList(): List<BagCategory> {
        return bagCategories.values.toList().sortedBy { it.order }
    }
}