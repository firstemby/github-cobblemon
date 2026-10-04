/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor


import com.cobblemon.mod.common.CobblemonBlocks
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.GrowingPlantHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.min

//TODO: Root/connection shit
class WistensuFlowerHeadBlock(properties: Properties?) : GrowingPlantHeadBlock(properties, Direction.DOWN, SHAPE, false, 0.1), WistensuFlowerBlock {
    companion object {
        val CODEC: MapCodec<WistensuFlowerHeadBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::WistensuFlowerHeadBlock)
        }
        val SHAPE: VoxelShape = box(1.0, 2.0, 1.0, 15.0, 16.0, 15.0)
    }
    override fun codec(): MapCodec<WistensuFlowerHeadBlock> = CODEC

    override fun getBlocksToGrowWhenBonemealed(random: RandomSource): Int {
        var d = 1.0
        var i = 0
        while (random.nextDouble() < d) {
            d *= 0.826
            ++i
        }
        return i
    }

    override fun canGrowInto(state: BlockState): Boolean {
        return state.isAir
    }

    override fun getBodyBlock(): Block {
        return CobblemonBlocks.WISTENSU_VINE
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        additionalBlockStateDefinition(builder)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        val updateState = wisternUpdateShape(state, direction, neighborState, level, pos, neighborPos, this)
        if (updateState != null) return updateState

        if (direction == Direction.DOWN &&
            (neighborState.`is`(CobblemonBlocks.WISTENSU) || neighborState.`is`(CobblemonBlocks.WISTENSU_VINE))) {
            return updateBodyAfterConvertedFromHead(state, bodyBlock.defaultBlockState())
        }
        return state
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val defaultState = super.getStateForPlacement(context)!!
        return adjustStateForPlacement(context, defaultState)
    }

    override fun getGrowIntoState(state: BlockState, random: RandomSource): BlockState {
        return super.getGrowIntoState(state, random)
            .setValue(WistensuFlowerBlock.ROOT, false)
            .setValue(BlockStateProperties.FACING, Direction.DOWN)
    }

    override fun updateBodyAfterConvertedFromHead(head: BlockState, body: BlockState): BlockState {
        return copyStateValues(body, head)
    }

    override fun performBonemeal(level: ServerLevel, random: RandomSource, pos: BlockPos, state: BlockState) {
        var blockPos = pos.relative(growthDirection)
        var i = min(state.getValue(AGE), MAX_AGE)
        val j = getBlocksToGrowWhenBonemealed(random)
        var currentState = state

        for (k in 0..<j) {
            if (!canGrowInto(level.getBlockState(blockPos))) break
            level.setBlockAndUpdate(blockPos, getGrowIntoState(currentState, random))
            currentState = level.getBlockState(blockPos)
            blockPos = blockPos.relative(growthDirection)
            i = min(i + 1, MAX_AGE)
        }
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return wisternCanSurvive(state, level, pos)
    }
}