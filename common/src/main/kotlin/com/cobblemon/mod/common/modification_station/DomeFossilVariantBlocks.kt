/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.modification_station

import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

object DomeFossilVariantBlocks : PlatformRegistry<Registry<Block>, ResourceKey<Registry<Block>>, Block>(),
    ModificationVariantBlockRegistrar {

    override val registry: Registry<Block> = BuiltInRegistries.BLOCK
    override val resourceKey: ResourceKey<Registry<Block>> = Registries.BLOCK

    private val blocksByIdentifier = linkedMapOf<ResourceLocation, Block>()
    private val identifiersByBlock = hashMapOf<Block, ResourceLocation>()

    init {
        registerVariant("dome_fossil_block_1", Shapes.box(4.0 / 16.0, 0.0, 3.0 / 16.0, 12.0 / 16.0, 2.75 / 16.0, 13.0 / 16.0))
        registerVariant("dome_fossil_block_2", Shapes.box(0.0, 0.0, 0.0, 1.0, 5.75 / 16.0, 1.0))
    }

    override fun registerAll(consumer: (ResourceLocation, Block) -> Unit) {
        register(consumer)
    }

    override fun allBlocks(): Collection<Block> = blocksByIdentifier.values

    override fun allEntries(): Map<ResourceLocation, Block> = blocksByIdentifier.toMap()

    override fun variantIdentifier(block: Block): ResourceLocation? = identifiersByBlock[block]

    private fun registerVariant(path: String, shape: VoxelShape): Block {
        val identifier = cobblemonResource(path)
        val block = create(
            identifier,
            DomeFossilVariantBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(0.8F)
                    .noOcclusion(),
                shape
            )
        )
        blocksByIdentifier[identifier] = block
        identifiersByBlock[block] = identifier
        return block
    }
}

private class DomeFossilVariantBlock(
    properties: BlockBehaviour.Properties,
    private val shape: VoxelShape
) : Block(properties) {
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = shape

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = shape
}
