/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.min

class StackableItemBlock(settings: Properties, val type: StackableItemBlockType): HorizontalDirectionalBlock(settings), SimpleWaterloggedBlock {
    enum class StackableItemBlockType: StringRepresentable {
        BATTLE_ITEM,
        ETHER,
        MAX_POTION,
        POTION,
        STATUS_RESTORE,
        VITAMIN,
        METAL_COAT,
        IRON_BALL,
        THROAT_SPRAY,
        UPGRADE,
        DICE,
        GLASS_BOTTLE,
        EXTENDER;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    companion object {
        val MAX_AMOUNT: Int = 4
        val AMOUNT: IntegerProperty = IntegerProperty.create("amount", 1, MAX_AMOUNT)

        val CODEC: MapCodec<StackableItemBlock> = RecordCodecBuilder.mapCodec { it.group(
            propertiesCodec(),
            StackableItemBlockType.CODEC.fieldOf("type").forGetter(StackableItemBlock::type)
        ).apply(it, ::StackableItemBlock) }

        val northShapeMap: Map<StackableItemBlockType, List<VoxelShape>>
            get() = mapOf(
                StackableItemBlockType.BATTLE_ITEM to listOf(
                    Shapes.box(0.34375, 0.0, 0.34375, 0.65625, 0.4375, 0.65625),
                    Shapes.box(0.09375, 0.0, 0.34375, 0.90625, 0.4375, 0.71875),
                    Shapes.box(0.09375, 0.0, 0.09375, 0.90625, 0.4375, 0.90625),
                    Shapes.box(0.09375, 0.0, 0.09375, 0.96875, 0.4375, 0.90625)
                ),
                StackableItemBlockType.ETHER to listOf(
                    Shapes.box(0.375, 0.0, 0.3125, 0.625, 0.5, 0.6875),
                    Shapes.box(0.125, 0.0, 0.3125, 0.875, 0.5, 0.75),
                    Shapes.box(0.125, 0.0, 0.125, 0.875, 0.5, 1.0),
                    Shapes.box(0.125, 0.0, 0.0625, 0.9375, 0.5, 1.0)
                ),
                StackableItemBlockType.MAX_POTION to listOf(
                    Shapes.box(0.34375, 0.0, 0.3125, 0.65625, 0.5, 0.6875),
                    Shapes.box(0.09375, 0.0, 0.3125, 0.90625, 0.5, 0.75),
                    Shapes.box(0.09375, 0.0, 0.0625, 0.90625, 0.5, 1.0),
                    Shapes.box(0.09375, 0.0, 0.0625, 0.96875, 0.5, 1.0)
                ),
                StackableItemBlockType.POTION to listOf(
                    Shapes.box(0.375, 0.0, 0.3125, 0.625, 0.5625, 0.75),
                    Shapes.box(0.125, 0.0, 0.3125, 0.875, 0.5625, 0.8125),
                    Shapes.box(0.125, 0.0, 0.0625, 0.875, 0.5625, 1.0),
                    Shapes.box(0.125, 0.0, 0.0, 0.9375, 0.5625, 1.0)
                ),
                StackableItemBlockType.STATUS_RESTORE to listOf(
                    Shapes.box(0.375, 0.0, 0.3125, 0.625, 0.4375, 0.6875),
                    Shapes.box(0.125, 0.0, 0.3125, 0.875, 0.4375, 0.75),
                    Shapes.box(0.125, 0.0, 0.0625, 0.875, 0.4375, 0.9375),
                    Shapes.box(0.125, 0.0, 0.0, 0.9375, 0.4375, 0.9375)
                ),
                StackableItemBlockType.METAL_COAT to listOf(
                    Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.375, 0.6875),
                    Shapes.box(0.125, 0.0, 0.125, 0.875, 0.375, 0.875),
                    Shapes.box(0.125, 0.0, 0.0625, 0.9375, 0.375, 0.875),
                    Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.375, 0.9375)
                ),
                StackableItemBlockType.VITAMIN to listOf(
                    Shapes.box(0.375, 0.0, 0.375, 0.625, 0.4375, 0.625),
                    Shapes.box(0.1875, 0.0, 0.3125, 0.8125, 0.4375, 0.6875),
                    Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.4375, 0.8125),
                    Shapes.box(0.125, 0.0, 0.125, 0.875, 0.4375, 0.875)
                ),
                StackableItemBlockType.IRON_BALL to listOf(
                    Shapes.box(0.25, 0.0, 0.25, 0.75, 0.4375, 0.75),
                    Shapes.box(0.0, 0.0, 0.1875, 1.0, 0.4375, 0.8125),
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.4375, 1.0),
                    Shapes.or(
                        Shapes.box(0.0, 0.0, 0.0, 1.0, 0.4375, 1.0),
                        Shapes.box(0.25, 0.4375, 0.3125, 0.6875, 0.875, 0.75)
                    )
                ),
                StackableItemBlockType.THROAT_SPRAY to listOf(
                    Shapes.box(0.375, 0.0, 0.1875, 0.625, 0.5625, 0.75),
                    Shapes.box(0.125, 0.0, 0.1875, 0.875, 0.5625, 0.8125),
                    Shapes.box(0.125, 0.0, 0.0, 0.875, 0.5625, 1.0),
                    Shapes.box(0.075, 0.0, 0.0, 0.9375, 0.5625, 1.0)
                ),
                StackableItemBlockType.UPGRADE to listOf(
                    Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.125, 0.6875),
                    Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.25, 0.6875),
                    Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.375, 0.6875),
                    Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.5, 0.6875)
                ),
                StackableItemBlockType.DICE to listOf(
                    Shapes.box(0.125, 0.0, 0.6875, 0.3125, 0.1875, 0.875),
                    Shapes.box(0.125, 0.0, 0.625, 0.5625, 0.1875, 0.875),
                    Shapes.box(0.125, 0.0, 0.4375, 0.5625, 0.1875, 0.875),
                    Shapes.box(0.125, 0.0, 0.4375, 0.5625, 0.375, 0.875)
                ),
                StackableItemBlockType.GLASS_BOTTLE to listOf(
                    Shapes.box(0.375, 0.0, 0.375, 0.625, 0.375, 0.625),
                    Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.375, 0.6875),
                    Shapes.box(0.125, 0.0, 0.125, 0.8125, 0.375, 0.875),
                    Shapes.box(0.125, 0.0, 0.125, 0.875, 0.375, 0.875)
                ),
                StackableItemBlockType.EXTENDER to listOf(
                    Shapes.box(0.25, 0.0, 0.375, 0.625, 0.5625, 0.625),
                    Shapes.box(0.125, 0.0, 0.25, 0.875, 0.5625, 0.75),
                    Shapes.box(0.0625, 0.0, 0.0625, 0.875, 0.5625, 0.875),
                    Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.5625, 0.9375)
                )
            )

        fun getShape(type: StackableItemBlockType, count: Int): VoxelShape = northShapeMap[type]?.let { list -> list[(count - 1).coerceIn(0, list.lastIndex)] } ?: Shapes.block()
        fun getMaxAmount(type: StackableItemBlockType): Int = min(northShapeMap[type]?.size ?: 1 , MAX_AMOUNT)
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(AMOUNT, 1)
            .setValue(WATERLOGGED, false))
    }

    override fun codec(): MapCodec<StackableItemBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING, AMOUNT, WATERLOGGED)
    }

    override fun getFluidState(blockState: BlockState): FluidState? = if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState)

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, world: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }
        return if (state.getValue(HORIZONTAL_FACING).opposite == direction && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val direction = state.getValue(HORIZONTAL_FACING)
        val count = state.getValue(AMOUNT)
        return rotateShape(Direction.NORTH, direction, getShape(type, count))
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getCollisionShape(state, level, pos, context)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val blockState = context.level.getBlockState(context.clickedPos)
        val fluidState = context.level.getFluidState(context.clickedPos)
        return if (blockState.`is`(this)) blockState.setValue(AMOUNT, min(getMaxAmount(type), (blockState.getValue(AMOUNT) + 1)))
            else super.getStateForPlacement(context)?.setValue(HORIZONTAL_FACING, context.horizontalDirection.opposite)?.setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
    }

    override fun canBeReplaced(state: BlockState, useContext: BlockPlaceContext): Boolean {
        return if (
            !useContext.isSecondaryUseActive
            && useContext.itemInHand.`is`(this.asItem())
            && state.getValue(AMOUNT) <= getMaxAmount(type)
        ) true else super.canBeReplaced(state, useContext)
    }
}
