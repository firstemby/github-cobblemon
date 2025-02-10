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
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class StadiumBlock(properties: Properties): BaseEntityBlock(properties) {
    companion object {
        val CODEC = simpleCodec(::StadiumBlock)
    }

    override fun codec() = CODEC

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return StadiumBlockEntity(pos, state)
    }

    override fun getRenderShape(state: BlockState) = RenderShape.MODEL

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

        val message = "Stadium Pos: " + blockEntity.blockPos +
                "\nPlayer 1 Pos: " + blockEntity.getPlayer1Pos() +
                "\nPoke 1 Pos: " + blockEntity.getPoke1Pos() +
                "\nPlayer 2 Pos: " + blockEntity.getPlayer2Pos() +
                "\nPoke 2 Pos: " + blockEntity.getPoke2Pos()
        player.sendSystemMessage(text(message).red())
        return InteractionResult.SUCCESS
    }
}