/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.entity.PrintedPhotoDisplayBlockEntity
import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.Containers
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class PrintedPhotoDisplayBlock(properties: Properties) :
    FaceAttachedHorizontalDirectionalBlock(properties),
    EntityBlock,
    SimpleWaterloggedBlock {

    data class SurfaceAxes(val normal: Direction, val right: Direction, val up: Direction)

    companion object {
        val CODEC: MapCodec<PrintedPhotoDisplayBlock> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(propertiesCodec()).apply(instance, ::PrintedPhotoDisplayBlock)
        }

        private val WALL_NORTH = Shapes.box(0.0625, 0.0625, 0.9375, 0.9375, 0.9375, 1.0)
        private val FLOOR_NORTH = Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.0625, 0.9375)
        private val CEILING_NORTH = Shapes.box(0.0625, 0.9375, 0.0625, 0.9375, 1.0, 0.9375)
        private val GROUP_REMOVE_GUARD = ThreadLocal.withInitial { false }

        fun resolveAxes(state: BlockState): SurfaceAxes {
            val face = state.getValue(FACE)
            val facing = state.getValue(FACING)
            val normal = when (face) {
                AttachFace.WALL -> facing
                AttachFace.FLOOR -> Direction.UP
                AttachFace.CEILING -> Direction.DOWN
            }
            val up = when (face) {
                AttachFace.WALL -> Direction.UP
                AttachFace.FLOOR -> facing.opposite
                AttachFace.CEILING -> facing
            }
            val right = cross(up, normal) ?: Direction.EAST
            return SurfaceAxes(normal = normal, right = right, up = up)
        }

        private fun cross(a: Direction, b: Direction): Direction? {
            val ax = a.stepX
            val ay = a.stepY
            val az = a.stepZ
            val bx = b.stepX
            val by = b.stepY
            val bz = b.stepZ
            val x = ay * bz - az * by
            val y = az * bx - ax * bz
            val z = ax * by - ay * bx
            return Direction.fromDelta(x, y, z)
        }
    }

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.WALL)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun codec(): MapCodec<PrintedPhotoDisplayBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, FACE, WATERLOGGED)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        PrintedPhotoDisplayBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        for (direction in context.nearestLookingDirections) {
            val candidate = if (direction.axis == Direction.Axis.Y) {
                defaultBlockState()
                    .setValue(FACE, if (direction == Direction.UP) AttachFace.CEILING else AttachFace.FLOOR)
                    .setValue(FACING, context.horizontalDirection.opposite)
            } else {
                defaultBlockState()
                    .setValue(FACE, AttachFace.WALL)
                    .setValue(FACING, direction.opposite)
            }.setValue(WATERLOGGED, fluidState.type == Fluids.WATER)

            if (candidate.canSurvive(context.level, context.clickedPos)) {
                return candidate
            }
        }
        return null
    }

    override fun getFluidState(state: BlockState): FluidState? {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        val direction = state.getValue(FACING)
        return when (state.getValue(FACE)) {
            AttachFace.WALL -> when (direction) {
                Direction.NORTH, Direction.UP, Direction.DOWN -> WALL_NORTH
                else -> rotateShape(Direction.NORTH, direction, WALL_NORTH)
            }
            AttachFace.FLOOR -> {
                if (direction.axis == Direction.Axis.X) {
                    rotateShape(Direction.NORTH, Direction.WEST, FLOOR_NORTH)
                } else {
                    FLOOR_NORTH
                }
            }
            AttachFace.CEILING -> {
                if (direction.axis == Direction.Axis.X) {
                    rotateShape(Direction.NORTH, Direction.WEST, CEILING_NORTH)
                } else {
                    CEILING_NORTH
                }
            }
        }
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return if (state.getValue(FACE) == AttachFace.CEILING) {
            val direction = getConnectedDirection(state)
            canSupportCenter(level, pos.relative(direction.opposite), direction)
        } else {
            super.canSurvive(state, level, pos)
        }
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighbourState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighbourPos: BlockPos
    ): BlockState {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }
        return if (getConnectedDirection(state).opposite == direction && !state.canSurvive(level, pos)) {
            Blocks.AIR.defaultBlockState()
        } else {
            super.updateShape(state, direction, neighbourState, level, pos, neighbourPos)
        }
    }

    override fun onRemove(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        newState: BlockState,
        movedByPiston: Boolean
    ) {
        if (state.block == newState.block) {
            super.onRemove(state, level, pos, newState, movedByPiston)
            return
        }

        if (!GROUP_REMOVE_GUARD.get()) {
            val blockEntity = level.getBlockEntity(pos) as? PrintedPhotoDisplayBlockEntity
            if (blockEntity != null) {
                if (!level.isClientSide) {
                    val axes = resolveAxes(state)
                    GROUP_REMOVE_GUARD.set(true)
                    try {
                        val root = blockEntity.rootPos
                        val size = blockEntity.gridSize.coerceIn(1, 3)
                        for (x in 0 until size) {
                            for (y in 0 until size) {
                                val otherPos = root
                                    .relative(axes.right, x)
                                    .relative(axes.up, y)
                                if (otherPos == pos) continue
                                if (level.getBlockState(otherPos).block != this) continue
                                val otherBe = level.getBlockEntity(otherPos) as? PrintedPhotoDisplayBlockEntity ?: continue
                                if (otherBe.rootPos != root) continue
                                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE)
                            }
                        }
                    } finally {
                        GROUP_REMOVE_GUARD.set(false)
                    }
                }

                if (
                    !level.isClientSide &&
                    !movedByPiston &&
                    level.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS)
                ) {
                    blockEntity.createDroppedStack().takeIf { !it.isEmpty }?.let { stack ->
                        Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack)
                    }
                }
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston)
    }
}
