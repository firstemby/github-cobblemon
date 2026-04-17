/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.modification_station

import com.cobblemon.mod.common.block.PCBlock
import com.cobblemon.mod.common.block.entity.PCBlockEntity
import com.cobblemon.mod.common.platform.PlatformRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.Fluids
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
        registerTopModelPCVariant("pc_oras")
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

    private fun registerTopModelPCVariant(path: String): PCBlock {
        val identifier = cobblemonResource(path)
        val block = create(
            identifier,
            TopModelPCVariantBlock(
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

private class TopModelPCVariantBlock(properties: BlockBehaviour.Properties) : PCBlock(properties) {
    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): PCBlockEntity? {
        return if (blockState.getValue(PART) == PCPart.TOP) PCBlockEntity(blockPos, blockState) else null
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState? {
        val abovePosition = blockPlaceContext.clickedPos.above()
        val world = blockPlaceContext.level
        if (world.getBlockState(abovePosition).canBeReplaced(blockPlaceContext) && !world.isOutsideBuildHeight(abovePosition)) {
            return defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, blockPlaceContext.horizontalDirection)
                .setValue(PART, PCPart.TOP)
                .setValue(WATERLOGGED, world.getFluidState(blockPlaceContext.clickedPos).type == Fluids.WATER)
        }

        return null
    }

    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack
    ) {
        world.setBlock(
            pos.above(),
            state
                .setValue(PART, PCPart.BOTTOM)
                .setValue(WATERLOGGED, world.getFluidState(pos.above()).type == Fluids.WATER),
            3
        )
        world.blockUpdated(pos, Blocks.AIR)
        state.updateNeighbourShapes(world, pos, 3)
    }

    override fun getPositionOfOtherPart(state: BlockState, pos: BlockPos): BlockPos {
        return if (state.getValue(PART) == PCPart.TOP) pos.above() else pos.below()
    }

    override fun getBasePosition(state: BlockState, pos: BlockPos): BlockPos {
        return if (state.getValue(PART) == PCPart.TOP) pos else pos.below()
    }

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        val belowPos = pos.below()
        val belowState = world.getBlockState(belowPos)
        return if (state.getValue(PART) == PCPart.TOP) {
            belowState.isFaceSturdy(world, belowPos, Direction.UP)
        } else {
            belowState.`is`(this)
        }
    }

    override fun getShape(
        blockState: BlockState,
        blockGetter: BlockGetter,
        blockPos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return if (blockState.getValue(PART) == PCPart.BOTTOM) Shapes.empty() else super.getShape(
            blockState,
            blockGetter,
            blockPos,
            collisionContext
        )
    }

    override fun getCollisionShape(
        blockState: BlockState,
        blockGetter: BlockGetter,
        blockPos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return if (blockState.getValue(PART) == PCPart.BOTTOM) Shapes.empty() else super.getCollisionShape(
            blockState,
            blockGetter,
            blockPos,
            collisionContext
        )
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }

        val isPc = neighborState.`is`(this)
        val part = state.getValue(PART)
        if (!isPc && part == PCPart.TOP && neighborPos == pos.above()) {
            return Blocks.AIR.defaultBlockState()
        } else if (!isPc && part == PCPart.BOTTOM && neighborPos == pos.below()) {
            return Blocks.AIR.defaultBlockState()
        }

        return state
    }
}
