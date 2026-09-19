/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.appliances

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
//TODO: Oxidation/Waxing/Scraping not breaking the blocks
//TODO: All the Fridge appliance stuff
open class CopperFridgeBlock(settings: Properties) : HorizontalDirectionalBlock(settings), SimpleWaterloggedBlock {

    companion object {
        val CODEC: MapCodec<CopperFridgeBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::CopperFridgeBlock) }
        val HALF: EnumProperty<DoubleBlockHalf> = BlockStateProperties.DOUBLE_BLOCK_HALF
        val POWERED = BooleanProperty.create("powered")

        val LOWER_HITBOX = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.125, 0.9375, 0.0625, 0.9375),
            Shapes.box(0.0, 0.0625, 0.0625, 1.0, 1.0, 1.0))
        val UPPER_HITBOX = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0625, 1.0, 1.0, 1.0),
            Shapes.box(0.25, 0.125, 0.0, 0.375, 0.3125, 0.0625),
            Shapes.box(0.625, 0.125, 0.0, 0.75, 0.3125, 0.0625))
        val UPPER_COLLISSIONBOX = Shapes.box(0.0, 0.0, 0.0625, 1.0, 1.0, 1.0)
        //The handles get ignored by entity collission, because it feels nicer, but still count as part of the hitbox
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(WATERLOGGED, false)
            .setValue(POWERED, false)
        )
    }

    override fun codec(): MapCodec<CopperFridgeBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING, HALF, WATERLOGGED, POWERED)
    }

    override fun getFluidState(blockState: BlockState): FluidState? = if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState)

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, world: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }

        val isFridge = neighborState.`is`(this)
        val half = state.getValue(HALF)
        if (!isFridge && half == DoubleBlockHalf.UPPER && neighborPos == pos.below()) {
            return Blocks.AIR.defaultBlockState()
        } else if (!isFridge && half == DoubleBlockHalf.LOWER && neighborPos == pos.above()) {
            return Blocks.AIR.defaultBlockState()
        }
        return state
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val direction = state.getValue(HORIZONTAL_FACING)
        return if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            rotateShape(Direction.NORTH, direction, LOWER_HITBOX)
        } else {
            rotateShape(Direction.NORTH, direction, UPPER_COLLISSIONBOX)
        }
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val direction = state.getValue(HORIZONTAL_FACING)
        return if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            rotateShape(Direction.NORTH, direction, LOWER_HITBOX)
        } else {
            rotateShape(Direction.NORTH, direction, UPPER_HITBOX)
        }
    }

    override fun setPlacedBy(world: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        world.setBlock(pos.above(),
            state
                .setValue(HALF, DoubleBlockHalf.UPPER)
                .setValue(WATERLOGGED, world.getFluidState((pos.above())).type == Fluids.WATER)
            , 3)
        world.blockUpdated(pos, Blocks.AIR)
        state.updateNeighbourShapes(world, pos, 3)
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState? {
        val abovePosition = blockPlaceContext.clickedPos.above()
        val world = blockPlaceContext.level
        if (world.getBlockState(abovePosition).canBeReplaced(blockPlaceContext) && !world.isOutsideBuildHeight(abovePosition)) {
            return defaultBlockState()
                .setValue(FACING, blockPlaceContext.horizontalDirection.opposite)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(WATERLOGGED, blockPlaceContext.level.getFluidState(blockPlaceContext.clickedPos).type == Fluids.WATER)
        }
        return null
    }

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        val blockPos = pos.below()
        val blockState = world.getBlockState(blockPos)
        return if (state.getValue(HALF) == DoubleBlockHalf.LOWER) blockState.isFaceSturdy(world, blockPos, Direction.UP) else blockState.`is`(this)
    }

    override fun playerWillDestroy(world: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!world.isClientSide && player.isCreative) {
            var blockPos: BlockPos = BlockPos.ZERO
            var blockState: BlockState = state
            val half = state.getValue(HALF)
            if (half == DoubleBlockHalf.UPPER && world.getBlockState(pos.below().also { blockPos = it }).also { blockState = it }.`is`(state.block) && blockState.getValue(
                    HALF
                ) == DoubleBlockHalf.LOWER) {
                val blockState2 = if (blockState.fluidState.`is`(Fluids.WATER)) Blocks.WATER.defaultBlockState() else Blocks.AIR.defaultBlockState()
                world.setBlock(blockPos, blockState2, UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
                world.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, getId(blockState))
            }
        }
        return super.playerWillDestroy(world, pos, state, player)
    }
}