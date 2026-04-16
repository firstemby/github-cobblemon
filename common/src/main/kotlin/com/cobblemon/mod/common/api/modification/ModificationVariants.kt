/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.modification

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.ModificationVariantRegistrySyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object ModificationVariants : JsonDataRegistry<ModificationVariant> {
    override val id: ResourceLocation = cobblemonResource("modification_variants")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<ModificationVariants>()
    override val typeToken: TypeToken<ModificationVariant> = TypeToken.get(ModificationVariant::class.java)
    override val resourcePath = "modification_variants"
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val variantsByIdentifier = linkedMapOf<ResourceLocation, ModificationVariant>()
    private val variantsByParent = hashMapOf<ResourceLocation, List<ModificationVariant>>()

    override fun reload(data: Map<ResourceLocation, ModificationVariant>) {
        val variants = data.mapNotNull { (identifier, variant) ->
            val parentExistsAsBlock = BuiltInRegistries.BLOCK.containsKey(variant.parent)
            val parentExistsAsItem = BuiltInRegistries.ITEM.containsKey(variant.parent)
            if (!parentExistsAsBlock && !parentExistsAsItem) {
                Cobblemon.LOGGER.warn(
                    "Skipping modification variant {} because parent id {} does not exist as a block or item",
                    identifier,
                    variant.parent
                )
                return@mapNotNull null
            }
            if (!BuiltInRegistries.BLOCK.containsKey(identifier)) {
                Cobblemon.LOGGER.warn("Skipping modification variant {} because variant block {} does not exist", identifier, identifier)
                return@mapNotNull null
            }

            variant.identifier = identifier
            variant
        }

        reloadEntries(variants)
    }

    fun reloadEntries(entries: Collection<ModificationVariant>) {
        val sorted = entries
            .sortedWith(compareBy<ModificationVariant> { it.id }.thenBy { it.identifier.toString() })

        variantsByIdentifier.clear()
        variantsByParent.clear()

        sorted.forEach { variant ->
            variantsByIdentifier[variant.identifier] = variant
        }

        variantsByParent.putAll(sorted.groupBy { it.parent })

        Cobblemon.LOGGER.info("Loaded {} modification variants", variantsByIdentifier.size)
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        ModificationVariantRegistrySyncPacket(all()).sendToPlayer(player)
    }

    @JvmStatic
    fun all(): List<ModificationVariant> = variantsByIdentifier.values.toList()

    @JvmStatic
    fun forParent(parent: ResourceLocation): List<ModificationVariant> = variantsByParent[parent] ?: emptyList()

    @JvmStatic
    fun getByIdentifier(identifier: ResourceLocation): ModificationVariant? = variantsByIdentifier[identifier]
}
