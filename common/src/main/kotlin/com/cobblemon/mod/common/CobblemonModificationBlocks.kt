/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.modification_station.DisplayCaseVariantBlocks
import com.cobblemon.mod.common.modification_station.DomeFossilVariantBlocks
import com.cobblemon.mod.common.modification_station.ModificationVariantBlockRegistrar
import com.cobblemon.mod.common.modification_station.PCVariantBlocks
import com.cobblemon.mod.common.platform.PlatformRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block

@Suppress("MemberVisibilityCanBePrivate")
object CobblemonModificationBlocks : PlatformRegistry<Registry<Block>, ResourceKey<Registry<Block>>, Block>() {
    override val registry: Registry<Block> = BuiltInRegistries.BLOCK
    override val resourceKey: ResourceKey<Registry<Block>> = Registries.BLOCK

    private val variantRegistrars: List<ModificationVariantBlockRegistrar> = listOf(
        DisplayCaseVariantBlocks,
        DomeFossilVariantBlocks,
        PCVariantBlocks
    )

    override fun register(consumer: (ResourceLocation, Block) -> Unit) {
        variantRegistrars.forEach { registrar -> registrar.registerAll(consumer) }
    }

    @JvmStatic
    fun allVariantEntries(): Map<ResourceLocation, Block> = linkedMapOf<ResourceLocation, Block>().also { result ->
        variantRegistrars.forEach { registrar -> result.putAll(registrar.allEntries()) }
    }

    @JvmStatic
    fun variantIdentifier(block: Block): ResourceLocation? {
        variantRegistrars.forEach { registrar ->
            val identifier = registrar.variantIdentifier(block)
            if (identifier != null) {
                return identifier
            }
        }
        return null
    }

    @JvmStatic
    fun isVariant(block: Block): Boolean = variantIdentifier(block) != null

    @JvmStatic
    fun displayCaseVariantBlocks(): Collection<Block> = DisplayCaseVariantBlocks.allBlocks()

    @JvmStatic
    fun displayCaseTintedVariantBlocks(): Collection<Block> = DisplayCaseVariantBlocks.tintedVariantBlocks()

    @JvmStatic
    fun displayCaseNonTintedVariantBlocks(): Collection<Block> = DisplayCaseVariantBlocks.nonTintedVariantBlocks()

    @JvmStatic
    fun displayCaseVariantEntries(): Map<ResourceLocation, Block> = DisplayCaseVariantBlocks.allEntries()

    @JvmStatic
    fun displayCaseVariantIdentifier(block: Block): ResourceLocation? = DisplayCaseVariantBlocks.variantIdentifier(block)

    @JvmStatic
    fun domeFossilVariantBlocks(): Collection<Block> = DomeFossilVariantBlocks.allBlocks()

    @JvmStatic
    fun pcVariantBlocks(): Collection<Block> = PCVariantBlocks.allBlocks()
}
