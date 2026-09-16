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
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
//TODO: All the Fan appliance stuff
//TODO: Able to place the Fan on Walls, models and hitbox are there (blockstate would need edits to support it)
open class CopperFanBlock(settings: Properties) : HorizontalDirectionalBlock(settings), SimpleWaterloggedBlock {

    companion object {
        val CODEC: MapCodec<CopperFanBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::CopperFanBlock) }
        //val FACE = //TODO: Floor/Wall
        val POWER = BlockStateProperties.POWER;
        val PUSH = BooleanProperty.create("push")

        val FLOOR_HITBOX = Shapes.or(
            Shapes.box(0.0625, 0.125, 0.4375, 0.9375, 1.0, 0.5625),
            Shapes.box(0.375, 0.0, 0.375, 0.625, 0.125, 0.625))
        val WALL_HITBOX = Shapes.box(0.0625, 0.0625, 0.875, 0.9375, 0.9375, 1.0)
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false)
            //.setValue(FACE, AttachFace.FLOOR)
            .setValue(POWER, 0)
            .setValue(PUSH, true) //If True, fan pushes entities, If False, fan is cosmetic only
        )
    }

    override fun codec(): MapCodec<CopperFanBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING, WATERLOGGED, /*FACE,*/ POWER, PUSH)
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
        return rotateShape(Direction.NORTH, direction, FLOOR_HITBOX)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getCollisionShape(state, level, pos, context)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return super.getStateForPlacement(context)?.setValue(HORIZONTAL_FACING, context.horizontalDirection.opposite)?.setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
    }
}