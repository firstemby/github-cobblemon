/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.general

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.min

class MintLitterBlock(properties: Properties?) : Block(properties) {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(AMOUNT, 1))
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val blockState = context.getLevel().getBlockState(context.getClickedPos())
        return if (blockState.`is`(this)) blockState.setValue<Int?, Int?>(
            AMOUNT,
            min(4, blockState.getValue<Int?>(AMOUNT) as Int + 1)
        ) as BlockState else this.defaultBlockState().setValue<Direction?, Direction?>(
            FACING,
            context.getHorizontalDirection().getOpposite()
        ) as BlockState
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        builder.add(AMOUNT)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction?,
        neighborState: BlockState?,
        level: LevelAccessor?,
        pos: BlockPos?,
        neighborPos: BlockPos?
    ): BlockState {
        return if (!state.canSurvive(level, pos)) Blocks.AIR.defaultBlockState() else super.updateShape(
            state,
            direction,
            neighborState,
            level,
            pos,
            neighborPos
        )
    }

    override fun canSurvive(state: BlockState?, level: LevelReader, pos: BlockPos): Boolean {
        val posBelow = pos.below()
        return level.getBlockState(posBelow).isFaceSturdy(level, posBelow, Direction.UP)
    }

    override fun propagatesSkylightDown(state: BlockState, level: BlockGetter?, pos: BlockPos?): Boolean {
        return state.getFluidState().isEmpty()
    }

    public override fun canBeReplaced(state: BlockState, useContext: BlockPlaceContext): Boolean {
        return if (!useContext.isSecondaryUseActive && useContext.itemInHand.`is`(this.asItem()) && state.getValue(
                AMOUNT
            ) <= MAX_AMOUNT
        ) true else super.canBeReplaced(state, useContext)
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)))
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape? {
        val litterBoxes = when (state.getValue(FACING)) {
            Direction.SOUTH -> SOUTH_SHAPES
            Direction.WEST -> WEST_SHAPES
            Direction.EAST -> EAST_SHAPES
            else -> NORTH_SHAPES
        }

        var litterBox = litterBoxes[state.getValue(AMOUNT)]
        return litterBox
    }

    companion object {
        val CODEC: MapCodec<MintLitterBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec()
            ).apply(it, ::MintLitterBlock) }

        val AMOUNT = IntegerProperty.create("amount", 1, 4)
        const val MAX_AMOUNT: Int = 4

        private val NORTH_SHAPES = listOf(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0),
            Shapes.box(0.0, 0.0, 0.0, 0.5, 0.0625, 0.5),
            Shapes.box(0.0, 0.0, 0.0, 0.5, 0.0625, 1.0),
            Shapes.or(
                Shapes.box(0.0, 0.0, 0.0, 0.5, 0.0625, 1.0),
                Shapes.box(0.5, 0.0, 0.5, 1.0, 0.0625, 1.0)
            ),
            Shapes.box(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)
        )

        private val SOUTH_SHAPES = createShapes(Direction.SOUTH)
        private val WEST_SHAPES = createShapes(Direction.WEST)
        private val EAST_SHAPES = createShapes(Direction.EAST)

        private fun createShapes(to: Direction): List<VoxelShape> {
            return NORTH_SHAPES.map { rotateShape(Direction.NORTH, to, it) }
        }
    }
}