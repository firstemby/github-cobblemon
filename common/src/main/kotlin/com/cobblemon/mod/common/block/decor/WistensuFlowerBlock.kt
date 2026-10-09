/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import com.cobblemon.mod.common.CobblemonBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty

interface WistensuFlowerBlock {
    fun additionalBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.FACING)
        builder.add(ROOT)
    }

    fun adjustStateForPlacement(context: BlockPlaceContext, defaultState: BlockState): BlockState {
        val upperBlock = context.level.getBlockState(context.clickedPos.relative(Direction.UP))
        if (upperBlock.`is`(CobblemonBlocks.WISTENSU) || upperBlock.`is`(CobblemonBlocks.WISTENSU_VINE)) {
            return defaultState
                .setValue(BlockStateProperties.FACING, Direction.DOWN)
                .setValue(ROOT, false)
        } else {
            var placeDirection = context.clickedFace
            if (placeDirection == Direction.UP) placeDirection = Direction.DOWN
            return defaultState
                .setValue(BlockStateProperties.FACING, placeDirection)
                .setValue(ROOT, true)
        }
    }

    fun copyStateValues(target: BlockState, source: BlockState): BlockState {
        return target
            .setValue(ROOT, source.getValue(ROOT))
            .setValue(BlockStateProperties.FACING, source.getValue(BlockStateProperties.FACING))
    }

    fun wisternCanSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        if (state.getValue(ROOT)) {
            val facing = state.getValue(BlockStateProperties.FACING)
            val targetPos = pos.relative(facing.opposite)
            val directionState = level.getBlockState(targetPos)
            return directionState.isFaceSturdy(level, targetPos, facing)
        } else {
            val topState = level.getBlockState(pos.relative(Direction.UP))
            return topState.`is`(CobblemonBlocks.WISTENSU) || topState.`is`(CobblemonBlocks.WISTENSU_VINE)
        }
    }

    fun wisternUpdateShape(state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos, block: Block): BlockState? {
        if (!wisternCanSurvive(state, level, pos)) {
            level.scheduleTick(pos, block, 1)
        }

        if (direction == Direction.UP &&
            (neighborState.`is`(CobblemonBlocks.WISTENSU) || neighborState.`is`(CobblemonBlocks.WISTENSU_VINE))) {
            return state
                .setValue(ROOT, false)
                .setValue(BlockStateProperties.FACING, Direction.DOWN)
        }

        return null
    }

    companion object {
        val ROOT = BooleanProperty.create("root")
    }
}