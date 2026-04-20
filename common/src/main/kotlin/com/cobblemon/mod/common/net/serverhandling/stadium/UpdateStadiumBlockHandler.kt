/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.stadium

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.stadium.StadiumBlockData
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.net.messages.server.stadium.UpdateStadiumBlockPacket
import com.cobblemon.mod.common.util.lang
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UpdateStadiumBlockHandler : ServerNetworkPacketHandler<UpdateStadiumBlockPacket> {
    override fun handle(packet: UpdateStadiumBlockPacket, server: MinecraftServer, player: ServerPlayer) {
        val blockEntity = player.level().getBlockEntity(packet.blockPos) as? StadiumBlockEntity ?: return

        // Validate distance
        if (player.blockPosition().distSqr(packet.blockPos) > 100) return

        // Validate permission: must be owner or OP
        val isOwner = blockEntity.data.ownerUUID == player.uuid
        val isOp = player.hasPermissions(2)
        if (!isOwner && !isOp) {
            player.sendSystemMessage(lang("stadium_config.no_permission"))
            return
        }

        val newData = StadiumBlockData.fromNbt(packet.data)
        // Preserve the original stadiumId and ownerUUID
        newData.copy(
            stadiumId = blockEntity.data.stadiumId,
            ownerUUID = blockEntity.data.ownerUUID
        ).let { blockEntity.data = it }

        blockEntity.markUpdated()

        if (blockEntity.data.isConfigured) {
            blockEntity.registerDynamic()
        } else {
            blockEntity.unregisterDynamic()
        }
    }
}
