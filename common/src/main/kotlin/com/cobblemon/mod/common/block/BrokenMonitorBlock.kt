/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.api.multiblock.MultiblockBlock
import com.cobblemon.mod.common.api.multiblock.MultiblockEntity
import com.cobblemon.mod.common.block.entity.FossilMultiblockEntity
import com.cobblemon.mod.common.block.multiblock.FossilMultiblockBuilder
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class BrokenMonitorBlock(settings: Properties) : Block(settings) {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(SCREEN, BrokenMonitorScreen.OFF))
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(HORIZONTAL_FACING, blockPlaceContext.horizontalDirection.opposite)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING)
        builder.add(SCREEN)
    }

    override fun getShape(
        state: BlockState,
        blockGetter: BlockGetter,
        pos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return when (state.getValue(HorizontalDirectionalBlock.FACING)) {
            Direction.WEST -> HITBOX_WEST
            Direction.EAST -> HITBOX_EAST
            Direction.SOUTH -> HITBOX_SOUTH
            else -> HITBOX_NORTH
        }
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
        //0 is off
        val SCREEN = EnumProperty.create("screen", BrokenMonitorScreen::class.java)
        val HITBOX_SOUTH = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.375, 0.9375),
            Shapes.box(0.0625, 0.875, 0.0625, 0.9375, 1.0, 0.9375),
            Shapes.box(0.8125, 0.375, 0.0625, 0.9375, 0.875, 0.9375),
            Shapes.box(0.1875, 0.375, 0.125, 0.8125, 0.875, 0.9375),
            Shapes.box(0.0625, 0.375, 0.0625, 0.1875, 0.875, 0.9375)
        )
        val HITBOX_NORTH = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.375, 0.9375),
            Shapes.box(0.0625, 0.875, 0.0625, 0.9375, 1.0, 0.9375),
            Shapes.box(0.0625, 0.375, 0.0625, 0.1875, 0.875, 0.9375),
            Shapes.box(0.1875, 0.375, 0.0625, 0.8125, 0.875, 0.875),
            Shapes.box(0.8125, 0.375, 0.0625, 0.9375, 0.875, 0.9375)
        )
        val HITBOX_EAST = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.375, 0.9375),
            Shapes.box(0.0625, 0.875, 0.0625, 0.9375, 1.0, 0.9375),
            Shapes.box(0.0625, 0.375, 0.0625, 0.9375, 0.875, 0.1875),
            Shapes.box(0.125, 0.375, 0.1875, 0.9375, 0.875, 0.8125),
            Shapes.box(0.0625, 0.375, 0.8125, 0.9375, 0.875, 0.9375)
        )
        val HITBOX_WEST = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.375, 0.9375),
            Shapes.box(0.0625, 0.875, 0.0625, 0.9375, 1.0, 0.9375),
            Shapes.box(0.0625, 0.375, 0.8125, 0.9375, 0.875, 0.9375),
            Shapes.box(0.0625, 0.375, 0.1875, 0.875, 0.875, 0.8125),
            Shapes.box(0.0625, 0.375, 0.0625, 0.9375, 0.875, 0.1875)
        )
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        fromPos: BlockPos,
        isMoving: Boolean
    ){
        val powered = level.hasNeighborSignal(pos)

        val newState = if (powered) {
            BrokenMonitorScreen.GLITCHING
        } else {
            BrokenMonitorScreen.OFF
        }

        if (state.getValue(SCREEN) != newState) {
            level.setBlock(pos, state.setValue(SCREEN, newState), 3)
        }
    }

    enum class BrokenMonitorScreen : StringRepresentable {
        OFF,
        GLITCHING;

        override fun getSerializedName(): String = this.name.lowercase()
    }
}
