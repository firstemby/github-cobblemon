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
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("DEPRECATED", "OVERRIDE_DEPRECATION")
class EjectButtonBlock(properties : Properties) : FaceAttachedHorizontalDirectionalBlock(properties), SimpleWaterloggedBlock {
    private val ticksToStayPressed: Int

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.FLOOR)
            .setValue(POWERED, false)
            .setValue(WATERLOGGED, false))
        this.ticksToStayPressed = 20
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
        builder.add(FACE)
        builder.add(POWERED)
        builder.add(WATERLOGGED)
    }

    override fun codec(): MapCodec<EjectButtonBlock> {
        return CODEC
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState)
    }

    //May need some clean up, ended up just copying this straight from the original class so I could inject the waterlogging stuff to make it properly work, but it does look a bit ugly
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val var2 = context.nearestLookingDirections
        val var3 = var2.size
        val fluidState = context.level.getFluidState(context.clickedPos)

        for (var4 in 0 until var3) {
            val direction = var2[var4]
            var blockState = if (direction.axis === Direction.Axis.Y) {
                defaultBlockState().setValue(
                    FACE,
                    if (direction == Direction.UP) AttachFace.CEILING else AttachFace.FLOOR
                )
                    .setValue(FACING, context.horizontalDirection.opposite).setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
            } else {
                (defaultBlockState().setValue(FACE, AttachFace.WALL) as BlockState).setValue(
                    FACING,
                    direction.opposite
                ).setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
            }
            if (blockState.canSurvive(context.level, context.clickedPos)) {
                return blockState
            }
        }
        return null
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        val direction = state.getValue(FACING)
        return when (state.getValue(FACE)) {
            AttachFace.FLOOR -> return if (state.getValue(POWERED)) {FLOOR_POWERED} else {FLOOR_UNPOWERED}

            AttachFace.WALL -> {
                when (direction) {
                    Direction.EAST  -> return if (state.getValue(POWERED)) {EAST_WALL_POWERED} else {EAST_WALL_UNPOWERED}
                    Direction.WEST -> return if (state.getValue(POWERED)) {WEST_WALL_POWERED} else {WEST_WALL_UNPOWERED}
                    Direction.SOUTH -> return if (state.getValue(POWERED)) {SOUTH_WALL_POWERED} else {SOUTH_WALL_UNPOWERED}
                    Direction.NORTH -> return if (state.getValue(POWERED)) {NORTH_WALL_POWERED} else {NORTH_WALL_UNPOWERED}
                    else -> {FLOOR_UNPOWERED}
                }
            }

            AttachFace.CEILING ->  return if (state.getValue(POWERED)) {CEILING_POWERED} else {CEILING_UNPOWERED}
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
        return if (getConnectedDirection(state).opposite == direction && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    //It does in fact override, and crashes if it isn't overriding. Don't trust this.
    @Deprecated("Deprecated in Java")
    override fun canSurvive(state: BlockState?, level: LevelReader?, pos: BlockPos): Boolean {
        return when (state?.getValue(FACE)) {
            AttachFace.CEILING -> {
                level!!.getBlockState(pos.above()).isSolid
            }
            AttachFace.WALL -> {
                level!!.getBlockState(pos.relative(state.getValue(FACING).opposite)).isSolid
            }
            AttachFace.FLOOR -> {
                level!!.getBlockState(pos.below()).isSolid
            }
            else -> true
        }
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (state.getValue(POWERED) as Boolean) {
            return InteractionResult.CONSUME
        } else {
            this.press(state, level, pos, player)
            return InteractionResult.sidedSuccess(level.isClientSide)
        }
    }

    fun press(state: BlockState, level: Level, pos: BlockPos, player: Player?) {
        level.setBlock(pos, state.setValue(POWERED, true) as BlockState, 3)
        this.updateNeighbours(state, level, pos)
        level.scheduleTick(pos, this, this.ticksToStayPressed)
        //this.playSound(player, level, pos, true)
        level.gameEvent(player, GameEvent.BLOCK_ACTIVATE, pos)
    }

    private fun updateNeighbours(state: BlockState, level: Level, pos: BlockPos) {
        level.updateNeighborsAt(pos, this)
        level.updateNeighborsAt(pos.relative(getConnectedDirection(state).getOpposite()), this)
    }

    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean
    ) {
        if (!movedByPiston && !state.`is`(newState.getBlock())) {
            if (state.getValue(POWERED) as Boolean) {
                this.updateNeighbours(state, level, pos)
            }

            super.onRemove(state, level, pos, newState, movedByPiston)
        }
    }

    override fun getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
        return if (state.getValue(POWERED) as Boolean) 15 else 0
    }

    override fun getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int {
        return if (state.getValue(POWERED) as Boolean && getConnectedDirection(state) == direction) 15 else 0
    }

    override fun isSignalSource(state: BlockState): Boolean {
        return true
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (state.getValue(POWERED)) {
            this.checkPressed(state, level, pos)
        }
    }

    protected fun checkPressed(state: BlockState, level: Level, pos: BlockPos) {
        val abstractArrow = null
        val bl = false
        val bl2 = state.getValue(POWERED) as Boolean
        if (bl != bl2) {
            level.setBlock(pos, state.setValue(POWERED, bl) as BlockState, 3)
            this.updateNeighbours(state, level, pos)
            //this.playSound(null as Player?, level, pos, bl)
            level.gameEvent(abstractArrow, if (bl) GameEvent.BLOCK_ACTIVATE else GameEvent.BLOCK_DEACTIVATE, pos)
        }

        if (bl) {
            level.scheduleTick(BlockPos(pos), this, this.ticksToStayPressed)
        }
    }

    companion object {
        val CODEC: MapCodec<EjectButtonBlock> = RecordCodecBuilder.mapCodec {
            it.group(
                propertiesCodec()
            ).apply(it, ::EjectButtonBlock) }
        val POWERED = BooleanProperty.create("powered")

        val FLOOR_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.0, 0.125, 0.875, 0.1875, 0.875),
            Shapes.box(0.375, 0.1875, 0.375, 0.625, 0.3125, 0.625) )
        val FLOOR_POWERED = Shapes.or(
            Shapes.box(0.125, 0.0, 0.125, 0.875, 0.1875, 0.875),
            Shapes.box(0.375, 0.1875, 0.375, 0.625, 0.25, 0.625) )
        val CEILING_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.8125, 0.125, 0.875, 1.0, 0.875),
            Shapes.box(0.375, 0.6875, 0.375, 0.625, 0.8125, 0.625) )
        val CEILING_POWERED = Shapes.or(
            Shapes.box(0.125, 0.8125, 0.125, 0.875, 1.0, 0.875),
            Shapes.box(0.375, 0.75, 0.375, 0.625, 0.8125, 0.625) )

        val EAST_WALL_UNPOWERED = Shapes.or(
            Shapes.box(0.0, 0.125, 0.125, 0.1875, 0.875, 0.875),
            Shapes.box(0.1875, 0.375, 0.375, 0.3125, 0.625, 0.625) )
        val EAST_WALL_POWERED = Shapes.or(
            Shapes.box(0.0, 0.125, 0.125, 0.1875, 0.875, 0.875),
            Shapes.box(0.1875, 0.375, 0.375, 0.25, 0.625, 0.625) )
        val WEST_WALL_UNPOWERED = Shapes.or(
            Shapes.box(0.8125, 0.125, 0.125, 1.0, 0.875, 0.875),
            Shapes.box(0.6875, 0.375, 0.375, 0.8125, 0.625, 0.625) )
        val WEST_WALL_POWERED = Shapes.or(
            Shapes.box(0.8125, 0.125, 0.125, 1.0, 0.875, 0.875),
            Shapes.box(0.75, 0.375, 0.375, 0.8125, 0.625, 0.625) )
        val NORTH_WALL_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.125, 0.8125, 0.875, 0.875, 1.0),
            Shapes.box(0.375, 0.375, 0.6875, 0.625, 0.625, 0.8125) )
        val NORTH_WALL_POWERED = Shapes.or(
            Shapes.box(0.125, 0.125, 0.8125, 0.875, 0.875, 1.0),
            Shapes.box(0.375, 0.375, 0.75, 0.625, 0.625, 0.8125) )
        val SOUTH_WALL_UNPOWERED = Shapes.or(
            Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 0.1875),
            Shapes.box(0.375, 0.375, 0.1875, 0.625, 0.625, 0.3125) )
        val SOUTH_WALL_POWERED = Shapes.or(
            Shapes.box(0.125, 0.125, 0.0, 0.875, 0.875, 0.1875),
            Shapes.box(0.375, 0.375, 0.1875, 0.625, 0.625, 0.25) )
    }
}