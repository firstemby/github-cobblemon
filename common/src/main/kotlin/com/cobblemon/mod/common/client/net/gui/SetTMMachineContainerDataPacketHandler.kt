/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.entity.TMMachineBlockEntity
import com.cobblemon.mod.common.block.tmmachine.TMMachineMenu
import com.cobblemon.mod.common.net.messages.client.ui.SetTMMachineContainerDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SetTMMachineContainerDataPacketHandler : ServerNetworkPacketHandler<SetTMMachineContainerDataPacket> {

    override fun handle(packet: SetTMMachineContainerDataPacket, server: MinecraftServer, player: ServerPlayer) {
        val menu = player.containerMenu as? TMMachineMenu ?: return
        val slotIndex = packet.dataSlotIndex
        val slotValue = packet.value

        menu.containerData?.set(slotIndex, slotValue)

        // If setting to not active, also reset progress
        if (slotIndex == TMMachineBlockEntity.BURN_ACTIVE_INDEX && slotValue == 0) {
            menu.containerData?.set(TMMachineBlockEntity.BURN_PROGRESS_INDEX, 0)
        }

        menu.broadcastChanges()
    }
}
