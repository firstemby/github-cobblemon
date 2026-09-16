/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

open class SixFaceRotationalBlock(properties : Properties) : FaceAttachedHorizontalDirectionalBlock(properties), SimpleWaterloggedBlock {
    companion object {
        val CODEC = simpleCodec(::SixFaceRotationalBlock)
    }

    override fun codec(): MapCodec<out SixFaceRotationalBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, WATERLOGGED)
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

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, world: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }
        return if (getConnectedDirection(state).opposite == direction && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return if (state.getValue(FACE) == AttachFace.CEILING) {
            val direction = getConnectedDirection(state)
            canSupportCenter(level, pos.relative(direction.opposite), direction)
        } else if (state.getValue(FACE) == AttachFace.WALL) {
            level.getBlockState(pos.relative(state.getValue(FACING).opposite)).isSolid
        } else {
            super.canSurvive(state, level, pos)
        }
    }
}
