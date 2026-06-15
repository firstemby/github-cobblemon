/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey

/**
 * Resolves a WorldDex entry's biome tags into the concrete biomes they cover, via the client's synced
 * biome registry — so the listing always reflects the loaded datapacks (including modded biomes added
 * to those tags). Used by each detail pane's "Biomes" tab.
 */
object WorldDexBiomes {

    class TagBiomes(val tagId: ResourceLocation, val biomes: List<ResourceLocation>) {
        val tagName: MutableComponent get() = Component.literal(WorldDexData.prettify(tagId.path.substringAfterLast('/').removePrefix("is_").removePrefix("has_")))
        fun biomeName(biome: ResourceLocation): MutableComponent =
            Component.translatableWithFallback("biome.${biome.namespace}.${biome.path}", WorldDexData.prettify(biome.path))
    }

    fun biomesFor(entry: WorldDexData.Entry): List<TagBiomes> {
        val registry = Minecraft.getInstance().level?.registryAccess()?.registryOrThrow(Registries.BIOME) ?: return emptyList()
        return entry.biomeTags.sortedBy { it.path }.map { tagId ->
            val biomes = registry.getTag(TagKey.create(Registries.BIOME, tagId))
                .map { named -> named.mapNotNull { it.unwrapKey().orElse(null)?.location() }.sortedBy { it.path } }
                .orElse(emptyList())
            TagBiomes(tagId, biomes)
        }
    }
}
