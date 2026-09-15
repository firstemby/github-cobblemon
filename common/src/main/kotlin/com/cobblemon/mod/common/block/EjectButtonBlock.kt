/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.util.rotateShape
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockSetType
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class EjectButtonBlock(properties: Properties, ticksToStayPressed: Int) : ButtonBlock(BlockSetType.IRON, ticksToStayPressed, properties), SimpleWaterloggedBlock  {
    companion object {
        val FLOOR_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.0, 0.125, 0.875, 0.1875, 0.875),
            Shapes.box(0.375, 0.1875, 0.375, 0.625, 0.3125, 0.625) )
        val FLOOR_POWERED = Shapes.or(
            Shapes.box(0.125, 0.0, 0.125, 0.875, 0.1875, 0.875),
            Shapes.box(0.375, 0.1875, 0.375, 0.625, 0.25, 0.625) )
        val FLOOR_PANEL = Shapes.box(0.125, 0.0, 0.125, 0.875, 0.1875, 0.875)
        val CEILING_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.8125, 0.125, 0.875, 1.0, 0.875),
            Shapes.box(0.375, 0.6875, 0.375, 0.625, 0.8125, 0.625) )
        val CEILING_POWERED = Shapes.or(
            Shapes.box(0.125, 0.8125, 0.125, 0.875, 1.0, 0.875),
            Shapes.box(0.375, 0.75, 0.375, 0.625, 0.8125, 0.625) )
        val CEILING_PANEL = Shapes.box(0.125, 0.8125, 0.125, 0.875, 1.0, 0.875)
        val WALL_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.125, 0.8125, 0.875, 0.875, 1.0),
            Shapes.box(0.375, 0.375, 0.6875, 0.625, 0.625, 0.8125) )
        val WALL_POWERED = Shapes.or(
            Shapes.box(0.125, 0.125, 0.8125, 0.875, 0.875, 1.0),
            Shapes.box(0.375, 0.375, 0.75, 0.625, 0.625, 0.8125) )
        val WALL_PANEL = Shapes.box(0.125, 0.125, 0.8125, 0.875, 0.875, 1.0)
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL)
            .setValue(POWERED, false)
            .setValue(WATERLOGGED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, POWERED, WATERLOGGED)
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        for (direction in context.nearestLookingDirections) {
            val blockState: BlockState
            if (direction.getAxis() === Direction.Axis.Y) {
                blockState = (defaultBlockState()
                    .setValue(FACE, if (direction == Direction.UP) AttachFace.CEILING else AttachFace.FLOOR) as BlockState)
                    .setValue(FACING, context.horizontalDirection.opposite)
                    .setValue(WATERLOGGED, fluidState.type === Fluids.WATER
                    ) as BlockState
            } else {
                blockState = (defaultBlockState()
                    .setValue(FACE, AttachFace.WALL) as BlockState)
                    .setValue(FACING, direction.opposite)
                    .setValue(WATERLOGGED, fluidState.type === Fluids.WATER
                    ) as BlockState
            }
            if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockState
            }
        }
        return null
    }

    override fun getShape(state: BlockState, blockGetter: BlockGetter, pos: BlockPos, collisionContext: CollisionContext): VoxelShape {
        val direction = state.getValue(FACING)
        return when (state.getValue(FACE)) {
            AttachFace.FLOOR -> if (state.getValue(POWERED)) {FLOOR_POWERED} else {FLOOR_UNPOWERED}
            AttachFace.WALL ->
                when (direction) {
                    Direction.NORTH, Direction.UP, Direction.DOWN -> if (state.getValue(POWERED)) {WALL_POWERED} else {WALL_UNPOWERED}
                    else -> rotateShape(Direction.NORTH, direction, if (state.getValue(POWERED)) {WALL_POWERED} else {WALL_UNPOWERED})
                }
            AttachFace.CEILING -> if (state.getValue(POWERED)) {CEILING_POWERED} else {CEILING_UNPOWERED}
        }
    }
    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val direction = state.getValue(FACING)
        return when (state.getValue(FACE)) {
            AttachFace.FLOOR -> FLOOR_PANEL
            AttachFace.WALL -> when (direction) {
                Direction.NORTH, Direction.UP, Direction.DOWN -> WALL_PANEL
                else -> rotateShape(Direction.NORTH, direction, WALL_PANEL)
            }
            AttachFace.CEILING -> CEILING_PANEL
        }
    }

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, world: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }
        return if (getConnectedDirection(state).opposite == direction && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return if (state.getValue(FACE) != AttachFace.WALL) {
            val direction = getConnectedDirection(state)
            canSupportCenter(level, pos.relative(direction.opposite), direction)
        } else {
            level.getBlockState(pos.relative(state.getValue(FACING).opposite)).isSolid
        }
    }
}
