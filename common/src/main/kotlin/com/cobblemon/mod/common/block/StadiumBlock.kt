/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
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
import net.minecraft.world.phys.BlockHitResult

class StadiumBlock(properties: Properties): BaseEntityBlock(properties) {
    companion object {
        val CODEC = simpleCodec(::StadiumBlock)
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH))
    }

    override fun codec() = CODEC

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return StadiumBlockEntity(pos, state)
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState {
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, blockPlaceContext.horizontalDirection)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HorizontalDirectionalBlock.FACING)
    }

    override fun rotate(blockState: BlockState, rotation: Rotation): BlockState {
        return blockState.setValue(
            HorizontalDirectionalBlock.FACING, rotation.rotate(blockState.getValue(
                HorizontalDirectionalBlock.FACING)))
    }

    override fun mirror(blockState: BlockState, mirror: Mirror): BlockState {
        return blockState.rotate(mirror.getRotation(blockState.getValue(HorizontalDirectionalBlock.FACING)))
    }

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) super.onRemove(state, world, pos, newState, moved)
    }

    //TODO: override getTicker

    override fun getRenderShape(state: BlockState) = RenderShape.MODEL

    override fun useWithoutItem(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (player !is ServerPlayer) return InteractionResult.SUCCESS

        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity !is StadiumBlockEntity) return InteractionResult.SUCCESS

        player.sendSystemMessage(text("Adding you to Battle").red())
        blockEntity.addPlayer(player)
        return InteractionResult.SUCCESS
    }

    /*
    override fun useWithoutItem(
        blockState: BlockState,
        world: Level,
        blockPos: BlockPos,
        player: Player,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        if (player !is ServerPlayer) return InteractionResult.SUCCESS

        val blockEntity = world.getBlockEntity(blockPos)
        if (blockEntity !is StadiumBlockEntity) return InteractionResult.SUCCESS

        // Toggle isInUse state
        blockEntity.isInUse = !blockEntity.isInUse

        // debug
        val activateMessage = if (blockEntity.isInUse) "Stadium Activated!" else "Stadium Deactivated!"
        player.sendSystemMessage(text(activateMessage).red())

        if (blockEntity.isInUse) {
            // Generate flight path when activated
            blockEntity.generateFlightPath(world, radiusX = 5.0, radiusZ = 3.0, height = 10.0, steps = 20)

        } else {
            // Clear path when deactivated
            blockEntity.pathPoints.clear()
        }

        val message = "Stadium Pos: " + blockEntity.blockPos +
                "\nPlayer 1 Pos: " + blockEntity.getPlayer1Pos() +
                "\nPoke 1 Pos: " + blockEntity.getPoke1Pos() +
                "\nPlayer 2 Pos: " + blockEntity.getPlayer2Pos() +
                "\nPoke 2 Pos: " + blockEntity.getPoke2Pos()
        player.sendSystemMessage(text(message).red())

        blockEntity.setChanged()
        world.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)

        return InteractionResult.SUCCESS
    }
    */
}