/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.modification_station

import com.cobblemon.mod.common.block.PCBlock
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

object PCVariantBlocks : PlatformRegistry<Registry<Block>, ResourceKey<Registry<Block>>, Block>(),
    ModificationVariantBlockRegistrar {

    override val registry: Registry<Block> = BuiltInRegistries.BLOCK
    override val resourceKey: ResourceKey<Registry<Block>> = Registries.BLOCK

    private val blocksByIdentifier = linkedMapOf<ResourceLocation, PCBlock>()
    private val identifiersByBlock = hashMapOf<Block, ResourceLocation>()

    init {
        registerSingleBlockPCVariant("pc_hgss")
        registerSingleBlockPCVariant("pc_sm")
        registerDoubleBlockPCVariant("pc_xd")
        registerDoubleBlockPCVariant("pc_bw")
        registerDoubleBlockPCVariant("pc_dppt")
        registerDoubleBlockPCVariant("pc_swsh")
        registerOrasPCVariant("pc_oras")
    }

    override fun registerAll(consumer: (ResourceLocation, Block) -> Unit) {
        register(consumer)
    }

    override fun allBlocks(): Collection<Block> = blocksByIdentifier.values

    override fun allEntries(): Map<ResourceLocation, Block> = blocksByIdentifier.toMap()

    override fun variantIdentifier(block: Block): ResourceLocation? = identifiersByBlock[block]

    private fun registerSingleBlockPCVariant(path: String): PCBlock {
        val identifier = cobblemonResource(path)
        val block = create(
            identifier,
            SingleBlockPCVariantBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .strength(2F)
                    .noOcclusion()
                    .lightLevel {
                        if (it.getValue(PCBlock.ON)) 13 else 0
                    }
            )
        )

        blocksByIdentifier[identifier] = block
        identifiersByBlock[block] = identifier
        return block
    }

    private fun registerDoubleBlockPCVariant(path: String): PCBlock {
        val identifier = cobblemonResource(path)
        val block = create(
            identifier,
            PCBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .strength(2F)
                    .noOcclusion()
                    .lightLevel {
                        if (it.getValue(PCBlock.ON) && it.getValue(PCBlock.PART) == PCBlock.PCPart.TOP) 13 else 0
                    }
            )
        )

        blocksByIdentifier[identifier] = block
        identifiersByBlock[block] = identifier
        return block
    }

    private fun registerOrasPCVariant(path: String): PCBlock {
        val identifier = cobblemonResource(path)
        val block = create(
            identifier,
            OrasPCVariantBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)
                    .strength(2F)
                    .noOcclusion()
                    .lightLevel {
                        if (it.getValue(PCBlock.ON) && it.getValue(PCBlock.PART) == PCBlock.PCPart.TOP) 13 else 0
                    }
            )
        )

        blocksByIdentifier[identifier] = block
        identifiersByBlock[block] = identifier
        return block
    }
}

private class SingleBlockPCVariantBlock(properties: BlockBehaviour.Properties) : PCBlock(properties) {
    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState {
        return defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, blockPlaceContext.horizontalDirection)
            .setValue(PART, PCPart.BOTTOM)
            .setValue(WATERLOGGED, blockPlaceContext.level.getFluidState(blockPlaceContext.clickedPos).type == Fluids.WATER)
    }

    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack
    ) {
        // Intentionally single-block: do not place a top half.
    }

    override fun getPositionOfOtherPart(state: BlockState, pos: BlockPos): BlockPos = pos

    override fun updateShape(
        state: BlockState,
        direction: net.minecraft.core.Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }

        return if (state.getValue(PART) == PCPart.TOP) Blocks.AIR.defaultBlockState() else state
    }
}

private class OrasPCVariantBlock(properties: BlockBehaviour.Properties) : PCBlock(properties) {
    companion object {
        private val ORAS_TOP_SECTION_SHAPE = Shapes.box(0.0, 0.5, 0.0, 1.0, 1.0, 1.0)
    }

    override fun getShape(
        blockState: BlockState,
        blockGetter: BlockGetter,
        blockPos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return if (blockState.getValue(PART) == PCPart.TOP) Shapes.empty() else ORAS_TOP_SECTION_SHAPE
    }

    override fun getCollisionShape(
        blockState: BlockState,
        blockGetter: BlockGetter,
        blockPos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return if (blockState.getValue(PART) == PCPart.TOP) Shapes.empty() else ORAS_TOP_SECTION_SHAPE
    }
}
