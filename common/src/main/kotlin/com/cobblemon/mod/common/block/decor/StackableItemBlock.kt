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
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.min

class StackableItemBlock(settings: Properties, val stackableItemType: StackableItemType) : Block(settings), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(ITEMS, 1)
            .setValue(WATERLOGGED, false))
    }

    @Suppress("unused")
    enum class StackableItemType : StringRepresentable {
        UPGRADE,
        POTION,
        GENERIC_8x;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    override fun codec(): MapCodec<StackableItemBlock> {
        return CODEC
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val blockState = context.level.getBlockState(context.clickedPos)
        return if (blockState.`is`(this)) blockState.setValue(
            ITEMS,
            min(MAX_ITEMS, (blockState.getValue(ITEMS) + 1))
        ) else defaultBlockState().setValue(FACING, context.horizontalDirection.opposite).setValue(WATERLOGGED, context.level.getFluidState(context.clickedPos).type == Fluids.WATER)
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING)
        builder.add(ITEMS)
        builder.add(WATERLOGGED)
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return when (stackableItemType) {
            StackableItemType.UPGRADE -> return when (state.getValue(ITEMS)) {
                2 -> UPGRADE_DOUBLE
                3 -> UPGRADE_TRIPLE
                4 -> UPGRADE_QUARTET
                else -> UPGRADE_SINGLE
            }
            StackableItemType.POTION -> return when (state.getValue(ITEMS)) {
                2 -> when (state.getValue(FACING)) {
                    Direction.WEST, Direction.EAST -> ITEM_8x16
                    else -> ITEM_16x8
                }
                3 -> when (state.getValue(FACING)) {
                    Direction.SOUTH -> TRIANGLE_SOUTH
                    Direction.WEST -> TRIANGLE_WEST
                    Direction.EAST -> TRIANGLE_EAST
                    else -> TRIANGLE_NORTH
                }
                4 -> ITEM_16xS
                else -> ITEM_8x
            }

            else -> return when (state.getValue(ITEMS)) {
                1 -> ITEM_8x
                2 -> ITEM_8x
                3 -> ITEM_8x
                4 -> when (state.getValue(FACING)) {
                    Direction.SOUTH -> ITEM_8x
                    Direction.WEST -> ITEM_8x
                    Direction.EAST -> ITEM_8x
                    else -> ITEM_8x
                }
                else -> ITEM_8x
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

    public override fun canBeReplaced(state: BlockState, useContext: BlockPlaceContext): Boolean {
        return if (!useContext.isSecondaryUseActive && useContext.itemInHand.`is`(this.asItem()) && state.getValue(
                ITEMS
            ) <= MAX_ITEMS
                ) true else super.canBeReplaced(state, useContext)
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
        val CODEC: MapCodec<StackableItemBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec(),
                StackableItemType.CODEC.fieldOf("stackableItemType").forGetter(StackableItemBlock::stackableItemType)
        ).apply(it, ::StackableItemBlock) }

        val ITEMS = IntegerProperty.create("items", 1, 4)
        const val MAX_ITEMS: Int = 4

        //Non-Specific Hitbox Shapes
        val ITEM_8x = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75)
        val ITEM_16xS = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
        val ITEM_8x16 = Shapes.box(0.25, 0.0, 0.0, 0.75, 0.5, 1.0)
        val ITEM_16x8 = Shapes.box(0.0, 0.0, 0.25, 1.0, 0.5, 0.75)

        private val TRIANGLE_NORTH = Shapes.or(
            Shapes.box(0.0, 0.0, 0.5, 1.0, 0.5, 1.0),
            Shapes.box(0.25, 0.0, 0.0, 0.75, 0.5, 0.5)
        )
        private val TRIANGLE_EAST = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 0.5, 0.5, 1.0),
            Shapes.box(0.5, 0.0, 0.25, 1.0, 0.5, 0.75)
        )
        private val TRIANGLE_SOUTH = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 0.5),
            Shapes.box(0.25, 0.0, 0.5, 0.75, 0.5, 1.0)
        )
        private val TRIANGLE_WEST = Shapes.or(
            Shapes.box(0.5, 0.0, 0.0, 1.0, 0.5, 1.0),
            Shapes.box(0.0, 0.0, 0.25, 0.5, 0.5, 0.75)
        )

        //Specific Hitbox Shapes
        val UPGRADE_SINGLE = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.125, 0.6875)
        val UPGRADE_DOUBLE = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.25, 0.6875)
        val UPGRADE_TRIPLE = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.375, 0.6875)
        val UPGRADE_QUARTET = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.5, 0.6875)
    }
}