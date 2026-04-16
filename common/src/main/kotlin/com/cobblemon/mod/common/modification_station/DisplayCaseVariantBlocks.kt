/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.modification_station

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.block.DisplayCaseBlock
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction

object DisplayCaseVariantBlocks : PlatformRegistry<Registry<Block>, ResourceKey<Registry<Block>>, Block>(),
    ModificationVariantBlockRegistrar {

    override val registry: Registry<Block> = BuiltInRegistries.BLOCK
    override val resourceKey: ResourceKey<Registry<Block>> = Registries.BLOCK

    private val blocksByIdentifier = linkedMapOf<ResourceLocation, DisplayCaseBlock>()
    private val identifiersByBlock = hashMapOf<Block, ResourceLocation>()
    private val tintedBlocks = mutableListOf<Block>()
    private val nonTintedBlocks = mutableListOf<Block>()

    init {
        displayCaseVariantPaths().forEach { path ->
            registerDisplayCaseVariant(path)
        }
    }

    override fun registerAll(consumer: (ResourceLocation, Block) -> Unit) {
        register(consumer)
    }

    override fun allBlocks(): Collection<Block> = blocksByIdentifier.values

    override fun allEntries(): Map<ResourceLocation, Block> = blocksByIdentifier.toMap()

    override fun variantIdentifier(block: Block): ResourceLocation? = identifiersByBlock[block]

    fun tintedVariantBlocks(): Collection<Block> = tintedBlocks

    fun nonTintedVariantBlocks(): Collection<Block> = nonTintedBlocks

    private fun registerDisplayCaseVariant(path: String): DisplayCaseBlock {
        val identifier = cobblemonResource(path)
        val block = create(
            identifier,
            DisplayCaseBlock(
                BlockBehaviour.Properties.of()
                    .sound(CobblemonSounds.DISPLAY_CASE_SOUNDS)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .mapColor(MapColor.STONE)
                    .strength(0.3F)
                    .requiresCorrectToolForDrops()
            )
        )

        blocksByIdentifier[identifier] = block
        identifiersByBlock[block] = identifier
        if (path.contains("_tinted_glass_")) {
            tintedBlocks.add(block)
        } else {
            nonTintedBlocks.add(block)
        }
        return block
    }

    private fun displayCaseVariantPaths(): List<String> {
        val paths = mutableListOf("display_case_default")
        for (index in 1..16) {
            paths.add("display_case_stone_glass_color_$index")
            paths.add("display_case_stone_no_glass_color_$index")
            paths.add("display_case_stone_tinted_glass_color_$index")
        }
        return paths
    }
}
