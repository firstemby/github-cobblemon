/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.modificationstation.ModificationStationMenu
import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class ModificationStationBlock(properties: Properties) : Block(properties) {
    override fun codec(): MapCodec<ModificationStationBlock> = CODEC

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        player.openMenu(state.getMenuProvider(level, pos))
        player.awardStat(Stats.INTERACT_WITH_STONECUTTER)
        return InteractionResult.CONSUME
    }

    override fun getMenuProvider(state: BlockState, level: Level, pos: BlockPos): MenuProvider {
        return SimpleMenuProvider(
            { syncId, inventory, _ -> ModificationStationMenu(syncId, inventory, ContainerLevelAccess.create(level, pos)) },
            CONTAINER_TITLE
        )
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return SHAPES_BY_FACING[state.getValue(FACING)] ?: SHAPE
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getShape(state, level, pos, context)
    }

    override fun useShapeForLightOcclusion(state: BlockState): Boolean = true
    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        return state.rotate(mirror.getRotation(state.getValue(FACING)))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean = false

    companion object {
        val CODEC: MapCodec<ModificationStationBlock> = simpleCodec(::ModificationStationBlock)
        val CONTAINER_TITLE: Component = Component.translatable("container.cobblemon.modification_station")
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
        val SHAPE: VoxelShape = Shapes.or(
            box(3.0, 0.0, 4.0, 13.0, 17.0, 13.0),
            box(0.0, 8.0, 4.0, 3.0, 13.0, 13.0),
            box(2.0, 10.5, 0.5, 16.0, 14.5, 13.5),
            box(3.0, 3.0, 4.0, 4.0, 8.0, 6.0),
            box(12.0, 3.0, 4.0, 13.0, 8.0, 6.0),
            box(11.5, 13.5, 0.5, 15.5, 14.5, 4.5),
            box(1.0, 13.0, 9.5, 2.0, 17.0, 12.5)
        )
        val SHAPES_BY_FACING: Map<Direction, VoxelShape> = mapOf(
            Direction.NORTH to SHAPE,
            Direction.EAST to rotateShape(Direction.NORTH, Direction.EAST, SHAPE),
            Direction.SOUTH to rotateShape(Direction.NORTH, Direction.SOUTH, SHAPE),
            Direction.WEST to rotateShape(Direction.NORTH, Direction.WEST, SHAPE)
        )
    }
}
