/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.sound.BlockEntitySoundTracker
import com.cobblemon.mod.common.client.sound.instances.CancellableSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
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

class MechanicalFanBlock(settings: Properties) : Block(settings), SimpleWaterloggedBlock {
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
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return when (state.getValue(HorizontalDirectionalBlock.FACING)) {
            Direction.SOUTH -> NS_AABB
            Direction.WEST -> EW_AABB
            Direction.EAST -> EW_AABB
            else -> NS_AABB
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
        playSound(level, pos, state, CobblemonSounds.DISPLAY_CASE_PLACE)
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
        val CODEC = simpleCodec(::MechanicalFanBlock)
        val ACTIVE = BooleanProperty.create("active")

        private val NS_AABB = Shapes.or(
            Shapes.box(0.125, 0.25, 0.4375, 0.875, 1.0, 0.5625),
            Shapes.box(0.4375, 0.125, 0.4375, 0.5625, 0.25, 0.5625),
            Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.125, 0.6875)
        )
        private val EW_AABB = Shapes.or(
            Shapes.box(0.4375, 0.25, 0.125, 0.5625, 1.0, 0.875),
            Shapes.box(0.4375, 0.125, 0.4375, 0.5625, 0.25, 0.5625),
            Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.125, 0.6875)
        )

        fun playSound(world: Level, pos: BlockPos, state: BlockState, sound: SoundEvent) {
            var d = pos.x.toDouble() + 0.5
            val e = pos.y.toDouble() + 0.5
            var f = pos.z.toDouble() + 0.5
            val direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
            d += direction.stepX.toDouble() * 0.5
            f += direction.stepZ.toDouble() * 0.5
            world.playSound(
                null,
                d,
                e,
                f,
                sound,
                SoundSource.BLOCKS,
                0.5f,
                world.random.nextFloat() * 0.1f + 0.9f
            )
        }
    }

    //TODO Get it to be a cleanly looping sound, and get Sterr to make a proper sound for it
/*    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if ((state.getValue(ACTIVE))) {
            if (random.nextInt(6) == 0) {
                level.playLocalSound(
                    pos.x.toDouble() + 0.5,
                    pos.y.toDouble() + 0.5,
                    pos.z.toDouble() + 0.5,
                    CobblemonSounds.EVOLUTION_NOTIFICATION,
                    SoundSource.BLOCKS,
                    1f,
                    1f,
                    false
                )
            }
        }
    }*/
}