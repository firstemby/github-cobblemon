/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class MetronomeBlock(settings: Properties) : Block(settings), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(ACTIVE, false)
            .setValue(WATERLOGGED, false)
        )
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        var blockState = defaultBlockState().setValue(ACTIVE, false)
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

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING)
        builder.add(ACTIVE)
        builder.add(WATERLOGGED)
    }

    override fun getShape(
            state: BlockState,
            world: BlockGetter,
            pos: BlockPos,
            context: CollisionContext
        ): VoxelShape {
            return when (state.getValue(HorizontalDirectionalBlock.FACING)) {
                Direction.NORTH -> NORTH_OUTLINE
                Direction.EAST -> EAST_OUTLINE
                Direction.WEST -> WEST_OUTLINE
                else -> SOUTH_OUTLINE
            }
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

        override fun getFluidState(blockState: BlockState): FluidState? {
            return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
        }

        @Deprecated("Deprecated in Java")
        override fun rotate(state: BlockState, rotation: Rotation): BlockState {
            return state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)))
        }

        @Deprecated("Deprecated in Java")
        override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean {
            return false
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

        companion object {
            val CODEC = simpleCodec(::MetronomeBlock)
            val ACTIVE = BooleanProperty.create("active")

            val NORTH_OUTLINE = Shapes.or(
                Shapes.box(0.25, 0.0, 0.375, 0.75, 0.25, 0.5),
                Shapes.box(0.25, 0.0, 0.5, 0.75, 0.8125, 0.75)
            )
            val SOUTH_OUTLINE = Shapes.or(
                Shapes.box(0.25, 0.0, 0.5, 0.75, 0.25, 0.625),
                Shapes.box(0.25, 0.0, 0.25, 0.75, 0.8125, 0.5)
            )
            val EAST_OUTLINE = Shapes.or(
                Shapes.box(0.5, 0.0, 0.25, 0.625, 0.25, 0.75),
                Shapes.box(0.25, 0.0, 0.25, 0.5, 0.8125, 0.75)
            )
            val WEST_OUTLINE = Shapes.or(
                Shapes.box(0.375, 0.0, 0.25, 0.5, 0.25, 0.75),
                Shapes.box(0.5, 0.0, 0.25, 0.75, 0.8125, 0.75)
            )
        }
    }