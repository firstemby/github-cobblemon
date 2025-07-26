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
        METAL_COAT,
        POTION,
        MAX_POTION,
        RESTORE,
        ETHER,
        BATTLE_X,
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
            StackableItemType.UPGRADE -> {
                when (state.getValue(ITEMS)) {
                    2 -> UPGRADE_DOUBLE
                    3 -> UPGRADE_TRIPLE
                    4 -> UPGRADE_QUARTET
                    else -> UPGRADE_SINGLE
                }
            }

            StackableItemType.METAL_COAT -> {
                when (state.getValue(ITEMS)) {
                    2 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> METAL_DOUBLE_EW
                        else -> METAL_DOUBLE_NS
                    }
                    3 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> METAL_TRIPLE_S
                        Direction.WEST -> METAL_TRIPLE_W
                        Direction.EAST -> METAL_TRIPLE_E
                        else -> METAL_TRIPLE_N
                    }
                    4 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> METAL_QUARTET_EW
                        else -> METAL_QUARTET_NS
                    }
                    else -> METAL_SINGLE
                }
            }

            StackableItemType.POTION -> {
                when (state.getValue(ITEMS)) {
                    2 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> POTION_DOUBLE_EW
                        else -> POTION_DOUBLE_NS
                    }
                    3 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> POTION_TRIPLE_SOUTH
                        Direction.WEST -> POTION_TRIPLE_WEST
                        Direction.EAST -> POTION_TRIPLE_EAST
                        else -> POTION_TRIPLE_NORTH
                    }
                    4 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> POTION_QUARTET_SOUTH
                        Direction.WEST -> POTION_QUARTET_WEST
                        Direction.EAST -> POTION_QUARTET_EAST
                        else -> POTION_QUARTET_NORTH
                    }
                    else -> POTION_SINGLE
                }
            }

            StackableItemType.MAX_POTION -> {
                when (state.getValue(ITEMS)) {
                    2 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> MAX_POTION_DOUBLE_SOUTH
                        Direction.WEST -> MAX_POTION_DOUBLE_WEST
                        Direction.EAST -> MAX_POTION_DOUBLE_EAST
                        else -> MAX_POTION_DOUBLE_NORTH
                    }
                    3 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> MAX_POTION_TRIPLE_SOUTH
                        Direction.WEST -> MAX_POTION_TRIPLE_WEST
                        Direction.EAST -> MAX_POTION_TRIPLE_EAST
                        else -> MAX_POTION_TRIPLE_NORTH
                    }
                    4 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> MAX_POTION_QUARTET_SOUTH
                        Direction.WEST -> MAX_POTION_QUARTET_WEST
                        Direction.EAST -> MAX_POTION_QUARTET_EAST
                        else -> MAX_POTION_QUARTET_NORTH
                    }
                    else ->  when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> MAX_POTION_SINGLE_EW
                        else -> MAX_POTION_SINGLE_NS
                    }
                }
            }

            StackableItemType.RESTORE -> {
                when (state.getValue(ITEMS)) {
                    2 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> RESTORE_DOUBLE_EW
                        else -> RESTORE_DOUBLE_NS
                    }
                    3 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> RESTORE_TRIPLE_SOUTH
                        Direction.WEST -> RESTORE_TRIPLE_WEST
                        Direction.EAST -> RESTORE_TRIPLE_EAST
                        else -> RESTORE_TRIPLE_NORTH
                    }
                    4 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> RESTORE_QUARTET_SOUTH
                        Direction.WEST -> RESTORE_QUARTET_WEST
                        Direction.EAST -> RESTORE_QUARTET_EAST
                        else -> RESTORE_QUARTET_NORTH
                    }
                    else -> RESTORE_SINGLE
                }
            }

            StackableItemType.ETHER -> {
                when (state.getValue(ITEMS)) {
                    1 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> ETHER_SINGLE_EW
                        else -> ETHER_SINGLE_NS
                    }
                    2 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> ETHER_DOUBLE_SOUTH
                        Direction.WEST -> ETHER_DOUBLE_WEST
                        Direction.EAST -> ETHER_DOUBLE_EAST
                        else -> ETHER_DOUBLE_NORTH
                    }
                    3 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> ETHER_TRIPLE_SOUTH
                        Direction.WEST -> ETHER_TRIPLE_WEST
                        Direction.EAST -> ETHER_TRIPLE_EAST
                        else -> ETHER_TRIPLE_NORTH
                    }
                    4 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> ETHER_QUARTET_SOUTH
                        Direction.WEST -> ETHER_QUARTET_WEST
                        Direction.EAST -> ETHER_QUARTET_EAST
                        else -> ETHER_QUARTET_NORTH
                    }
                    else -> ETHER_SINGLE_NS
                }
            }

            StackableItemType.BATTLE_X -> {
                when (state.getValue(ITEMS)) {
                    2 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> BATTLE_DOUBLE_EW
                        else -> BATTLE_DOUBLE_NS
                    }
                    3 -> when (state.getValue(FACING)) {
                        Direction.SOUTH -> BATTLE_TRIPLE_S
                        Direction.WEST -> BATTLE_TRIPLE_W
                        Direction.EAST -> BATTLE_TRIPLE_E
                        else -> BATTLE_TRIPLE_N
                    }
                    4 -> when (state.getValue(FACING)) {
                        Direction.WEST, Direction.EAST -> BATTLE_QUARTET_EW
                        else -> BATTLE_QUARTET_NS
                    }
                    else -> BATTLE_SINGLE
                }
            }

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

        val METAL_SINGLE = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.375, 0.6875)
        val METAL_DOUBLE_NS = Shapes.or(
            Shapes.box(0.5, 0.0, 0.125, 0.875, 0.375, 0.5),
            Shapes.box(0.125, 0.0, 0.5, 0.5, 0.375, 0.875)
        )
        val METAL_DOUBLE_EW = Shapes.or(
            Shapes.box(0.5, 0.0, 0.5, 0.875, 0.375, 0.875),
            Shapes.box(0.125, 0.0, 0.125, 0.5, 0.375, 0.5)
        )
        val METAL_TRIPLE_N = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.0625, 0.6875, 0.375, 0.4375),
            Shapes.box(0.5625, 0.0, 0.5, 0.9375, 0.375, 0.875),
            Shapes.box(0.125, 0.0, 0.5, 0.5, 0.375, 0.875)
        )
        val METAL_TRIPLE_E = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.3125, 0.9375, 0.375, 0.6875),
            Shapes.box(0.125, 0.0, 0.5625, 0.5, 0.375, 0.9375),
            Shapes.box(0.125, 0.0, 0.125, 0.5, 0.375, 0.5)
        )
        val METAL_TRIPLE_S = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.5625, 0.6875, 0.375, 0.9375),
            Shapes.box(0.0625, 0.0, 0.125, 0.4375, 0.375, 0.5),
            Shapes.box(0.5, 0.0, 0.125, 0.875, 0.375, 0.5)
        )
        val METAL_TRIPLE_W = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.3125, 0.4375, 0.375, 0.6875),
            Shapes.box(0.5, 0.0, 0.0625, 0.875, 0.375, 0.4375),
            Shapes.box(0.5, 0.0, 0.5, 0.875, 0.375, 0.875)
        )
        val METAL_QUARTET_NS = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.0625, 0.9375, 0.375, 0.4375),
            Shapes.box(0.125, 0.0, 0.125, 0.5, 0.375, 0.5),
            Shapes.box(0.5, 0.0, 0.5, 0.875, 0.375, 0.875),
            Shapes.box(0.0625, 0.0, 0.5625, 0.4375, 0.375, 0.9375)
        )
        val METAL_QUARTET_EW = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.5625, 0.9375, 0.375, 0.9375),
            Shapes.box(0.5, 0.0, 0.125, 0.875, 0.375, 0.5),
            Shapes.box(0.125, 0.0, 0.5, 0.5, 0.375, 0.875),
            Shapes.box(0.0625, 0.0, 0.0625, 0.4375, 0.375, 0.4375)
        )

        val POTION_SINGLE = Shapes.box(0.34375, 0.0, 0.34375, 0.65625, 0.625, 0.65625)
        val POTION_DOUBLE_EW = Shapes.or(
            Shapes.box(0.4375, 0.0, 0.5625, 0.75, 0.625, 0.875),
            Shapes.box(0.25, 0.0, 0.125, 0.5625, 0.625, 0.4375)
        )
        val POTION_DOUBLE_NS = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.25, 0.875, 0.625, 0.5625),
            Shapes.box(0.125, 0.0, 0.4375, 0.4375, 0.625, 0.75)
        )
        val POTION_TRIPLE_EAST = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.375, 0.875, 0.625, 0.6875),
            Shapes.box(0.1875, 0.0, 0.125, 0.5, 0.625, 0.4375),
            Shapes.box(0.0625, 0.0, 0.5625, 0.375, 0.625, 0.875)
        )
        val POTION_TRIPLE_WEST = Shapes.or(
            Shapes.box(0.125, 0.0, 0.3125, 0.4375, 0.625, 0.625),
            Shapes.box(0.5, 0.0, 0.5625, 0.8125, 0.625, 0.875),
            Shapes.box(0.625, 0.0, 0.125, 0.9375, 0.625, 0.4375)
        )
        val POTION_TRIPLE_NORTH = Shapes.or(
            Shapes.box(0.375, 0.0, 0.125, 0.6875, 0.625, 0.4375),
            Shapes.box(0.125, 0.0, 0.5, 0.4375, 0.625, 0.8125),
            Shapes.box(0.5625, 0.0, 0.625, 0.875, 0.625, 0.9375)
        )
        val POTION_TRIPLE_SOUTH = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.5625, 0.625, 0.625, 0.875),
            Shapes.box(0.5625, 0.0, 0.1875, 0.875, 0.625, 0.5),
            Shapes.box(0.125, 0.0, 0.0625, 0.4375, 0.625, 0.375)
        )
        val POTION_QUARTET_EAST = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.5, 0.875, 0.625, 0.8125),
            Shapes.box(0.5, 0.0, 0.125, 0.8125, 0.625, 0.4375),
            Shapes.box(0.0625, 0.0, 0.0625, 0.375, 0.625, 0.375),
            Shapes.box(0.125, 0.0, 0.5625, 0.4375, 0.625, 0.875)
        )
        val POTION_QUARTET_WEST = Shapes.or(
            Shapes.box(0.125, 0.0, 0.1875, 0.4375, 0.625, 0.5),
            Shapes.box(0.1875, 0.0, 0.5625, 0.5, 0.625, 0.875),
            Shapes.box(0.625, 0.0, 0.625, 0.9375, 0.625, 0.9375),
            Shapes.box(0.5625, 0.0, 0.125, 0.875, 0.625, 0.4375)
        )
        val POTION_QUARTET_NORTH = Shapes.or(
            Shapes.box(0.5, 0.0, 0.125, 0.8125, 0.625, 0.4375),
            Shapes.box(0.125, 0.0, 0.1875, 0.4375, 0.625, 0.5),
            Shapes.box(0.0625, 0.0, 0.625, 0.375, 0.625, 0.9375),
            Shapes.box(0.5625, 0.0, 0.5625, 0.875, 0.625, 0.875)
        )
        val POTION_QUARTET_SOUTH = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.5625, 0.5, 0.625, 0.875),
            Shapes.box(0.5625, 0.0, 0.5, 0.875, 0.625, 0.8125),
            Shapes.box(0.625, 0.0, 0.0625, 0.9375, 0.625, 0.375),
            Shapes.box(0.125, 0.0, 0.125, 0.4375, 0.625, 0.4375)
        )

        val MAX_POTION_SINGLE_NS = Shapes.box(0.34375, 0.0, 0.3125, 0.65625, 0.5, 0.6875)
        val MAX_POTION_SINGLE_EW = Shapes.box(0.3125, 0.0, 0.34375, 0.6875, 0.5, 0.65625)
        val MAX_POTION_DOUBLE_EAST = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.5, 0.6875, 0.5, 0.8125),
            Shapes.box(0.25, 0.0, 0.125, 0.625, 0.5, 0.4375)
        )
        val MAX_POTION_DOUBLE_WEST = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.1875, 0.6875, 0.5, 0.5),
            Shapes.box(0.375, 0.0, 0.5625, 0.75, 0.5, 0.875)
        )
        val MAX_POTION_DOUBLE_NORTH = Shapes.or(
            Shapes.box(0.5, 0.0, 0.3125, 0.8125, 0.5, 0.6875),
            Shapes.box(0.125, 0.0, 0.375, 0.4375, 0.5, 0.75)
        )
        val MAX_POTION_DOUBLE_SOUTH = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.3125, 0.5, 0.5, 0.6875),
            Shapes.box(0.5625, 0.0, 0.25, 0.875, 0.5, 0.625)
        )
        val MAX_POTION_TRIPLE_EAST = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.625, 0.5625, 0.5, 0.9375),
            Shapes.box(0.125, 0.0, 0.125, 0.5, 0.5, 0.4375),
            Shapes.box(0.5625, 0.0, 0.3125, 0.9375, 0.5, 0.625)
        )
        val MAX_POTION_TRIPLE_WEST = Shapes.or(
            Shapes.box(0.4375, 0.0, 0.0625, 0.8125, 0.5, 0.375),
            Shapes.box(0.5, 0.0, 0.5625, 0.875, 0.5, 0.875),
            Shapes.box(0.0625, 0.0, 0.375, 0.4375, 0.5, 0.6875)
        )
        val MAX_POTION_TRIPLE_NORTH = Shapes.or(
            Shapes.box(0.625, 0.0, 0.4375, 0.9375, 0.5, 0.8125),
            Shapes.box(0.125, 0.0, 0.5, 0.4375, 0.5, 0.875),
            Shapes.box(0.3125, 0.0, 0.0625, 0.625, 0.5, 0.4375)
        )
        val MAX_POTION_TRIPLE_SOUTH = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.1875, 0.375, 0.5, 0.5625),
            Shapes.box(0.5625, 0.0, 0.125, 0.875, 0.5, 0.5),
            Shapes.box(0.375, 0.0, 0.5625, 0.6875, 0.5, 0.9375)
        )
        val MAX_POTION_QUARTET_EAST = Shapes.or(
            Shapes.box(0.125, 0.0, 0.625, 0.5, 0.5, 0.9375),
            Shapes.box(0.0625, 0.0, 0.125, 0.4375, 0.5, 0.4375),
            Shapes.box(0.5625, 0.0, 0.5625, 0.9375, 0.5, 0.875),
            Shapes.box(0.5, 0.0, 0.1875, 0.875, 0.5, 0.5)
        )
        val MAX_POTION_QUARTET_WEST = Shapes.or(
            Shapes.box(0.5, 0.0, 0.0625, 0.875, 0.5, 0.375),
            Shapes.box(0.5625, 0.0, 0.5625, 0.9375, 0.5, 0.875),
            Shapes.box(0.0625, 0.0, 0.125, 0.4375, 0.5, 0.4375),
            Shapes.box(0.125, 0.0, 0.5, 0.5, 0.5, 0.8125)
        )
        val MAX_POTION_QUARTET_NORTH = Shapes.or(
            Shapes.box(0.625, 0.0, 0.5, 0.9375, 0.5, 0.875),
            Shapes.box(0.125, 0.0, 0.5625, 0.4375, 0.5, 0.9375),
            Shapes.box(0.5625, 0.0, 0.0625, 0.875, 0.5, 0.4375),
            Shapes.box(0.1875, 0.0, 0.125, 0.5, 0.5, 0.5)
        )
        val MAX_POTION_QUARTET_SOUTH = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.125, 0.375, 0.5, 0.5),
            Shapes.box(0.5625, 0.0, 0.0625, 0.875, 0.5, 0.4375),
            Shapes.box(0.125, 0.0, 0.5625, 0.4375, 0.5, 0.9375),
            Shapes.box(0.5, 0.0, 0.5, 0.8125, 0.5, 0.875)
        )

        val RESTORE_SINGLE = Shapes.box(0.375, 0.0, 0.375, 0.625, 0.5, 0.625)
        val RESTORE_DOUBLE_EW = Shapes.or(
            Shapes.box(0.375, 0.0, 0.1875, 0.625, 0.5, 0.4375),
            Shapes.box(0.375, 0.0, 0.5625, 0.625, 0.5, 0.8125)
        )
        val RESTORE_DOUBLE_NS = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.375, 0.4375, 0.5, 0.625),
            Shapes.box(0.5625, 0.0, 0.375, 0.8125, 0.5, 0.625)
        )
        val RESTORE_TRIPLE_EAST = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.1875, 0.4375, 0.5, 0.4375),
            Shapes.box(0.25, 0.0, 0.5625, 0.5, 0.5, 0.8125),
            Shapes.box(0.625, 0.0, 0.375, 0.875, 0.5, 0.625)
        )
        val RESTORE_TRIPLE_WEST = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.5625, 0.8125, 0.5, 0.8125),
            Shapes.box(0.5, 0.0, 0.1875, 0.75, 0.5, 0.4375),
            Shapes.box(0.125, 0.0, 0.375, 0.375, 0.5, 0.625)
        )
        val RESTORE_TRIPLE_NORTH = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.5625, 0.4375, 0.5, 0.8125),
            Shapes.box(0.5625, 0.0, 0.5, 0.8125, 0.5, 0.75),
            Shapes.box(0.375, 0.0, 0.125, 0.625, 0.5, 0.375)
        )
        val RESTORE_TRIPLE_SOUTH = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.1875, 0.8125, 0.5, 0.4375),
            Shapes.box(0.1875, 0.0, 0.25, 0.4375, 0.5, 0.5),
            Shapes.box(0.375, 0.0, 0.625, 0.625, 0.5, 0.875)
        )
        val RESTORE_QUARTET_EAST = Shapes.or(
            Shapes.box(0.125, 0.0, 0.1875, 0.375, 0.5, 0.4375),
            Shapes.box(0.1875, 0.0, 0.625, 0.4375, 0.5, 0.875),
            Shapes.box(0.625, 0.0, 0.5, 0.875, 0.5, 0.75),
            Shapes.box(0.625, 0.0, 0.125, 0.875, 0.5, 0.375)
        )
        val RESTORE_QUARTET_WEST = Shapes.or(
            Shapes.box(0.625, 0.0, 0.5625, 0.875, 0.5, 0.8125),
            Shapes.box(0.5625, 0.0, 0.125, 0.8125, 0.5, 0.375),
            Shapes.box(0.125, 0.0, 0.25, 0.375, 0.5, 0.5),
            Shapes.box(0.125, 0.0, 0.625, 0.375, 0.5, 0.875)
        )
        val RESTORE_QUARTET_NORTH = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.625, 0.4375, 0.5, 0.875),
            Shapes.box(0.625, 0.0, 0.5625, 0.875, 0.5, 0.8125),
            Shapes.box(0.5, 0.0, 0.125, 0.75, 0.5, 0.375),
            Shapes.box(0.125, 0.0, 0.125, 0.375, 0.5, 0.375)
        )
        val RESTORE_QUARTET_SOUTH = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.125, 0.8125, 0.5, 0.375),
            Shapes.box(0.125, 0.0, 0.1875, 0.375, 0.5, 0.4375),
            Shapes.box(0.25, 0.0, 0.625, 0.5, 0.5, 0.875),
            Shapes.box(0.625, 0.0, 0.625, 0.875, 0.5, 0.875)
        )

        val ETHER_SINGLE_EW = Shapes.box(0.34375, 0.0, 0.40625, 0.65625, 0.4375, 0.59375)
        val ETHER_SINGLE_NS = Shapes.box(0.40625, 0.0, 0.34375, 0.59375, 0.4375, 0.65625)
        val ETHER_DOUBLE_EAST = Shapes.or(
            Shapes.box(0.375, 0.0, 0.5, 0.6875, 0.4375, 0.6875),
            Shapes.box(0.25, 0.0, 0.25, 0.5625, 0.4375, 0.4375)
        )
        val ETHER_DOUBLE_WEST = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.3125, 0.625, 0.4375, 0.5),
            Shapes.box(0.4375, 0.0, 0.5625, 0.75, 0.4375, 0.75)
        )
        val ETHER_DOUBLE_NORTH = Shapes.or(
            Shapes.box(0.5, 0.0, 0.3125, 0.6875, 0.4375, 0.625),
            Shapes.box(0.25, 0.0, 0.4375, 0.4375, 0.4375, 0.75)
        )
        val ETHER_DOUBLE_SOUTH = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.375, 0.5, 0.4375, 0.6875),
            Shapes.box(0.5625, 0.0, 0.25, 0.75, 0.4375, 0.5625)
        )
        val ETHER_TRIPLE_EAST = Shapes.or(
            Shapes.box(0.25, 0.0, 0.625, 0.5625, 0.4375, 0.8125),
            Shapes.box(0.125, 0.0, 0.1875, 0.4375, 0.4375, 0.375),
            Shapes.box(0.5, 0.0, 0.375, 0.8125, 0.4375, 0.5625)
        )
        val ETHER_TRIPLE_WEST = Shapes.or(
            Shapes.box(0.4375, 0.0, 0.1875, 0.75, 0.4375, 0.375),
            Shapes.box(0.5625, 0.0, 0.625, 0.875, 0.4375, 0.8125),
            Shapes.box(0.1875, 0.0, 0.4375, 0.5, 0.4375, 0.625)
        )
        val ETHER_TRIPLE_NORTH = Shapes.or(
            Shapes.box(0.625, 0.0, 0.4375, 0.8125, 0.4375, 0.75),
            Shapes.box(0.1875, 0.0, 0.5625, 0.375, 0.4375, 0.875),
            Shapes.box(0.375, 0.0, 0.1875, 0.5625, 0.4375, 0.5)
        )
        val ETHER_TRIPLE_SOUTH = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.25, 0.375, 0.4375, 0.5625),
            Shapes.box(0.625, 0.0, 0.125, 0.8125, 0.4375, 0.4375),
            Shapes.box(0.4375, 0.0, 0.5, 0.625, 0.4375, 0.8125)
        )
        val ETHER_QUARTET_EAST = Shapes.or(
            Shapes.box(0.125, 0.0, 0.5625, 0.4375, 0.4375, 0.75),
            Shapes.box(0.0625, 0.0, 0.1875, 0.375, 0.4375, 0.375),
            Shapes.box(0.5625, 0.0, 0.625, 0.875, 0.4375, 0.8125),
            Shapes.box(0.5, 0.0, 0.125, 0.8125, 0.4375, 0.3125)
        )
        val ETHER_QUARTET_WEST = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.25, 0.875, 0.4375, 0.4375),
            Shapes.box(0.625, 0.0, 0.625, 0.9375, 0.4375, 0.8125),
            Shapes.box(0.125, 0.0, 0.1875, 0.4375, 0.4375, 0.375),
            Shapes.box(0.1875, 0.0, 0.6875, 0.5, 0.4375, 0.875)
        )
        val ETHER_QUARTET_NORTH = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.5625, 0.75, 0.4375, 0.875),
            Shapes.box(0.1875, 0.0, 0.625, 0.375, 0.4375, 0.9375),
            Shapes.box(0.625, 0.0, 0.125, 0.8125, 0.4375, 0.4375),
            Shapes.box(0.125, 0.0, 0.1875, 0.3125, 0.4375, 0.5)
        )
        val ETHER_QUARTET_SOUTH = Shapes.or(
            Shapes.box(0.25, 0.0, 0.125, 0.4375, 0.4375, 0.4375),
            Shapes.box(0.625, 0.0, 0.0625, 0.8125, 0.4375, 0.375),
            Shapes.box(0.1875, 0.0, 0.5625, 0.375, 0.4375, 0.875),
            Shapes.box(0.6875, 0.0, 0.5, 0.875, 0.4375, 0.8125)
        )

        val BATTLE_SINGLE = Shapes.box(0.375, 0.0, 0.375, 0.625, 0.4375, 0.625)
        val BATTLE_DOUBLE_NS = Shapes.or(
            Shapes.box(0.5625, 0.0, 0.4375, 0.8125, 0.4375, 0.6875),
            Shapes.box(0.1875, 0.0, 0.3125, 0.4375, 0.4375, 0.5625)
        )
        val BATTLE_DOUBLE_EW = Shapes.or(
            Shapes.box(0.3125, 0.0, 0.5625, 0.5625, 0.4375, 0.8125),
            Shapes.box(0.4375, 0.0, 0.1875, 0.6875, 0.4375, 0.4375)
        )
        val BATTLE_TRIPLE_N = Shapes.or(
            Shapes.box(0.625, 0.0, 0.625, 0.875, 0.4375, 0.875),
            Shapes.box(0.125, 0.0, 0.5, 0.375, 0.4375, 0.75),
            Shapes.box(0.4375, 0.0, 0.125, 0.6875, 0.4375, 0.375)
        )
        val BATTLE_TRIPLE_E = Shapes.or(
            Shapes.box(0.125, 0.0, 0.625, 0.375, 0.4375, 0.875),
            Shapes.box(0.25, 0.0, 0.125, 0.5, 0.4375, 0.375),
            Shapes.box(0.625, 0.0, 0.4375, 0.875, 0.4375, 0.6875)
        )
        val BATTLE_TRIPLE_S = Shapes.or(
            Shapes.box(0.125, 0.0, 0.125, 0.375, 0.4375, 0.375),
            Shapes.box(0.625, 0.0, 0.25, 0.875, 0.4375, 0.5),
            Shapes.box(0.3125, 0.0, 0.625, 0.5625, 0.4375, 0.875)
        )
        val BATTLE_TRIPLE_W = Shapes.or(
            Shapes.box(0.625, 0.0, 0.125, 0.875, 0.4375, 0.375),
            Shapes.box(0.5, 0.0, 0.625, 0.75, 0.4375, 0.875),
            Shapes.box(0.125, 0.0, 0.3125, 0.375, 0.4375, 0.5625)
        )
        val BATTLE_QUARTET_NS = Shapes.or(
            Shapes.box(0.625, 0.0, 0.625, 0.875, 0.4375, 0.875),
            Shapes.box(0.1875, 0.0, 0.5625, 0.4375, 0.4375, 0.8125),
            Shapes.box(0.5625, 0.0, 0.1875, 0.8125, 0.4375, 0.4375),
            Shapes.box(0.125, 0.0, 0.125, 0.375, 0.4375, 0.375)
        )
        val BATTLE_QUARTET_EW = Shapes.or(
            Shapes.box(0.125, 0.0, 0.625, 0.375, 0.4375, 0.875),
            Shapes.box(0.1875, 0.0, 0.1875, 0.4375, 0.4375, 0.4375),
            Shapes.box(0.5625, 0.0, 0.5625, 0.8125, 0.4375, 0.8125),
            Shapes.box(0.625, 0.0, 0.125, 0.875, 0.4375, 0.375)
        )
    }
}