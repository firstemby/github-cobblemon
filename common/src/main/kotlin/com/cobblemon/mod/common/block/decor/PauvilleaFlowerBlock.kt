/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BonemealableBlock
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class PauvilleaFlowerBlock(properties: Properties?) : DirectionalBlock(properties), BonemealableBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.DOWN))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        var blockState = defaultBlockState()
        val worldView = ctx.level
        val blockPos = ctx.clickedPos
        blockState = blockState.setValue(FACING, ctx.clickedFace) as BlockState
        if (blockState.canSurvive(worldView, blockPos)) {
            return blockState
        }
        return null
    }

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        val facing = state.getValue(FACING)
        if (direction == facing.opposite && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val direction = state.getValue(FACING) as Direction
        val blockState = level.getBlockState(pos.relative(direction.opposite))
        return blockState.isCollisionShapeFullBlock(level, pos) or blockState.canOcclude()
    }

    override fun codec(): MapCodec<PauvilleaFlowerBlock> = CODEC

    override fun getShape(state: BlockState, blockGetter: BlockGetter, pos: BlockPos, collisionContext: CollisionContext): VoxelShape {
        return when (state.getValue(FACING)) {
            Direction.DOWN -> CEILING
            Direction.NORTH -> WALL_NORTH
            Direction.EAST -> WALL_EAST
            Direction.SOUTH -> WALL_SOUTH
            Direction.WEST -> WALL_WEST
            Direction.UP -> FLOOR
        }
    }

    companion object {
        val CODEC: MapCodec<PauvilleaFlowerBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::PauvilleaFlowerBlock)
        }
        val FLOOR = Shapes.box(0.075, 0.0, 0.075, 0.925, 0.4375, 0.925)
        val WALL_NORTH = Shapes.box(0.0, 0.0, 0.8125, 1.0, 1.0, 1.0)
        val WALL_EAST = Shapes.box(0.0, 0.0, 0.0, 0.1875, 1.0, 1.0)
        val WALL_SOUTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.1875)
        val WALL_WEST = Shapes.box(0.8125, 0.0, 0.0, 1.0, 1.0, 1.0)
        val CEILING = Shapes.box(0.075, 0.0, 0.075, 0.925, 1.0, 0.925)
    }

    override fun isValidBonemealTarget(level: LevelReader?, pos: BlockPos?, state: BlockState?): Boolean {
        return true
    }

    override fun isBonemealSuccess(level: Level?, random: RandomSource?, pos: BlockPos?, state: BlockState?): Boolean {
        return true
    }

    override fun performBonemeal(level: ServerLevel, random: RandomSource?, pos: BlockPos, state: BlockState?) {
        popResource(level, pos, ItemStack(this))
    }
}