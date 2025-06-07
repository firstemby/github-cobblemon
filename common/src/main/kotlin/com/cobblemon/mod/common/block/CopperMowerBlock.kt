/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class CopperMowerBlock(settings: Properties) : Block(settings), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
        )
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        var blockState = defaultBlockState()
            .setValue(WATERLOGGED, ctx.level.getFluidState(ctx.clickedPos).type == Fluids.WATER)
        val worldView = ctx.level
        val blockPos = ctx.clickedPos
        ctx.nearestLookingDirections.forEach { direction ->
            if (direction.axis.isHorizontal) {
                blockState = blockState.setValue(FACING, direction.opposite) as BlockState
                if (blockState.canSurvive(worldView, blockPos)) {
                    return blockState
                }
            }
        }
        return null
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING)
        builder.add(WATERLOGGED)
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return when (state.getValue(FACING)) {
            Direction.EAST -> EAST_OUTLINE
            Direction.SOUTH -> SOUTH_OUTLINE
            Direction.WEST -> WEST_OUTLINE
            else -> NORTH_OUTLINE
        }
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)))
    }

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return false
    }

    @Deprecated("Deprecated in Java")
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
        return if (direction == state.getValue(FACING) && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    companion object {
        val CODEC = simpleCodec(::CopperMowerBlock)

        val SOUTH_OUTLINE = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.0, 0.9375, 0.625, 0.375),
            Shapes.box(0.1875, 0.0, 0.375, 0.8125, 0.375, 1.0),
            Shapes.box(0.8125, 0.0, 0.5, 0.875, 0.3125, 0.8125),
            Shapes.box(0.125, 0.0, 0.5, 0.1875, 0.3125, 0.8125)
        )
        val NORTH_OUTLINE = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.625, 0.9375, 0.625, 1.0),
            Shapes.box(0.1875, 0.0, 0.0, 0.8125, 0.375, 0.625),
            Shapes.box(0.125, 0.0, 0.1875, 0.1875, 0.3125, 0.5),
            Shapes.box(0.8125, 0.0, 0.1875, 0.875, 0.3125, 0.5)
        )
        val WEST_OUTLINE = Shapes.or(
            Shapes.box(0.625, 0.0, 0.0625, 1.0, 0.625, 0.9375),
            Shapes.box(0.0, 0.0, 0.1875, 0.625, 0.375, 0.8125),
            Shapes.box(0.1875, 0.0, 0.8125, 0.5, 0.3125, 0.875),
            Shapes.box(0.1875, 0.0, 0.125, 0.5, 0.3125, 0.1875),
        )
        val EAST_OUTLINE = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0625, 0.375, 0.625, 0.9375),
            Shapes.box(0.375, 0.0, 0.1875, 1.0, 0.375, 0.8125),
            Shapes.box(0.5, 0.0, 0.125, 0.8125, 0.3125, 0.1875),
            Shapes.box(0.5, 0.0, 0.8125, 0.8125, 0.3125, 0.875)
        )
    }
}