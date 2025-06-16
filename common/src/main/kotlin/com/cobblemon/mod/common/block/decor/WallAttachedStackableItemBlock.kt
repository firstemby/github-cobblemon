/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import com.cobblemon.mod.common.block.decor.StackableItemBlock.Companion
import com.cobblemon.mod.common.block.decor.StackableItemBlock.Companion.ITEMS
import com.cobblemon.mod.common.block.decor.StackableItemBlock.Companion.MAX_ITEMS
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
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.min

@Suppress("DEPRECATED", "OVERRIDE_DEPRECATION")
class WallAttachedStackableItemBlock(settings: Properties, val wallStackableItemType: WallStackableItemType) : Block(settings), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(ITEMS, 1)
            .setValue(WATERLOGGED, false))
    }

    @Suppress("unused")
    enum class WallStackableItemType : StringRepresentable {
        TAG,
        GENERIC_8x;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    override fun codec(): MapCodec<WallAttachedStackableItemBlock> {
        return CODEC
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val blockState = context.level.getBlockState(context.clickedPos)
        return if (blockState.`is`(this)) blockState.setValue(
            ITEMS,
            min(MAX_ITEMS, (blockState.getValue(ITEMS) + 1))
        ) else {
            var blockState = defaultBlockState()
                .setValue(WATERLOGGED, context.level.getFluidState(context.clickedPos).type == Fluids.WATER)
            val worldView = context.level
            val blockPos = context.clickedPos
            context.nearestLookingDirections.forEach { direction ->
                if (direction.axis.isHorizontal) {
                    blockState = blockState.setValue(FACING, direction.opposite)
                    if (blockState.canSurvive(worldView, blockPos)) {
                        return blockState
                    }
                }
            }
            return null
        }
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        builder.add(ITEMS)
        builder.add(WATERLOGGED)
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return when (wallStackableItemType) {
            WallStackableItemType.TAG -> return when (state.getValue(ITEMS)) {
                2 -> when (state.getValue(FACING)) {
                    Direction.SOUTH -> TAGS_SOUTH
                    Direction.WEST -> TAGS_WEST
                    Direction.EAST -> TAGS_EAST
                    else -> TAGS_NORTH
                }
                3, 4 -> when (state.getValue(FACING)) {
                    Direction.SOUTH -> TAGS_WIDE_SOUTH
                    Direction.WEST -> TAGS_WIDE_WEST
                    Direction.EAST -> TAGS_WIDE_EAST
                    else -> TAGS_WIDE_NORTH
                }
                5, 6, 7, 8 -> when (state.getValue(FACING)) {
                    Direction.SOUTH -> FULL_PLANE_SOUTH
                    Direction.WEST -> FULL_PLANE_WEST
                    Direction.EAST -> FULL_PLANE_EAST
                    else -> FULL_PLANE_NORTH
                }
                else -> when (state.getValue(FACING)) {
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
        return if (direction.opposite == state.getValue(FACING) && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    public override fun canBeReplaced(state: BlockState, useContext: BlockPlaceContext): Boolean {
        return if (!useContext.isSecondaryUseActive && useContext.itemInHand.`is`(this.asItem()) && state.getValue(
                ITEMS
            ) <= MAX_ITEMS
        ) true else super.canBeReplaced(state, useContext)
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return level!!.getBlockState(pos.relative(state.getValue(FACING).opposite)).isSolid
    }

    companion object {
        val CODEC: MapCodec<WallAttachedStackableItemBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec(),
                WallStackableItemType.CODEC.fieldOf("wallStackableItemType").forGetter(WallAttachedStackableItemBlock::wallStackableItemType)
        ).apply(it, ::WallAttachedStackableItemBlock) }

        val ITEMS = IntegerProperty.create("items", 1, 8)
        const val MAX_ITEMS: Int = 8

        val ITEM_8x_NORTH = Shapes.box(0.25, 0.25, 0.5, 0.75, 0.75, 1.0)
        val ITEM_8x_WEST = Shapes.box(0.5, 0.25, 0.25, 1.0, 0.75, 0.75)
        val ITEM_8x_SOUTH = Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.5)
        val ITEM_8x_EAST = Shapes.box(0.0, 0.25, 0.25, 0.5, 0.75, 0.75)

        val FULL_PLANE_NORTH = Shapes.box(0.0, 0.0, 0.925, 1.0, 1.0, 1.0)
        val FULL_PLANE_WEST = Shapes.box(0.925, 0.0, 0.0, 1.0, 1.0, 1.0)
        val FULL_PLANE_SOUTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.075)
        val FULL_PLANE_EAST = Shapes.box(0.0, 0.0, 0.0, 0.075, 1.0, 1.0)

        //One Tag
        val TAG_NORTH = Shapes.box(0.375, 0.25, 0.925, 0.625, 0.6875, 1.0)
        val TAG_WEST = Shapes.box(0.925, 0.25, 0.375, 1.0, 0.6875, 0.625)
        val TAG_SOUTH = Shapes.box(0.375, 0.25, 0.0, 0.625, 0.6875, 0.075)
        val TAG_EAST = Shapes.box(0.0, 0.25, 0.375, 0.075, 0.6875, 0.625)
        //Two Tags
        val TAGS_NORTH = Shapes.box(0.375, 0.125, 0.925, 0.875, 0.6875, 1.0)
        val TAGS_WEST = Shapes.box(0.925, 0.125, 0.125, 1.0, 0.6875, 0.625)
        val TAGS_SOUTH = Shapes.box(0.125, 0.125, 0.0, 0.625, 0.6875, 0.075)
        val TAGS_EAST = Shapes.box(0.0, 0.125, 0.375, 0.075, 0.6875, 0.875)
        //Three and Four Tags
        val TAGS_WIDE_NORTH = Shapes.box(0.125, 0.125, 0.925, 0.875, 0.875, 1.0)
        val TAGS_WIDE_WEST = Shapes.box(0.925, 0.125, 0.125, 1.0, 0.875, 0.875)
        val TAGS_WIDE_SOUTH = Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 0.075)
        val TAGS_WIDE_EAST = Shapes.box(0.0, 0.125, 0.125, 0.075, 0.875, 0.875)
    }
}