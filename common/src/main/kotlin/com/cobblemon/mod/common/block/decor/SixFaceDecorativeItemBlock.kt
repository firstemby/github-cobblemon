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
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("DEPRECATED", "OVERRIDE_DEPRECATION")
class SixFaceDecorativeItemBlock(properties : Properties) : FaceAttachedHorizontalDirectionalBlock(properties), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.FLOOR)
            .setValue(WATERLOGGED, false))
    }

/*    @Suppress("unused")
    enum class SixFaceItemType : StringRepresentable {
        GENERIC_8x;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }*/

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        builder.add(FACE)
        builder.add(WATERLOGGED)
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    //May need some clean up, ended up just copying this straight from the original class so I could inject the waterlogging stuff to make it properly work, but it does look a bit ugly
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val var2 = context.nearestLookingDirections
        val var3 = var2.size
        val fluidState = context.level.getFluidState(context.clickedPos)

        for (var4 in 0 until var3) {
            val direction = var2[var4]
            var blockState = if (direction.axis === Direction.Axis.Y) {
                defaultBlockState().setValue(
                    FACE,
                    if (direction == Direction.UP) AttachFace.CEILING else AttachFace.FLOOR
                )
                    .setValue(FACING, context.horizontalDirection.opposite).setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
            } else {
                (defaultBlockState().setValue(FACE, AttachFace.WALL) as BlockState).setValue(
                    FACING,
                    direction.opposite
                ).setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
            }
            if (blockState.canSurvive(context.level, context.clickedPos)) {
                return blockState
            }
        }
        return null
    }

    override fun codec(): MapCodec<SixFaceDecorativeItemBlock> {
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
            AttachFace.FLOOR -> {
                FLOOR_8x
            }
            AttachFace.WALL -> {
                when (direction) {
                    Direction.EAST -> EAST_WALL_8x
                    Direction.WEST -> WEST_WALL_8x
                    Direction.SOUTH -> SOUTH_WALL_8x
                    Direction.NORTH, Direction.UP, Direction.DOWN -> NORTH_WALL_8x
                }
            }
            AttachFace.CEILING -> CEILING_8x
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
        return if (getConnectedDirection(state).opposite == direction && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    @Deprecated("Deprecated in Java")
    override fun canSurvive(state: BlockState?, level: LevelReader?, pos: BlockPos): Boolean {
        return true
    }

    companion object {
        val CODEC: MapCodec<SixFaceDecorativeItemBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec()
            ).apply(it, ::SixFaceDecorativeItemBlock) }

        val FLOOR_8x = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75)
        val EAST_WALL_8x = Shapes.box(0.0, 0.25, 0.25, 0.5, 0.75, 0.75)
        val WEST_WALL_8x = Shapes.box(0.5, 0.25, 0.25, 1.0, 0.75, 0.75)
        val NORTH_WALL_8x = Shapes.box(0.25, 0.25, 0.5, 0.75, 0.75, 1.0)
        val SOUTH_WALL_8x = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.5)
        val CEILING_8x = Shapes.box(0.25, 0.5, 0.25, 0.75, 1.0, 0.75)
    }
}