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

class DecorativeItemBlock(settings: Properties, val itemType: ItemType) : Block(settings), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false))
    }

    @Suppress("unused")
    enum class ItemType : StringRepresentable {
        TEAPOT,
        TEACUP,
        MOSS,
        KNOT,
        POUCH,
        GENERIC_7x6,
        GENERIC_8x,
        GENERIC_8x4;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    override fun codec(): MapCodec<DecorativeItemBlock> {
        return CODEC
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

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
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
        return when (itemType) {
            ItemType.TEAPOT -> {
                when (state.getValue(FACING)) {
                    Direction.SOUTH -> TEAPOT_SOUTH
                    Direction.WEST -> TEAPOT_WEST
                    Direction.EAST -> TEAPOT_EAST
                    else -> TEAPOT_NORTH
                }
            }
            ItemType.KNOT -> {
                when (state.getValue(FACING)) {
                    Direction.SOUTH -> KNOT_SOUTH
                    Direction.WEST -> KNOT_WEST
                    Direction.EAST -> KNOT_EAST
                    else -> KNOT_NORTH
                }
            }
            ItemType.TEACUP -> TEACUP
            ItemType.MOSS -> MOSS
            ItemType.POUCH -> POUCH
            ItemType.GENERIC_7x6 -> ITEM_7x6
            ItemType.GENERIC_8x4 -> ITEM_8x4
            else -> ITEM_8x
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
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)))
    }

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return false
    }

    companion object {
        val CODEC: MapCodec<DecorativeItemBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec(),
                ItemType.CODEC.fieldOf("itemType").forGetter(DecorativeItemBlock::itemType)
        ).apply(it, ::DecorativeItemBlock) }

        val ITEM_8x = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75)
        val ITEM_8x4 = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.25, 0.75)
        val ITEM_7x6 = Shapes.box(0.28125, 0.0, 0.28125, 0.71875, 0.375, 0.71875)
        val POUCH = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.3125, 0.6875)
        private val TEACUP = Shapes.or(
            Shapes.box(0.375, 0.0, 0.375, 0.625, 0.0625, 0.625),
            Shapes.box(0.3125, 0.0, 0.3125, 0.625, 0.25, 0.375),
            Shapes.box(0.375, 0.0, 0.625, 0.6875, 0.25, 0.6875),
            Shapes.box(0.625, 0.0, 0.3125, 0.6875, 0.25, 0.625),
            Shapes.box(0.3125, 0.0, 0.375, 0.375, 0.25, 0.6875)
        )
        private val MOSS = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.3125, 0.8125),
            Shapes.box(0.25, 0.3125, 0.25, 0.75, 0.5625, 0.75)
        )
        val KNOT_NORTH = Shapes.box(0.375, 0.0, 0.375, 0.75, 0.375, 0.75)
        val KNOT_WEST = Shapes.box(0.375, 0.0, 0.25, 0.75, 0.375, 0.625)
        val KNOT_SOUTH = Shapes.box(0.25, 0.0, 0.25, 0.625, 0.375, 0.625)
        val KNOT_EAST = Shapes.box(0.25, 0.0, 0.375, 0.625, 0.375, 0.75)
        private val TEAPOT_NORTH = Shapes.or(
            Shapes.box(0.3125, 0.5, 0.3125, 0.6875, 0.625, 0.6875),
            Shapes.box(0.4375, 0.625, 0.4375, 0.5625, 0.6875, 0.5625),
            Shapes.box(0.25, 0.0625, 0.25, 0.75, 0.5, 0.75),
            Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.0625, 0.6875),
            Shapes.box(0.75, 0.125, 0.40625, 0.875, 0.375, 0.59375)
        )
        private val TEAPOT_EAST = Shapes.or(
            Shapes.box(0.3125, 0.5, 0.3125, 0.6875, 0.625, 0.6875),
            Shapes.box(0.4375, 0.625, 0.4375, 0.5625, 0.6875, 0.5625),
            Shapes.box(0.25, 0.0625, 0.25, 0.75, 0.5, 0.75),
            Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.0625, 0.6875),
            Shapes.box(0.40625, 0.125, 0.75, 0.59375, 0.375, 0.875)
        )
        private val TEAPOT_SOUTH = Shapes.or(
            Shapes.box(0.3125, 0.5, 0.3125, 0.6875, 0.625, 0.6875),
            Shapes.box(0.4375, 0.625, 0.4375, 0.5625, 0.6875, 0.5625),
            Shapes.box(0.25, 0.0625, 0.25, 0.75, 0.5, 0.75),
            Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.0625, 0.6875),
            Shapes.box(0.125, 0.125, 0.40625, 0.25, 0.375, 0.59375)
        )
        private val TEAPOT_WEST = Shapes.or(
            Shapes.box(0.3125, 0.5, 0.3125, 0.6875, 0.625, 0.6875),
            Shapes.box(0.4375, 0.625, 0.4375, 0.5625, 0.6875, 0.5625),
            Shapes.box(0.25, 0.0625, 0.25, 0.75, 0.5, 0.75),
            Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.0625, 0.6875),
            Shapes.box(0.40625, 0.125, 0.125, 0.59375, 0.375, 0.25)
        )
    }
}