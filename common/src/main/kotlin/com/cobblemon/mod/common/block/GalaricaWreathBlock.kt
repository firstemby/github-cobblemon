/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class GalaricaWreathBlock(properties : Properties) : FaceAttachedHorizontalDirectionalBlock(properties), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL)
            .setValue(WATERLOGGED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        builder.add(FACE)
        builder.add(WATERLOGGED)
    }

    override fun codec(): MapCodec<GalaricaWreathBlock> {
        return CODEC
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        val direction = state.getValue(FACING)
        return when (state.getValue(FACE)) {
            AttachFace.FLOOR -> {FLOOR}

            AttachFace.WALL -> {
                when (direction) {
                    Direction.EAST -> EAST_WALL
                    Direction.WEST -> WEST_WALL
                    Direction.SOUTH -> SOUTH_WALL
                    Direction.NORTH, Direction.UP, Direction.DOWN -> NORTH_WALL
                }
            }

            AttachFace.CEILING -> return if (direction.axis === Direction.Axis.X) {HANGING_NS} else {HANGING_EW}
        }
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

    override fun canSurvive(state: BlockState?, level: LevelReader?, pos: BlockPos): Boolean {
        val direction = getConnectedDirection(state!!).opposite
        return canSupportCenter(level!!, pos.relative(direction), direction.opposite)
    }

    companion object {
        val CODEC: MapCodec<GalaricaWreathBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec()
            ).apply(it, ::GalaricaWreathBlock) }

        val FLOOR = Shapes.box(0.171875, 0.0, 0.171875, 0.828125, 0.203125, 0.828125)
        val EAST_WALL = Shapes.box(0.0, 0.171875, 0.171875, 0.203125, 0.828125, 0.828125)
        val WEST_WALL = Shapes.box(0.796875, 0.171875, 0.171875, 1.0, 0.828125, 0.828125)
        val NORTH_WALL = Shapes.box(0.171875, 0.171875, 0.796875, 0.828125, 0.828125, 1.0)
        val SOUTH_WALL = Shapes.box(0.171875, 0.171875, 0.0, 0.828125, 0.828125, 0.203125)
        val HANGING_EW = Shapes.box(0.171875, 0.171875, 0.390625, 0.828125, 0.828125, 0.609375)
        val HANGING_NS = Shapes.box(0.390625, 0.171875, 0.171875, 0.609375, 0.828125, 0.828125)
    }
}