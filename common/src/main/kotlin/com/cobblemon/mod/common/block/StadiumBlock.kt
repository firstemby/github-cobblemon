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
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class StadiumBlock(properties: Settings): BlockWithEntity(properties) {
    override fun createBlockEntity(blockPos: BlockPos?, blockState: BlockState?): BlockEntity = StadiumBlockEntity(blockPos, blockState)


    @Deprecated("Deprecated in Java")
    override fun onUse(
        blockState: BlockState,
        world: World,
        blockPos: BlockPos,
        player: PlayerEntity,
        interactionHand: Hand,
        blockHitResult: BlockHitResult
    ): ActionResult {
        if (player !is ServerPlayerEntity) return ActionResult.SUCCESS

        val blockEntity = world.getBlockEntity(blockPos)
        if (blockEntity !is StadiumBlockEntity) return ActionResult.SUCCESS

        val message = "Stadium Pos: " + blockEntity.pos +
                "\nPlayer 1 Pos: " + blockEntity.getPlayer1Pos() +
                "\nPoke 1 Pos: " + blockEntity.getPoke1Pos() +
                "\nPlayer 2 Pos: " + blockEntity.getPlayer2Pos() +
                "\nPoke 2 Pos: " + blockEntity.getPoke2Pos()
        player.sendMessage(text(message).red())
        return ActionResult.SUCCESS
    }
}