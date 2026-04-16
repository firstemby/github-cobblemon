/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.platform.PlatformRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item

@Suppress("MemberVisibilityCanBePrivate")
object CobblemonModificationItems : PlatformRegistry<Registry<Item>, ResourceKey<Registry<Item>>, Item>() {
    override val registry: Registry<Item> = BuiltInRegistries.ITEM
    override val resourceKey: ResourceKey<Registry<Item>> = Registries.ITEM

    private val variantItemsById = linkedMapOf<ResourceLocation, BlockItem>()

    init {
        CobblemonModificationBlocks.allVariantEntries().forEach { (identifier, block) ->
            variantItemsById[identifier] = create(identifier, BlockItem(block, Item.Properties()))
        }
    }

    @JvmStatic
    fun allVariantItems(): Collection<Item> = variantItemsById.values

    @JvmStatic
    fun displayCaseVariantItems(): Collection<Item> = variantItemsById.values
}
