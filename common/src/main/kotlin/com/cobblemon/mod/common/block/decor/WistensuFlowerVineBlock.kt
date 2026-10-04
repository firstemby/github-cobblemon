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
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.GrowingPlantBodyBlock
import net.minecraft.world.level.block.GrowingPlantHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.shapes.VoxelShape

//TODO: Root/connection shit
class WistensuFlowerVineBlock(properties: Properties?) : GrowingPlantBodyBlock(properties, Direction.DOWN, SHAPE, false), WistensuFlowerBlock
{
    companion object {
        val CODEC: MapCodec<WistensuFlowerVineBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::WistensuFlowerVineBlock)
        }
        val SHAPE: VoxelShape = box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0)
    }
    override fun codec(): MapCodec<WistensuFlowerVineBlock> = CODEC

    override fun getHeadBlock(): GrowingPlantHeadBlock {
        return CobblemonBlocks.WISTENSU
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
                !neighborState.`is`(CobblemonBlocks.WISTENSU) && !neighborState.`is`(CobblemonBlocks.WISTENSU_VINE)) {
            return updateHeadAfterConvertedFromBody(state, headBlock.getStateForPlacement(level))
        }
        return state
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val defaultState = super.getStateForPlacement(context)!!
        return adjustStateForPlacement(context, defaultState)
    }

    override fun updateHeadAfterConvertedFromBody(head: BlockState, body: BlockState): BlockState {
        return copyStateValues(body, head)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return wisternCanSurvive(state, level, pos)
    }
}