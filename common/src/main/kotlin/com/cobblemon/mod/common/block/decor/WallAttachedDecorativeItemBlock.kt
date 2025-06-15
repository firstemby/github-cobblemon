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
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
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

class WallAttachedDecorativeItemBlock(settings: Properties, val wallItemType: WallItemType) : Block(settings), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false))
    }

    @Suppress("unused")
    enum class WallItemType : StringRepresentable {
        TAG,
        GENERIC_8x;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    override fun codec(): MapCodec<WallAttachedDecorativeItemBlock> {
        return CODEC
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        var blockState = defaultBlockState()
            .setValue(WATERLOGGED, ctx.level.getFluidState(ctx.clickedPos).type == Fluids.WATER)
        val worldView = ctx.level
        val blockPos = ctx.clickedPos
        ctx.nearestLookingDirections.forEach { direction ->
            if (direction.axis.isHorizontal) {
                blockState = blockState.setValue(FACING, direction) as BlockState
                if (blockState.canSurvive(worldView, blockPos)) {
                    return blockState
                }
            }
        }
        return null
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        builder.add(WATERLOGGED)
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return when (wallItemType) {
            WallItemType.TAG -> {
                when (state.getValue(FACING)) {
                    Direction.SOUTH -> TAG_SOUTH
                    Direction.WEST -> TAG_WEST
                    Direction.EAST -> TAG_EAST
                    else -> TAG_NORTH
                }
            }
            else -> {
                when (state.getValue(FACING)) {
                    Direction.SOUTH -> ITEM_8x_SOUTH
                    Direction.WEST -> ITEM_8x_WEST
                    Direction.EAST -> ITEM_8x_EAST
                    else -> ITEM_8x_NORTH
                }
            }
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

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return false
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val direction = state.getValue(FACING)
        val blockpos = pos.relative(direction.opposite)
        val blockstate = level.getBlockState(blockpos)
        return direction.axis.isHorizontal && blockstate.isFaceSturdy(level, blockpos, direction)
    }

    companion object {
        val CODEC: MapCodec<WallAttachedDecorativeItemBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec(),
                WallItemType.CODEC.fieldOf("wallItemType").forGetter(WallAttachedDecorativeItemBlock::wallItemType)
        ).apply(it, ::WallAttachedDecorativeItemBlock) }

        val ITEM_8x_NORTH = Shapes.box(0.25, 0.25, 0.5, 0.75, 0.75, 1.0)
        val ITEM_8x_WEST = Shapes.box(0.5, 0.25, 0.25, 1.0, 0.75, 0.75)
        val ITEM_8x_SOUTH = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.5)
        val ITEM_8x_EAST = Shapes.box(0.0, 0.25, 0.25, 0.5, 0.75, 0.75)

        val TAG_NORTH = Shapes.box(0.25, 0.125, 0.875, 0.75, 0.75, 1.0)
        val TAG_WEST = Shapes.box(0.875, 0.125, 0.25, 1.0, 0.875, 0.75)
        val TAG_SOUTH = Shapes.box(0.25, 0.125, 0.0, 0.75, 0.875, 0.125)
        val TAG_EAST = Shapes.box(0.0, 0.125, 0.25, 0.125, 0.875, 0.75)
    }
}