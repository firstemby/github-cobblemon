/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.appliances

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
//TODO: All the Mower appliance stuff
open class CopperMowerBlock(settings: Properties) : HorizontalDirectionalBlock(settings), SimpleWaterloggedBlock {

    companion object {
        val CODEC: MapCodec<CopperMowerBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::CopperMowerBlock) }
        val TRIGGERED = BooleanProperty.create("triggered")

        val COLLISIONBOX = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.0, 0.8125, 0.375, 0.625),
            Shapes.box(0.0625, 0.0, 0.625, 0.9375, 0.625, 1.0),
            Shapes.box(0.125, 0.0, 0.1875, 0.875, 0.3125, 0.5),
            //The next three lines are just so the handle does have collision, it feels awkward without it but it looks bad as hitbox
            Shapes.box(0.375, 0.375, 0.375, 0.625, 0.625, 0.5625),
            Shapes.box(0.4375, 0.5625, 0.5, 0.5625, 0.875, 0.625),
            Shapes.box(0.25, 0.875, 0.5625, 0.75, 1.0, 0.6875)
        )
        val HITBOX = Shapes.or(
            Shapes.box(0.1875, 0.0, 0.0, 0.8125, 0.375, 0.625),
            Shapes.box(0.0625, 0.0, 0.625, 0.9375, 0.625, 1.0),
            Shapes.box(0.125, 0.0, 0.1875, 0.875, 0.3125, 0.5)
        )
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
            .setValue(TRIGGERED, false)
        )
    }

    override fun codec(): MapCodec<CopperMowerBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING, WATERLOGGED, TRIGGERED)
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
        return rotateShape(Direction.NORTH, direction, COLLISIONBOX)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val direction = state.getValue(HORIZONTAL_FACING)
        return rotateShape(Direction.NORTH, direction, HITBOX)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return super.getStateForPlacement(context)?.setValue(HORIZONTAL_FACING, context.horizontalDirection)?.setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
    }
}