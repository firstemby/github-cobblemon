/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class ActivatableDecorationBlock(settings: Properties, val type: ActivatableDecorationBlockType) : HorizontalDirectionalBlock(settings), SimpleWaterloggedBlock {
    enum class ActivatableDecorationBlockType: StringRepresentable {
        //For basic use, also ignore camelcase errors, it's just dumb
        CENTERED_8x,
        //For specific items
        METRONOME;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    companion object {
        val CODEC: MapCodec<ActivatableDecorationBlock> = RecordCodecBuilder.mapCodec { it.group(
            propertiesCodec(),
            ActivatableDecorationBlockType.CODEC.fieldOf("type").forGetter(ActivatableDecorationBlock::type)
        ).apply(it, ::ActivatableDecorationBlock) }
        val ACTIVE = BooleanProperty.create("active")

        val CENTERED_8x = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75)
        val METRONOME_HITBOX = Shapes.box(0.25, 0.0, 0.375, 0.75, 0.8125, 0.75)

        fun getShape(type: ActivatableDecorationBlockType): VoxelShape {
            return when (type) {
                ActivatableDecorationBlockType.METRONOME -> METRONOME_HITBOX
                else -> CENTERED_8x
            }
        }
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(ACTIVE, false)
            .setValue(WATERLOGGED, false)
        )
    }

    override fun codec(): MapCodec<ActivatableDecorationBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING, ACTIVE, WATERLOGGED)
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
        return rotateShape(Direction.NORTH, direction, getShape(type))
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getCollisionShape(state, level, pos, context).singleEncompassing()
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return super.getStateForPlacement(context)?.setValue(HORIZONTAL_FACING, context.horizontalDirection.opposite)?.setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) {
            level.setBlockAndUpdate(pos, state.cycle(ACTIVE))
            return InteractionResult.SUCCESS
        } else {
            this.push(state, level, pos, null as Player?)
            return InteractionResult.CONSUME
        }
    }

    fun push(state: BlockState, level: Level, pos: BlockPos?, player: Player?) {
        var state = state
        state = state.cycle(ACTIVE)
        level.setBlock(pos!!, state, 3)
        level.gameEvent(player, if (state.getValue(ACTIVE)) GameEvent.BLOCK_ACTIVATE else GameEvent.BLOCK_DEACTIVATE, pos)
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean {
        return true
    }
        //TODO: Replace this forced 4 with a value that can be set by the block, only relevant when more blocks of this class are added.
    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        return if (level.getBlockState(pos).getValue(ACTIVE) as Boolean) 4 else 0
    }
}