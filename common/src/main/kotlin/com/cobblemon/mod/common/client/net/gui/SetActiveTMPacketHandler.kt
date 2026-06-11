/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.net.messages.client.ui.SetActiveTMPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block

object SetActiveTMPacketHandler : ServerNetworkPacketHandler<SetActiveTMPacket> {
    override fun handle(packet: SetActiveTMPacket, server: MinecraftServer, player: ServerPlayer) {
        val menu = player.containerMenu as? TMMachineMenu ?: return
        val inventory = menu.inventory ?: return
        val tmData = Cobblemon.playerDataManager.getTMData(player)

        if (packet.tm != null && (!packet.tm.isPassivelyObtained() && packet.tm.id !in tmData.learnedTMs)) {
            return // Hacker smh
        }

        menu.tmMachineEntity?.let { blockEntity ->
            blockEntity.activeMove = packet.tm?.moveName?.name ?: ""
            blockEntity.setChanged()
            player.level().sendBlockUpdated(blockEntity.blockPos, blockEntity.blockState, blockEntity.blockState, Block.UPDATE_CLIENTS)
        }

        inventory.setChanged()
        menu.broadcastChanges()
    }
}
