/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.net.messages.client.stadium.OpenStadiumConfigPacket
import com.cobblemon.mod.common.util.lang
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.level.BlockGetter

@Suppress("OVERRIDE_DEPRECATION")
class StadiumBlock(properties: Properties) : BaseEntityBlock(properties) {

    companion object {
        val CODEC: MapCodec<StadiumBlock> = simpleCodec(::StadiumBlock)

        private val SHAPE = Shapes.or(
            // Main base pedestal
            Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.625, 0.9375),
            // North wall
            Shapes.box(0.0, 0.5625, 0.0, 1.0, 0.8125, 0.1875),
            // South wall
            Shapes.box(0.0, 0.5625, 0.8125, 1.0, 0.8125, 1.0),
            // West wall
            Shapes.box(0.0, 0.5625, 0.1875, 0.125, 0.8125, 0.8125),
            // East wall
            Shapes.box(0.875, 0.5625, 0.1875, 1.0, 0.8125, 0.8125)
        )
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH))
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): BlockEntity {
        return StadiumBlockEntity(blockPos, blockState)
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, blockPlaceContext.horizontalDirection)
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    override fun getShape(blockState: BlockState, blockGetter: BlockGetter, blockPos: BlockPos, collisionContext: CollisionContext): VoxelShape = SHAPE

    override fun isPathfindable(blockState: BlockState, pathComputationType: PathComputationType): Boolean = false

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HorizontalDirectionalBlock.FACING)
    }

    override fun rotate(blockState: BlockState, rotation: Rotation): BlockState {
        return blockState.setValue(
            HorizontalDirectionalBlock.FACING,
            rotation.rotate(blockState.getValue(HorizontalDirectionalBlock.FACING))
        )
    }

    override fun mirror(blockState: BlockState, mirror: Mirror): BlockState {
        return blockState.rotate(mirror.getRotation(blockState.getValue(HorizontalDirectionalBlock.FACING)))
    }

    override fun setPlacedBy(world: Level, blockPos: BlockPos, blockState: BlockState, livingEntity: LivingEntity?, itemStack: ItemStack) {
        super.setPlacedBy(world, blockPos, blockState, livingEntity, itemStack)
        if (!world.isClientSide && livingEntity is ServerPlayer) {
            val blockEntity = world.getBlockEntity(blockPos)
            if (blockEntity is StadiumBlockEntity) {
                blockEntity.data.ownerUUID = livingEntity.uuid
                blockEntity.markUpdated()
            }
        }
    }

    override fun useWithoutItem(blockState: BlockState, world: Level, blockPos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val blockEntity = world.getBlockEntity(blockPos)
        if (blockEntity !is StadiumBlockEntity) {
            return InteractionResult.SUCCESS
        }

        if (player is ServerPlayer) {
            val isOwner = blockEntity.data.ownerUUID == player.uuid
            val isOp = player.hasPermissions(2)
            if (!isOwner && !isOp) {
                player.sendSystemMessage(lang("stadium_config.no_permission"))
                return InteractionResult.CONSUME
            }

            player.sendPacket(OpenStadiumConfigPacket(blockPos, blockEntity.data.toNbt()))
        }
        return InteractionResult.CONSUME
    }

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) {
            val blockEntity = world.getBlockEntity(pos)
            if (blockEntity is StadiumBlockEntity) {
                blockEntity.unregisterDynamic()
            }
            super.onRemove(state, world, pos, newState, moved)
        }
    }

    override fun getRenderShape(blockState: BlockState): RenderShape = RenderShape.MODEL
}
